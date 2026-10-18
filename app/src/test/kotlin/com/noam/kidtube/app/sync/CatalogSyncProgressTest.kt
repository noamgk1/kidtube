package com.noam.kidtube.app.sync

import com.noam.kidtube.core.Channel
import com.noam.kidtube.core.ChannelId
import com.noam.kidtube.core.Clock
import com.noam.kidtube.core.SyncState
import com.noam.kidtube.core.VideoId
import com.noam.kidtube.core.VideoRef
import com.noam.kidtube.core.store.CatalogStore
import com.noam.kidtube.core.store.FileStore
import com.noam.kidtube.core.store.SyncStateStore
import com.noam.kidtube.core.store.WhitelistStore
import com.noam.kidtube.engine.Cursor
import com.noam.kidtube.engine.VideoPage
import com.noam.kidtube.engine.fake.FakeContentSource
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private object TickingClock : Clock {
    override fun nowEpochMillis(): Long = 1_760_000_000_000L
    override fun elapsedRealtimeMillis(): Long = 0
    override fun zone(): ZoneId = ZoneId.of("Asia/Jerusalem")
}

/**
 * Progress has to reach the disk after every page, not only when the whole sync
 * finishes. A large kids channel is around 35 pages and up to four minutes; if the
 * process dies at page 6 and nothing was written, the parent's status screen shows
 * nothing wrong while the catalog stays permanently truncated.
 */
class CatalogSyncProgressTest {

    private val root: File = Files.createTempDirectory("sync-progress").toFile()
    private val files = FileStore(root)
    private val whitelistStore = WhitelistStore(files)
    private val catalogStore = CatalogStore(files)
    private val syncStateStore = SyncStateStore(files)
    private val source = FakeContentSource()

    private val channelId = ChannelId("UC_abc")

    @AfterTest
    fun tearDown() { root.deleteRecursively() }

    private fun video(id: String, published: Long) =
        VideoRef(VideoId(id), channelId, "t " + id, null, 60L, published)

    private fun sync(onProgress: (SyncState) -> Unit = {}) = CatalogSync(
        source,
        whitelistStore,
        catalogStore,
        syncStateStore,
        TickingClock,
        onProgress = onProgress,
    )

    private fun threePages() = listOf(
        VideoPage(listOf(video("a", 900), video("b", 800)), Cursor("p2")),
        VideoPage(listOf(video("c", 700), video("d", 600)), Cursor("p3")),
        VideoPage(listOf(video("e", 500)), null),
    )

    @Test
    fun `progress is reported after every page, not only at the end`() = runTest {
        whitelistStore.addChannel(Channel(channelId, "Kids", null, "f", 1L))
        source.scriptPages(channelId, threePages())

        val seen = mutableListOf<Int>()
        sync(onProgress = { seen += it.pagesDone }).deepSync(channelId)

        assertEquals(listOf(1, 2, 3), seen)
    }

    @Test
    fun `each report carries the running video count`() = runTest {
        whitelistStore.addChannel(Channel(channelId, "Kids", null, "f", 1L))
        source.scriptPages(channelId, threePages())

        val seen = mutableListOf<Int>()
        sync(onProgress = { seen += it.videosStored }).deepSync(channelId)

        assertEquals(listOf(2, 4, 5), seen)
    }

    @Test
    fun `the persisted state is updated as each page lands`() = runTest {
        whitelistStore.addChannel(Channel(channelId, "Kids", null, "f", 1L))
        source.scriptPages(channelId, threePages())

        // Read the store from inside the progress callback: whatever is on disk at that
        // moment is what a crash would leave behind.
        val persisted = mutableListOf<Int>()
        sync(onProgress = { persisted += syncStateStore.load(channelId).pagesDone })
            .deepSync(channelId)

        assertEquals(listOf(1, 2, 3), persisted)
    }

    @Test
    fun `an interrupted sync leaves honest progress on disk rather than nothing`() = runTest {
        whitelistStore.addChannel(Channel(channelId, "Kids", null, "f", 1L))
        source.scriptPages(channelId, threePages())
        source.failPagesAfter = 2

        sync().deepSync(channelId)

        val state = syncStateStore.load(channelId)
        assertEquals(2, state.pagesDone)
        assertEquals(4, state.videosStored)
        assertFalse(state.complete)
        assertTrue(state.lastError!!.isNotBlank())
    }

    @Test
    fun `only the last report of a finished sync is marked complete`() = runTest {
        whitelistStore.addChannel(Channel(channelId, "Kids", null, "f", 1L))
        source.scriptPages(channelId, threePages())

        val completeFlags = mutableListOf<Boolean>()
        sync(onProgress = { completeFlags += it.complete }).deepSync(channelId)

        assertEquals(listOf(false, false, true), completeFlags)
    }
}
