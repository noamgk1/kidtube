package com.noam.kidtube.app.sync

import com.noam.kidtube.core.Channel
import com.noam.kidtube.core.ChannelId
import com.noam.kidtube.core.Clock
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

private object FixedClock : Clock {
    override fun nowEpochMillis(): Long = 1_760_000_000_000L
    override fun elapsedRealtimeMillis(): Long = 0
    override fun zone(): ZoneId = ZoneId.of("Asia/Jerusalem")
}

class CatalogSyncTest {

    private val root: File = Files.createTempDirectory("sync").toFile()
    private val files = FileStore(root)
    private val whitelistStore = WhitelistStore(files)
    private val catalogStore = CatalogStore(files)
    private val syncStateStore = SyncStateStore(files)
    private val source = FakeContentSource()

    private val channelId = ChannelId("UC_abc")
    private val channel = Channel(channelId, "Kids", null, "https://feed", 1L)

    private fun sync(maxPages: Int = CatalogSync.MAX_PAGES) =
        CatalogSync(source, whitelistStore, catalogStore, syncStateStore, FixedClock, maxPages)

    @AfterTest
    fun tearDown() { root.deleteRecursively() }

    private fun video(id: String, published: Long) =
        VideoRef(VideoId(id), channelId, "t " + id, null, 60L, published)

    private fun threePages() = listOf(
        VideoPage(listOf(video("a", 900), video("b", 800)), Cursor("p2")),
        VideoPage(listOf(video("c", 700), video("d", 600)), Cursor("p3")),
        VideoPage(listOf(video("e", 500)), null),
    )

    @Test
    fun `a deep sync walks every page and stores everything`() = runTest {
        whitelistStore.addChannel(channel)
        source.scriptPages(channelId, threePages())

        assertEquals(5, sync().deepSync(channelId).getOrThrow())
        assertEquals(
            listOf("a", "b", "c", "d", "e"),
            catalogStore.load(channelId).map { it.id.value },
        )
    }

    @Test
    fun `a completed deep sync is recorded so the parent sees it finished`() = runTest {
        whitelistStore.addChannel(channel)
        source.scriptPages(channelId, threePages())
        sync().deepSync(channelId)

        val state = syncStateStore.load(channelId)
        assertTrue(state.complete)
        assertEquals(3, state.pagesDone)
        assertEquals(5, state.videosStored)
        assertNull(state.lastError)
    }

    @Test
    fun `a failure midway leaves a valid partial catalog and records the error`() = runTest {
        whitelistStore.addChannel(channel)
        source.scriptPages(channelId, threePages())
        source.failPagesAfter = 2

        assertTrue(sync().deepSync(channelId).isFailure)
        assertEquals(listOf("a", "b", "c", "d"), catalogStore.load(channelId).map { it.id.value })

        val state = syncStateStore.load(channelId)
        assertFalse(state.complete)
        assertTrue(state.lastError!!.isNotBlank())
    }

    @Test
    fun `resuming after an interruption re-pages and de-duplicates rather than doubling up`() = runTest {
        whitelistStore.addChannel(channel)
        source.scriptPages(channelId, threePages())
        source.failPagesAfter = 2
        sync().deepSync(channelId)

        source.failPagesAfter = null
        assertEquals(1, sync().deepSync(channelId).getOrThrow(), "only the unseen tail is new")
        assertEquals(
            listOf("a", "b", "c", "d", "e"),
            catalogStore.load(channelId).map { it.id.value },
        )
    }

    @Test
    fun `a page cap stops a runaway catalog`() = runTest {
        whitelistStore.addChannel(channel)
        source.scriptPages(channelId, threePages())

        sync(maxPages = 2).deepSync(channelId).getOrThrow()
        assertEquals(4, catalogStore.load(channelId).size)
        assertFalse(syncStateStore.load(channelId).complete, "a capped sync is not complete")
    }

    @Test
    fun `an empty channel completes cleanly rather than erroring`() = runTest {
        whitelistStore.addChannel(channel)
        source.scriptPages(channelId, listOf(VideoPage(emptyList(), null)))

        assertEquals(0, sync().deepSync(channelId).getOrThrow())
        assertTrue(syncStateStore.load(channelId).complete)
    }

    @Test
    fun `refresh merges new rss entries and reports the count`() = runTest {
        whitelistStore.addChannel(channel)
        catalogStore.save(channelId, listOf(video("old", 100)))
        source.scriptFeed(channelId, listOf(video("fresh", 900), video("old", 100)))

        assertEquals(1, sync().refresh(channelId).getOrThrow())
        assertEquals(listOf("fresh", "old"), catalogStore.load(channelId).map { it.id.value })
    }

    @Test
    fun `refresh on an unknown channel fails rather than creating a catalog`() = runTest {
        assertTrue(sync().refresh(ChannelId("UC_never_added")).isFailure)
        assertEquals(emptyList(), catalogStore.channelIds())
    }

    @Test
    fun `refreshAll covers every approved channel and isolates failures`() = runTest {
        val other = ChannelId("UC_xyz")
        whitelistStore.addChannel(channel)
        whitelistStore.addChannel(Channel(other, "Other", null, "https://feed2", 1L))
        source.scriptFeed(channelId, listOf(video("new1", 900)))
        // No feed scripted for `other`: it yields an empty list, not an exception.

        val results = sync().refreshAll()
        assertEquals(setOf(channelId, other), results.keys)
        assertEquals(1, results.getValue(channelId).getOrThrow())
        assertEquals(0, results.getValue(other).getOrThrow())
    }

    @Test
    fun `refresh does not touch the sync state, which belongs to deep paging`() = runTest {
        whitelistStore.addChannel(channel)
        source.scriptPages(channelId, threePages())
        sync().deepSync(channelId)
        source.scriptFeed(channelId, listOf(video("fresh", 950)))

        sync().refresh(channelId)
        assertTrue(syncStateStore.load(channelId).complete, "a refresh must not reopen a finished sync")
    }
}
