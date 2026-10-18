package com.noam.kidtube.core

import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class Budget(
    /** ISO date this record accounts for. */
    val dateIso: String,
    val consumedSeconds: Int = 0,
    /** One-off grants from the admin UI, cleared with the day. */
    val bonusSeconds: Int = 0,
)

/**
 * An open playback session. It remembers which day it began on, so a video that runs
 * across midnight keeps consuming that day rather than being handed a new one.
 */
class WatchSession internal constructor(
    val videoId: VideoId,
    val startedRealtimeMs: Long,
    val budgetDateIso: String,
    val debitedSeconds: Int,
)

class BudgetAccountant(private val clock: Clock) {

    /**
     * Normalises a stored record for the current day. Called from the issue path only,
     * never while a session is open.
     *
     * A record dated in the future means the device clock was moved backwards; the
     * consumption is kept rather than wiped, so turning the clock back buys nothing.
     */
    fun forToday(stored: Budget): Budget {
        val today = clock.today()
        val storedDate = runCatching { LocalDate.parse(stored.dateIso) }.getOrNull()
        return when {
            storedDate == null -> Budget(today.toString())
            storedDate == today -> stored
            storedDate.isAfter(today) -> stored
            else -> Budget(today.toString())
        }
    }

    fun remainingSeconds(stored: Budget, dailyLimitSeconds: Int): Int =
        (dailyLimitSeconds + stored.bonusSeconds - stored.consumedSeconds).coerceAtLeast(0)

    /**
     * Debits [upfrontSeconds] the moment playback starts. Without this, a child who
     * force-kills the app every fourteen seconds would never be charged for anything,
     * because reconciliation only lands every fifteen. Charging first inverts the
     * incentive: the loop spends the allowance rather than preserving it.
     */
    fun openSession(
        videoId: VideoId,
        stored: Budget,
        upfrontSeconds: Int = UPFRONT_DEBIT_SECONDS,
    ): Pair<WatchSession, Budget> {
        val session = WatchSession(
            videoId = videoId,
            startedRealtimeMs = clock.elapsedRealtimeMillis(),
            budgetDateIso = stored.dateIso,
            debitedSeconds = upfrontSeconds,
        )
        return session to stored.copy(consumedSeconds = stored.consumedSeconds + upfrontSeconds)
    }

    /**
     * Charges whatever has been watched beyond what was already taken. Uses the
     * monotonic clock, so moving the wall clock during a video changes nothing.
     */
    fun tick(session: WatchSession, stored: Budget): Pair<WatchSession, Budget> {
        require(stored.dateIso == session.budgetDateIso) {
            "budget record rolled from ${session.budgetDateIso} to ${stored.dateIso} while a " +
                "session was open; forToday() must be called from the issue path only"
        }
        val watched =
            ((clock.elapsedRealtimeMillis() - session.startedRealtimeMs) / 1_000L)
                .toInt()
                .coerceAtLeast(0)
        val shortfall = watched - session.debitedSeconds
        if (shortfall <= 0) return session to stored

        val advanced = WatchSession(
            videoId = session.videoId,
            startedRealtimeMs = session.startedRealtimeMs,
            budgetDateIso = session.budgetDateIso,
            debitedSeconds = watched,
        )
        return advanced to stored.copy(consumedSeconds = stored.consumedSeconds + shortfall)
    }

    fun grantBonus(stored: Budget, seconds: Int): Budget =
        stored.copy(bonusSeconds = stored.bonusSeconds + seconds)

    companion object {
        /** Matches the fifteen-second persistence interval in the spec. */
        const val UPFRONT_DEBIT_SECONDS: Int = 15
    }
}
