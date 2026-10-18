package com.noam.kidtube.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The seam that makes time testable. [elapsedRealtimeMillis] is monotonic and is the only
 * thing budget accounting may use; the wall clock is for dates alone.
 */
interface Clock {
    fun nowEpochMillis(): Long
    fun elapsedRealtimeMillis(): Long
    fun zone(): ZoneId
}

fun Clock.today(): LocalDate =
    Instant.ofEpochMilli(nowEpochMillis()).atZone(zone()).toLocalDate()
