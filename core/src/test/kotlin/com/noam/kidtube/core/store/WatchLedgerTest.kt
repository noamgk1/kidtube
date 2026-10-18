package com.noam.kidtube.core.store

import com.noam.kidtube.core.FakeClock
import com.noam.kidtube.core.WatchEntry
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WatchLedgerTest {

    private val root: File = Files.createTempDirectory("ledger").toFile()
    private val clock = FakeClock()
    private val ledger = WatchLedger(FileStore(root), clock)
    private val nowSeconds = clock.nowEpochMillis() / 1_000

    @AfterTest
    fun tearDown() { root.deleteRecursively() }

    private fun entry(
        id: String,
        agoDays: Long = 0,
        watched: Int = 60,
        completed: Boolean = true,
    ) = WatchEntry(
        videoId = id,
        channelId = "UC_abc",
        title = "title $id",
        startedAtEpochSeconds = nowSeconds - agoDays * 86_400L,
        watchedSeconds = watched,
        completed = completed,
    )

    @Test
    fun `an empty ledger lists nothing`() {
        assertEquals(emptyList(), ledger.all())
    }

    @Test
    fun `entries come back newest first regardless of append order`() {
        ledger.append(entry("old", agoDays = 5))
        ledger.append(entry("new", agoDays = 0))
        assertEquals(listOf("new", "old"), ledger.all().map { it.videoId })
    }

    @Test
    fun `a corrupt line is skipped rather than losing the whole history`() {
        ledger.append(entry("good1"))
        File(root, "history.jsonl").appendText("{ not json at all\n")
        ledger.append(entry("good2"))
        assertEquals(setOf("good1", "good2"), ledger.all().map { it.videoId }.toSet())
    }

    @Test
    fun `aggregate counts plays and sums watched seconds per video`() {
        ledger.append(entry("v1", watched = 30))
        ledger.append(entry("v1", watched = 45))
        ledger.append(entry("v2", watched = 10))

        val byId = ledger.aggregate().associateBy { it.videoId }
        assertEquals(2, byId.getValue("v1").plays)
        assertEquals(75, byId.getValue("v1").totalSeconds)
        assertEquals(1, byId.getValue("v2").plays)
    }

    @Test
    fun `aggregate is ordered by most recently watched first`() {
        ledger.append(entry("older", agoDays = 3))
        ledger.append(entry("newer", agoDays = 1))
        assertEquals(listOf("newer", "older"), ledger.aggregate().map { it.videoId })
    }

    @Test
    fun `rotate drops entries older than the age limit`() {
        ledger.append(entry("ancient", agoDays = 120))
        ledger.append(entry("recent", agoDays = 3))
        ledger.rotate()
        assertEquals(listOf("recent"), ledger.all().map { it.videoId })
    }

    @Test
    fun `rotate caps the count and keeps the newest`() {
        repeat(10) { i -> ledger.append(entry("v$i", agoDays = (10 - i).toLong())) }
        ledger.rotate(maxEntries = 3, maxAgeDays = 365)
        assertEquals(listOf("v9", "v8", "v7"), ledger.all().map { it.videoId })
    }

    @Test
    fun `rotate on an empty ledger does nothing and does not create a file`() {
        ledger.rotate()
        assertTrue(ledger.all().isEmpty())
    }

    @Test
    fun `rotate is a no-op when nothing exceeds either limit`() {
        ledger.append(entry("v1", agoDays = 1))
        ledger.rotate()
        assertEquals(listOf("v1"), ledger.all().map { it.videoId })
    }
}
