package com.noam.kidtube.app.admin

import com.noam.kidtube.app.sync.CatalogSync
import com.noam.kidtube.core.Channel
import com.noam.kidtube.core.ChannelId
import com.noam.kidtube.core.VideoId
import com.noam.kidtube.core.VideoRef
import com.noam.kidtube.core.Clock
import com.noam.kidtube.core.store.CatalogStore
import com.noam.kidtube.core.store.FileStore
import com.noam.kidtube.core.store.SyncStateStore
import com.noam.kidtube.core.store.WhitelistStore
import com.noam.kidtube.engine.SearchKind
import com.noam.kidtube.engine.SearchResult
import com.noam.kidtube.engine.UnsupportedReason
import com.noam.kidtube.engine.fake.FakeContentSource
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private object AddClock : Clock {
    override fun nowEpochMillis(): Long = 1_760_000_000_000L
    override fun elapsedRealtimeMillis(): Long = 0
    override fun zone(): ZoneId = ZoneId.of("Asia/Jerusalem")
}

class AddFlowTest {

    private val root: File = Files.createTempDirectory("addflow").toFile()
    private val files = FileStore(root)
    private val whitelistStore = WhitelistStore(files)
    private val catalogStore = CatalogStore(files)
    private val source = FakeContentSource()

    private val channelId = ChannelId("UCLsooMJoIpl_7ux2jvdPB-Q")
    private val channel = Channel(channelId, "Super Simple Songs", null, "https://feed", 47_000_000L)

    private val flow = AddFlow(
        source,
        whitelistStore,
        CatalogSync(source, whitelistStore, catalogStore, SyncStateStore(files), AddClock),
    )

    @AfterTest
    fun tearDown() { root.deleteRecursively() }

    @Test
    fun `a handle resolves to a preview the parent can confirm`() = runTest {
        source.scriptChannel("https://www.youtube.com/@SuperSimpleSongs", channel)

        val preview = assertIs<AddPreview.Channel>(
            flow.preview("https://www.youtube.com/@SuperSimpleSongs"),
        )
        assertEquals("Super Simple Songs", preview.channel.title)
        assertEquals(47_000_000L, preview.channel.subscriberCount)
        assertFalse(preview.alreadyApproved)
    }

    @Test
    fun `nothing is written until the parent confirms`() = runTest {
        source.scriptChannel("https://www.youtube.com/@SuperSimpleSongs", channel)
        flow.preview("https://www.youtube.com/@SuperSimpleSongs")
        assertTrue(whitelistStore.load().channels.isEmpty())
    }

    @Test
    fun `confirming writes the channel`() = runTest {
        source.scriptChannel("https://www.youtube.com/@SuperSimpleSongs", channel)
        val preview = assertIs<AddPreview.Channel>(
            flow.preview("https://www.youtube.com/@SuperSimpleSongs"),
        )
        assertTrue(flow.confirmChannel(preview.channel))
        assertTrue(whitelistStore.load().hasChannel(channelId))
    }

    @Test
    fun `a channel already approved is flagged in the preview`() = runTest {
        whitelistStore.addChannel(channel)
        source.scriptChannel("https://www.youtube.com/channel/" + channelId.value, channel)

        val preview = assertIs<AddPreview.Channel>(
            flow.preview("https://www.youtube.com/channel/" + channelId.value),
        )
        assertTrue(
            preview.alreadyApproved,
            "the same channel under a second URL form must be recognised, not duplicated",
        )
    }

    @Test
    fun `a pasted video link offers a single-video add rather than a dead end`() = runTest {
        val preview = assertIs<AddPreview.SingleVideo>(flow.preview("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", preview.videoId.value)
    }

    @Test
    fun `confirming a single video stores it with a derived thumbnail`() = runTest {
        val preview = assertIs<AddPreview.SingleVideo>(flow.preview("https://youtu.be/dQw4w9WgXcQ"))
        assertTrue(flow.confirmSingleVideo(preview.videoId, "a pasted video"))

        val stored = whitelistStore.load().singleVideos.getValue("dQw4w9WgXcQ")
        assertTrue(stored.thumbnailUrl!!.contains("dQw4w9WgXcQ"))
    }

    @Test
    fun `a shorts link is rejected by name`() = runTest {
        val rejected = assertIs<AddPreview.Rejected>(
            flow.preview("https://www.youtube.com/shorts/abcdefghijk"),
        )
        assertEquals(UnsupportedReason.SHORTS, rejected.reason)
    }

    @Test
    fun `plain text is rejected before any network call`() = runTest {
        assertIs<AddPreview.Rejected>(flow.preview("super simple songs"))
        assertEquals(0, source.listVideosCalls)
    }

    @Test
    fun `a channel that cannot be resolved is reported, not swallowed`() = runTest {
        val rejected = assertIs<AddPreview.Rejected>(
            flow.preview("https://www.youtube.com/@DoesNotExist"),
        )
        assertTrue(rejected.detail.isNotBlank())
    }

    @Test
    fun `confirming a duplicate channel reports false and adds nothing`() = runTest {
        whitelistStore.addChannel(channel)
        assertFalse(flow.confirmChannel(channel))
        assertEquals(1, whitelistStore.load().channels.size)
    }

    // Searching YouTube from the parent screen. This is the one place in the app that
    // queries YouTube for something that is not yet approved, which is why it sits behind
    // the password and why finding something approves nothing on its own.

    @Test
    fun `a search returns what YouTube gave back`() = runTest {
        val hit = SearchResult.ChannelHit(
            Channel(ChannelId("UC_found"), "Found Channel", null, "feed", 10L),
        )
        source.scriptSearch("kids songs", listOf(hit))

        val page = flow.search("kids songs").getOrThrow()

        assertEquals(listOf(hit), page.results)
    }

    @Test
    fun `an empty query asks YouTube nothing at all`() = runTest {
        val page = flow.search("   ").getOrThrow()

        assertTrue(page.results.isEmpty())
    }

    @Test
    fun `finding a channel does not approve it`() = runTest {
        source.scriptSearch(
            "kids songs",
            listOf(
                SearchResult.ChannelHit(
                    Channel(ChannelId("UC_found"), "Found Channel", null, "feed", 10L),
                ),
            ),
        )

        flow.search("kids songs").getOrThrow()

        assertTrue(
            whitelistStore.load().channels.isEmpty(),
            "a search result the parent has not ticked must not reach the whitelist",
        )
    }

    @Test
    fun `approving a channel from a search re-resolves it first`() = runTest {
        val fromSearch = Channel(ChannelId("UC_found"), "Stale Title", null, "", 10L)
        val resolved = Channel(ChannelId("UC_found"), "Real Title", null, "real-feed", 99L)
        source.scriptChannel("https://www.youtube.com/channel/UC_found", resolved)

        val added = flow.approveFromSearch(SearchResult.ChannelHit(fromSearch)).getOrThrow()
        assertEquals(resolved, added, "the caller needs the resolved channel to sync it")

        val stored = whitelistStore.load().channels.getValue("UC_found")
        assertEquals("Real Title", stored.title, "the title must come from the channel, not the hit")
        assertEquals("real-feed", stored.feedUrl, "a search hit carries no feed of its own")
    }

    @Test
    fun `approving a channel twice reports that there was nothing to add`() = runTest {
        val resolved = Channel(ChannelId("UC_found"), "Real", null, "feed", 1L)
        source.scriptChannel("https://www.youtube.com/channel/UC_found", resolved)
        val hit = SearchResult.ChannelHit(resolved)

        flow.approveFromSearch(hit).getOrThrow()

        assertNull(
            flow.approveFromSearch(hit).getOrThrow(),
            "a second approval must not restart the catalog sync",
        )
    }

    @Test
    fun `approving a channel that has vanished since the search fails`() = runTest {
        val gone = Channel(ChannelId("UC_gone"), "Gone", null, "", 1L)

        assertTrue(flow.approveFromSearch(SearchResult.ChannelHit(gone)).isFailure)
        assertTrue(whitelistStore.load().channels.isEmpty())
    }

    @Test
    fun `approving one video from a search grants nothing else from its channel`() = runTest {
        val hit = SearchResult.VideoHit(
            video = VideoRef(
                VideoId("v_found"),
                ChannelId("UC_someone"),
                "A Song",
                "https://thumb",
                120L,
                500L,
            ),
            channelTitle = "Someone",
        )

        assertTrue(flow.approveFromSearch(hit))

        val whitelist = whitelistStore.load()
        assertTrue(whitelist.channels.isEmpty(), "the channel it came from stays unapproved")
        val stored = whitelist.singleVideos.getValue("v_found")
        assertEquals("", stored.channelId.value)
        assertEquals("https://thumb", stored.thumbnailUrl, "the real thumbnail is kept")
        assertEquals(120L, stored.durationSeconds)
    }

    @Test
    fun `asking for channels passes that through to YouTube`() = runTest {
        // Unfiltered, YouTube answers a search almost entirely with videos, and the one
        // channel a parent came to approve is buried among them.
        source.scriptSearch("kids", emptyList())

        flow.search("kids", kind = SearchKind.CHANNELS)

        assertEquals(SearchKind.CHANNELS, source.lastSearchKind)
    }

    @Test
    fun `an empty query asks YouTube nothing, whatever kind was requested`() = runTest {
        flow.search("  ", kind = SearchKind.VIDEOS)

        assertNull(source.lastSearchKind)
    }
}
