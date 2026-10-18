package com.noam.kidtube.app.kid

import kotlin.test.Test
import kotlin.test.assertEquals

class VideoPresentationTest {

    @Test
    fun `an unknown duration shows nothing rather than a zero`() {
        // The RSS fast path carries no duration, so this is the common case for a video
        // that arrived since the last deep sync.
        assertEquals("", formatDuration(null))
        assertEquals("", formatDuration(0))
    }

    @Test
    fun `under a minute still reads as minutes and seconds`() {
        assertEquals("0:07", formatDuration(7))
        assertEquals("0:59", formatDuration(59))
    }

    @Test
    fun `minutes and seconds are zero padded`() {
        assertEquals("1:00", formatDuration(60))
        assertEquals("3:45", formatDuration(225))
        assertEquals("9:05", formatDuration(545))
    }

    @Test
    fun `an hour switches to three parts`() {
        assertEquals("1:00:00", formatDuration(3_600))
        assertEquals("1:02:03", formatDuration(3_723))
        assertEquals("12:00:00", formatDuration(43_200))
    }

    @Test
    fun `a negative duration is treated as unknown`() {
        assertEquals("", formatDuration(-5))
    }

    @Test
    fun `no progress means no bar`() {
        assertEquals(0f, progressFraction(positionMs = 0, durationSeconds = 600))
    }

    @Test
    fun `progress is the fraction watched`() {
        assertEquals(0.5f, progressFraction(positionMs = 300_000, durationSeconds = 600))
    }

    @Test
    fun `an unknown duration cannot produce a bar`() {
        assertEquals(0f, progressFraction(positionMs = 300_000, durationSeconds = null))
        assertEquals(0f, progressFraction(positionMs = 300_000, durationSeconds = 0))
    }

    @Test
    fun `progress never exceeds a full bar`() {
        assertEquals(1f, progressFraction(positionMs = 900_000, durationSeconds = 600))
    }
}
