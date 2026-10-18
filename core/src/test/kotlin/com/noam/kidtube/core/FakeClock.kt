package com.noam.kidtube.core

import java.time.LocalDate
import java.time.ZoneId

/** Shared across :core tests. Wall clock and monotonic clock move independently on purpose. */
class FakeClock(
    private var date: LocalDate = LocalDate.of(2026, 10, 18),
    var realtimeMillis: Long = 1_000_000L,
    private val zone: ZoneId = ZoneId.of("Asia/Jerusalem"),
) : Clock {
    override fun nowEpochMillis(): Long =
        date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    override fun elapsedRealtimeMillis(): Long = realtimeMillis

    override fun zone(): ZoneId = zone

    fun advanceRealtime(seconds: Long) { realtimeMillis += seconds * 1_000L }

    fun setDate(newDate: LocalDate) { date = newDate }
}
