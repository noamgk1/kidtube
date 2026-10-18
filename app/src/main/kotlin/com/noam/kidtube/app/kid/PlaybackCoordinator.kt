package com.noam.kidtube.app.kid

import com.noam.kidtube.core.BudgetAccountant
import com.noam.kidtube.core.Clock
import com.noam.kidtube.core.ConfirmResult
import com.noam.kidtube.core.ConfirmedTicket
import com.noam.kidtube.core.DenyReason
import com.noam.kidtube.core.IssueResult
import com.noam.kidtube.core.Policy
import com.noam.kidtube.core.VideoDetails
import com.noam.kidtube.core.VideoId
import com.noam.kidtube.core.VideoRef
import com.noam.kidtube.core.WatchEntry
import com.noam.kidtube.core.WatchSession
import com.noam.kidtube.core.store.BudgetStore
import com.noam.kidtube.core.store.SettingsStore
import com.noam.kidtube.core.store.WatchLedger
import com.noam.kidtube.core.store.WhitelistStore
import com.noam.kidtube.core.today
import com.noam.kidtube.engine.PlaybackPhase
import com.noam.kidtube.engine.PlaybackResolver
import com.noam.kidtube.engine.PlayerState
import com.noam.kidtube.engine.VideoPlayer
import com.noam.kidtube.engine.VideoUnavailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

sealed interface PlaybackOutcome {
    data class Started(val details: VideoDetails) : PlaybackOutcome
    data class Refused(val reason: DenyReason) : PlaybackOutcome

    /** The video is gone for good: deleted, private, region-blocked. Never retried. */
    data class Unavailable(val videoId: VideoId) : PlaybackOutcome
    data class Failed(val message: String) : PlaybackOutcome

    /**
     * A playback is already being set up. Extraction took between 1.8 and 26.6 seconds
     * when measured, which is long enough for an impatient child to tap three more times;
     * without this each tap would open its own session and debit the budget again.
     */
    data object Busy : PlaybackOutcome
}

sealed interface TickResult {
    data class Continue(val remainingSeconds: Int) : TickResult
    data object BudgetExhausted : TickResult
}

/** What the UI should do in response to the player's own state. */
sealed interface PlayerEvent {
    data object Nothing : PlayerEvent
    data object ReturnToGrid : PlayerEvent
    data class ShowError(val message: String) : PlayerEvent
}

/**
 * Runs the four-step dance that keeps the whitelist promise structural:
 * Policy.issue, resolver.resolve, Policy.confirm, player.start. The player only ever
 * receives a [ConfirmedTicket], and only Policy can mint one.
 *
 * It also owns the two things the UI must not be trusted with: what counts as watch time,
 * and what to do when the player reaches a terminal state.
 */
class PlaybackCoordinator(
    private val policy: Policy,
    private val resolver: PlaybackResolver,
    private val player: VideoPlayer,
    private val whitelistStore: WhitelistStore,
    private val budgetStore: BudgetStore,
    private val accountant: BudgetAccountant,
    private val settingsStore: SettingsStore,
    private val ledger: WatchLedger,
    private val clock: Clock,
    /**
     * Where the store reads and writes happen. Every method below touches files, and the
     * UI calls them from composition and from a collector, so none of it may run on the
     * main thread.
     */
    private val io: CoroutineContext = Dispatchers.IO,
) {
    /**
     * One viewing at a time, serialised. The player-state collector and the budget ticker
     * both drive this object concurrently; without the lock they would interleave reads
     * and writes of the same session fields.
     */
    private val mutex = Mutex()

    private var session: WatchSession? = null
    private var current: ConfirmedTicket? = null
    private var currentChannelId: String? = null
    private var retried = false
    private var starting = false

    /** Only time spent actually PLAYING is charged; pauses and buffering are not. */
    private var playingSinceRealtimeMs: Long? = null
    private var accumulatedPlayedMs: Long = 0

    suspend fun play(video: VideoRef): PlaybackOutcome = mutex.withLock {
        if (starting || session != null) return@withLock PlaybackOutcome.Busy
        starting = true
        try {
            withContext(io) { start(video) }
        } finally {
            starting = false
        }
    }

    private suspend fun start(video: VideoRef): PlaybackOutcome {
        val todayIso = clock.today().toString()
        val budget = accountant.forToday(budgetStore.load(todayIso))
        budgetStore.save(budget)

        val limitSeconds = settingsStore.load().dailyLimitMinutes * 60
        val remaining = accountant.remainingSeconds(budget, limitSeconds)

        val ticket = when (
            val issued = policy.issue(video.id, video.channelId, whitelistStore.load(), remaining)
        ) {
            is IssueResult.Denied -> return PlaybackOutcome.Refused(issued.reason)
            is IssueResult.Allowed -> issued.ticket
        }

        val details = resolver.resolve(ticket).getOrElse { cause ->
            return if (cause is VideoUnavailable) {
                PlaybackOutcome.Unavailable(video.id)
            } else {
                PlaybackOutcome.Failed(cause.message ?: "resolution failed")
            }
        }

        val confirmed = when (val checked = policy.confirm(ticket, details)) {
            is ConfirmResult.Denied -> return PlaybackOutcome.Refused(checked.reason)
            is ConfirmResult.Allowed -> checked.confirmed
        }

        val (openedSession, debited) = accountant.openSession(video.id, budget)
        budgetStore.save(debited)

        session = openedSession
        current = confirmed
        currentChannelId = video.channelId.value
        retried = false
        playingSinceRealtimeMs = null
        accumulatedPlayedMs = 0

        player.start(confirmed, positionMs = 0)
        return PlaybackOutcome.Started(details)
    }

    /**
     * Reacts to the player. Without this the terminal phases were never observed: a
     * finished video sat on screen draining the allowance, and the single re-resolve for
     * an expired stream URL was unreachable.
     */
    suspend fun onPlayerState(state: PlayerState): PlayerEvent = mutex.withLock {
        withContext(io) { onPlayerStateLocked(state) }
    }

    private suspend fun onPlayerStateLocked(state: PlayerState): PlayerEvent {
        if (session == null) return PlayerEvent.Nothing

        when (state.phase) {
            PlaybackPhase.PLAYING -> {
                if (playingSinceRealtimeMs == null) {
                    playingSinceRealtimeMs = clock.elapsedRealtimeMillis()
                }
                return PlayerEvent.Nothing
            }

            PlaybackPhase.IDLE, PlaybackPhase.BUFFERING, PlaybackPhase.PAUSED -> {
                stopCountingPlayTime()
                return PlayerEvent.Nothing
            }

            PlaybackPhase.ENDED -> {
                stopCountingPlayTime()
                onEndedLocked(completed = true)
                return PlayerEvent.ReturnToGrid
            }

            PlaybackPhase.FAILED -> {
                stopCountingPlayTime()
                val failure = state.failure ?: "playback failed"
                // The position comes from the state snapshot rather than from the player:
                // a player that has just failed is not a reliable source of its position.
                val outcome = onPlayerFailedLocked(
                    failure,
                    state.failureIsRecoverable,
                    resumeFromMs = state.positionMs,
                )
                return if (outcome is PlaybackOutcome.Started) {
                    PlayerEvent.Nothing
                } else {
                    // Record what was watched before giving up, then surface a calm card.
                    onEndedLocked(completed = false)
                    PlayerEvent.ShowError(calmMessageFor(outcome))
                }
            }
        }
    }

    /** Charges whatever has been played since the last charge. */
    suspend fun tick(): TickResult = mutex.withLock {
        withContext(io) { tickLocked() }
    }

    private fun tickLocked(): TickResult {
        val openSession = session ?: return TickResult.Continue(0)
        val stored = budgetStore.load(openSession.budgetDateIso)
        val (advanced, updated) = accountant.charge(openSession, stored, playedSeconds())
        session = advanced
        budgetStore.save(updated)

        val limitSeconds = settingsStore.load().dailyLimitMinutes * 60
        val remaining = accountant.remainingSeconds(updated, limitSeconds)
        if (remaining <= 0) {
            player.pause()
            return TickResult.BudgetExhausted
        }
        return TickResult.Continue(remaining)
    }

    /**
     * Stream URLs expire after a few hours, so one silent re-resolve is worth trying.
     * Exactly one: a child sitting in front of a retry loop is worse than a calm card.
     */
    suspend fun onPlayerFailed(
        failure: String,
        recoverable: Boolean,
        resumeFromMs: Long? = null,
    ): PlaybackOutcome = mutex.withLock {
        withContext(io) { onPlayerFailedLocked(failure, recoverable, resumeFromMs) }
    }

    private suspend fun onPlayerFailedLocked(
        failure: String,
        recoverable: Boolean,
        resumeFromMs: Long? = null,
    ): PlaybackOutcome {
        val ticket = current ?: return PlaybackOutcome.Failed(failure)
        if (!recoverable || retried) return PlaybackOutcome.Failed(failure)
        retried = true

        val position = resumeFromMs ?: player.state.value.positionMs
        val details = resolver.resolve(ticket.ticket).getOrElse { cause ->
            return if (cause is VideoUnavailable) {
                PlaybackOutcome.Unavailable(ticket.ticket.videoId)
            } else {
                PlaybackOutcome.Failed(cause.message ?: failure)
            }
        }

        val confirmed = when (val checked = policy.confirm(ticket.ticket, details)) {
            is ConfirmResult.Denied -> return PlaybackOutcome.Refused(checked.reason)
            is ConfirmResult.Allowed -> checked.confirmed
        }

        current = confirmed
        player.start(confirmed, positionMs = position)
        return PlaybackOutcome.Started(details)
    }

    /**
     * Closes the viewing: a final charge, then the history row. The final charge matters
     * because the 15-second tick cadence cannot see the tail of a short viewing, so
     * without it every viewing under 15 seconds would cost only the up-front debit.
     */
    suspend fun onEnded(completed: Boolean) = mutex.withLock {
        withContext(io) { onEndedLocked(completed) }
    }

    private fun onEndedLocked(completed: Boolean) {
        val openSession = session ?: return
        stopCountingPlayTime()

        val watched = playedSeconds()
        val stored = budgetStore.load(openSession.budgetDateIso)
        val (_, reconciled) = accountant.charge(openSession, stored, watched)
        budgetStore.save(reconciled)

        ledger.append(
            WatchEntry(
                videoId = openSession.videoId.value,
                channelId = currentChannelId,
                title = current?.details?.title.orEmpty(),
                startedAtEpochSeconds = clock.nowEpochMillis() / 1_000 - watched,
                watchedSeconds = watched,
                completed = completed,
            ),
        )
        // Clearing the session is what makes a second onEnded a no-op rather than a
        // duplicate history row.
        stop()
    }

    fun stop() {
        session = null
        current = null
        currentChannelId = null
        retried = false
        playingSinceRealtimeMs = null
        accumulatedPlayedMs = 0
    }

    private fun stopCountingPlayTime() {
        val since = playingSinceRealtimeMs ?: return
        accumulatedPlayedMs += (clock.elapsedRealtimeMillis() - since).coerceAtLeast(0)
        playingSinceRealtimeMs = null
    }

    private fun playedSeconds(): Int {
        val since = playingSinceRealtimeMs
        val live = if (since == null) 0L else (clock.elapsedRealtimeMillis() - since).coerceAtLeast(0)
        return ((accumulatedPlayedMs + live) / 1_000L).toInt()
    }
}
