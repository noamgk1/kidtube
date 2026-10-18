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
import com.noam.kidtube.engine.PlaybackResolver
import com.noam.kidtube.engine.VideoPlayer
import com.noam.kidtube.engine.VideoUnavailable

sealed interface PlaybackOutcome {
    data class Started(val details: VideoDetails) : PlaybackOutcome
    data class Refused(val reason: DenyReason) : PlaybackOutcome

    /** The video is gone for good: deleted, private, region-blocked. Never retried. */
    data class Unavailable(val videoId: VideoId) : PlaybackOutcome
    data class Failed(val message: String) : PlaybackOutcome
}

sealed interface TickResult {
    data class Continue(val remainingSeconds: Int) : TickResult
    data object BudgetExhausted : TickResult
}

/**
 * Runs the four-step dance that keeps the whitelist promise structural:
 * Policy.issue, resolver.resolve, Policy.confirm, player.start. The player only ever
 * receives a [ConfirmedTicket], and only Policy can mint one.
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
) {
    private var session: WatchSession? = null
    private var current: ConfirmedTicket? = null
    private var currentChannelId: String? = null
    private var retried = false

    suspend fun play(video: VideoRef): PlaybackOutcome {
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

        player.start(confirmed, positionMs = 0)
        return PlaybackOutcome.Started(details)
    }

    /** Charges whatever has been watched since the last tick. */
    fun tick(): TickResult {
        val openSession = session ?: return TickResult.Continue(0)
        val stored = budgetStore.load(openSession.budgetDateIso)
        val (advanced, updated) = accountant.tick(openSession, stored)
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
    suspend fun onPlayerFailed(failure: String, recoverable: Boolean): PlaybackOutcome {
        val ticket = current ?: return PlaybackOutcome.Failed(failure)
        if (!recoverable || retried) return PlaybackOutcome.Failed(failure)
        retried = true

        val position = player.state.value.positionMs
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

    fun onEnded(completed: Boolean) {
        val openSession = session ?: return
        val title = current?.details?.title.orEmpty()

        val watched =
            ((clock.elapsedRealtimeMillis() - openSession.startedRealtimeMs) / 1_000L)
                .toInt()
                .coerceAtLeast(0)

        ledger.append(
            WatchEntry(
                videoId = openSession.videoId.value,
                channelId = currentChannelId,
                title = title,
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
    }
}
