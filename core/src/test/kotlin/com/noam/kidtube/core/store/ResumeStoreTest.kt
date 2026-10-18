package com.noam.kidtube.core.store

import com.noam.kidtube.core.FakeClock
import com.noam.kidtube.core.VideoId
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where a video was left off. Two rules keep the "continue watching" row honest: a
 * glance is not progress, and a video watched to the end is finished rather than
 * half-watched.
 */
class ResumeStoreTest {

    private val root: File = Files.createTempDirectory("resume").toFile()
    private val clock = FakeClock()
    private val store = ResumeStore(FileStore(root), clock)

    @AfterTest
    fun tearDown() { root.deleteRecursively() }

    private val tenMinutes = 600_000L

    @Test
    fun `nothing is remembered for a video never watched`() {
        assertEquals(0L, store.positionFor(VideoId("v1")))
        assertTrue(store.continueWatching().isEmpty())
    }

    @Test
    fun `a position in the middle is remembered`() {
        store.save(VideoId("v1"), positionMs = 120_000, durationMs = tenMinutes)
        assertEquals(120_000L, store.positionFor(VideoId("v1")))
    }

    @Test
    fun `a glance is not progress`() {
        // Under ten seconds: the child tapped and changed their mind.
        store.save(VideoId("v1"), positionMs = 4_000, durationMs = tenMinutes)
        assertEquals(0L, store.positionFor(VideoId("v1")))
        assertTrue(store.continueWatching().isEmpty())
    }

    @Test
    fun `a video watched to the end is finished, not half-watched`() {
        store.save(VideoId("v1"), positionMs = tenMinutes - 3_000, durationMs = tenMinutes)
        assertEquals(0L, store.positionFor(VideoId("v1")))
        assertTrue(store.continueWatching().isEmpty())
    }

    @Test
    fun `reaching the end clears an earlier position`() {
        store.save(VideoId("v1"), positionMs = 120_000, durationMs = tenMinutes)
        store.save(VideoId("v1"), positionMs = tenMinutes, durationMs = tenMinutes)
        assertEquals(0L, store.positionFor(VideoId("v1")))
    }

    @Test
    fun `a later position replaces an earlier one`() {
        store.save(VideoId("v1"), positionMs = 60_000, durationMs = tenMinutes)
        store.save(VideoId("v1"), positionMs = 180_000, durationMs = tenMinutes)
        assertEquals(180_000L, store.positionFor(VideoId("v1")))
        assertEquals(1, store.continueWatching().size)
    }

    @Test
    fun `continue watching lists the most recently watched first`() {
        store.save(VideoId("first"), 60_000, tenMinutes)
        clock.setDate(java.time.LocalDate.of(2026, 10, 19)) // the next day
        store.save(VideoId("second"), 60_000, tenMinutes)

        assertEquals(listOf("second", "first"), store.continueWatching().map { it.videoId })
    }

    @Test
    fun `continue watching can be capped`() {
        repeat(10) { store.save(VideoId("v" + it), 60_000, tenMinutes) }
        assertEquals(3, store.continueWatching(limit = 3).size)
    }

    @Test
    fun `clearing a video forgets it`() {
        store.save(VideoId("v1"), 120_000, tenMinutes)
        store.clear(VideoId("v1"))
        assertEquals(0L, store.positionFor(VideoId("v1")))
    }

    @Test
    fun `an unknown duration is not treated as a finished video`() {
        store.save(VideoId("v1"), positionMs = 120_000, durationMs = 0)
        assertEquals(120_000L, store.positionFor(VideoId("v1")))
    }

    @Test
    fun `the list is bounded so it cannot grow without limit`() {
        repeat(ResumeStore.MAX_REMEMBERED + 20) { store.save(VideoId("v" + it), 60_000, tenMinutes) }
        assertTrue(store.continueWatching(limit = 1_000).size <= ResumeStore.MAX_REMEMBERED)
    }

    @Test
    fun `a corrupt file loses the positions rather than the app`() {
        File(root, "resume.json").writeText("{ not json")
        assertEquals(0L, store.positionFor(VideoId("v1")))
        store.save(VideoId("v1"), 120_000, tenMinutes)
        assertEquals(120_000L, store.positionFor(VideoId("v1")))
    }
}
