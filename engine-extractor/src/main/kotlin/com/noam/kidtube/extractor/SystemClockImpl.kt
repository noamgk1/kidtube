package com.noam.kidtube.extractor

import android.os.SystemClock
import com.noam.kidtube.core.Clock
import java.time.ZoneId

/** The production clock: wall time for dates, monotonic time for budget accounting. */
object SystemClockImpl : Clock {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
