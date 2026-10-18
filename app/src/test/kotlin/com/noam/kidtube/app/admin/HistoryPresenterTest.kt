package com.noam.kidtube.app.admin

import com.noam.kidtube.app.FakeClock
import com.noam.kidtube.app.kid.KidGridLoader
import com.noam.kidtube.core.Channel
import com.noam.kidtube.core.ChannelId
import com.noam.kidtube.core.VideoId
import com.noam.kidtube.core.VideoRef
import com.noam.kidtube.core.WatchEntry
import com.noam.kidtube.core.store.CatalogStore
import com.noam.kidtube.core.store.FileStore
import com.noam.kidtube.core.store.WatchLedger
import com.noam.kidtube.core.store.WhitelistStore
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HistoryPresenterTest {

    private val root: File = Files.createTempDirectory("history").toFile()
    private val files = FileStore(root)
    private val clock = FakeClock()
    private val ledger = WatchLedger(files, clock)
    private val whitelistStore = WhitelistStore(files)
    private val catalogStore = CatalogStore(files)
    private val presenter = HistoryPresenter(ledger, whitelistStore)

    private val nowSeconds = clock.nowEpochMillis() / 1_000

    @AfterTest
    fun tearDown() { root.deleteRecursively() }

    private fun watched(id: String, agoDays: Long, seconds: Int) = WatchEntry(
        videoId = id,
        channelId = "UC_abc",
        title = "title " + id,
        startedAtEpochSeconds = nowSeconds - agoDays * 86_400L,
        watchedSeconds = seconds,
        completed = true,
    )

    @Test
    fun `an empty history shows no rows`() {
        assertTrue(presenter.rows().isEmpty())
    }

    @Test
    fun `repeat viewings collapse into one row with a play count`() {
        ledger.append(watched("v1", 1, 30))
        ledger.append(watched("v1", 0, 45))

        val rows = presenter.rows()
        assertEquals(1, rows.size)
        assertEquals(2, rows[0].plays)
        assertEquals(75, rows[0].totalSeconds)
    }

    @Test
    fun `rows are ordered by most recently watched`() {
        ledger.append(watched("older", 5, 10))
        ledger.append(watched("newer", 1, 10))
        assertEquals(listOf("newer", "older"), presenter.rows().map { it.videoId })
    }

    @Test
    fun `a row shows whether that video is already blocked`() {
        ledger.append(watched("v1", 0, 10))
        assertFalse(presenter.rows()[0].blocked)

        presenter.block(VideoId("v1"))
        assertTrue(presenter.rows()[0].blocked)
    }

    @Test
    fun `blocking from history removes the video from the kid grid`() {
        val channelId = ChannelId("UC_abc")
        whitelistStore.addChannel(Channel(channelId, "Kids", null, "f", 1L))
        catalogStore.save(
            channelId,
            listOf(VideoRef(VideoId("v1"), channelId, "t", null, 60L, 900L)),
        )
        ledger.append(watched("v1", 0, 10))

        val grid = KidGridLoader(whitelistStore, catalogStore)
        assertEquals(1, grid.load(null).videos.size)

        presenter.block(VideoId("v1"))
        assertTrue(
            grid.load(null).videos.isEmpty(),
            "the point of blocking from history is that it takes effect on the child's screen",
        )
    }

    @Test
    fun `unblocking restores the video`() {
        ledger.append(watched("v1", 0, 10))
        presenter.block(VideoId("v1"))
        presenter.unblock(VideoId("v1"))
        assertFalse(presenter.rows()[0].blocked)
    }

    @Test
    fun `blocking a video never watched is still possible and harmless`() {
        presenter.block(VideoId("never-seen"))
        assertTrue(whitelistStore.load().isBlocked(VideoId("never-seen")))
        assertTrue(presenter.rows().isEmpty())
    }
}
