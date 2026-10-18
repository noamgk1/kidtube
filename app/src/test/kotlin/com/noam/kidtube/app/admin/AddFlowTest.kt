package com.noam.kidtube.app.admin

import com.noam.kidtube.app.sync.CatalogSync
import com.noam.kidtube.core.Channel
import com.noam.kidtube.core.ChannelId
import com.noam.kidtube.core.Clock
import com.noam.kidtube.core.store.CatalogStore
import com.noam.kidtube.core.store.FileStore
import com.noam.kidtube.core.store.SyncStateStore
import com.noam.kidtube.core.store.WhitelistStore
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
}
