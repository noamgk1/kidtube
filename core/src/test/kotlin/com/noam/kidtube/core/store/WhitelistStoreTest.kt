package com.noam.kidtube.core.store

import com.noam.kidtube.core.Channel
import com.noam.kidtube.core.ChannelId
import com.noam.kidtube.core.VideoId
import com.noam.kidtube.core.VideoRef
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WhitelistStoreTest {

    private val root: File = Files.createTempDirectory("whitelist").toFile()
    private val store = WhitelistStore(FileStore(root))

    @AfterTest
    fun tearDown() { root.deleteRecursively() }

    private fun channel(id: String, title: String, feed: String) =
        Channel(ChannelId(id), title, null, feed, 1L)

    @Test
    fun `an unseeded store loads an empty whitelist`() {
        assertEquals(0, store.load().channels.size)
    }

    @Test
    fun `adding a channel persists it`() {
        assertTrue(store.addChannel(channel("UC_abc", "Kids", "feed")))
        assertTrue(store.load().hasChannel(ChannelId("UC_abc")))
    }

    @Test
    fun `the same channel pasted in two url forms is one entry`() {
        assertTrue(store.addChannel(channel("UC_abc", "Kids Channel", "feed?channel_id=UC_abc")))
        assertFalse(
            store.addChannel(channel("UC_abc", "Kids Channel - Official", "feed?channel_id=UC_abc")),
            "a duplicate add must report false rather than overwrite",
        )

        val loaded = store.load()
        assertEquals(1, loaded.channels.size)
        assertEquals(
            "Kids Channel",
            loaded.channels.getValue("UC_abc").title,
            "the first add wins; a duplicate must not silently relabel an approved channel",
        )
    }

    @Test
    fun `a genuinely different channel is added alongside`() {
        store.addChannel(channel("UC_abc", "A", "f1"))
        assertTrue(store.addChannel(channel("UC_xyz", "B", "f2")))
        assertEquals(2, store.load().channels.size)
    }

    @Test
    fun `removing a channel leaves the others`() {
        store.addChannel(channel("UC_abc", "A", "f1"))
        store.addChannel(channel("UC_xyz", "B", "f2"))
        store.removeChannel(ChannelId("UC_abc"))

        val loaded = store.load()
        assertFalse(loaded.hasChannel(ChannelId("UC_abc")))
        assertTrue(loaded.hasChannel(ChannelId("UC_xyz")))
    }

    @Test
    fun `a single video is added once and reported duplicate thereafter`() {
        val v = VideoRef(VideoId("v1"), ChannelId("UC_abc"), "t", null, 10L, null)
        assertTrue(store.addSingleVideo(v))
        assertFalse(store.addSingleVideo(v))
        assertEquals(1, store.load().singleVideos.size)
    }

    @Test
    fun `blocking and unblocking a video round trips`() {
        store.block(VideoId("v1"))
        assertTrue(store.load().isBlocked(VideoId("v1")))
        store.unblock(VideoId("v1"))
        assertFalse(store.load().isBlocked(VideoId("v1")))
    }

    @Test
    fun `blocking is idempotent`() {
        store.block(VideoId("v1"))
        store.block(VideoId("v1"))
        assertEquals(setOf("v1"), store.load().blockedVideoIds)
    }

    @Test
    fun `a blocked video stays blocked after its channel is removed`() {
        store.addChannel(channel("UC_abc", "A", "f1"))
        store.block(VideoId("v1"))
        store.removeChannel(ChannelId("UC_abc"))
        assertTrue(store.load().isBlocked(VideoId("v1")))
    }

    // An individually approved video had no way off the list at all: the parent's screen
    // only ever listed channels, so a video approved from a search or a pasted link was
    // permanent.

    @Test
    fun `an individually approved video can be taken back off`() {
        store.addSingleVideo(VideoRef(VideoId("solo"), ChannelId(""), "Solo", null, 10L, null))

        store.removeSingleVideo(VideoId("solo"))

        assertFalse(store.load().hasSingleVideo(VideoId("solo")))
    }

    @Test
    fun `removing one single video leaves the others alone`() {
        store.addSingleVideo(VideoRef(VideoId("keep"), ChannelId(""), "Keep", null, 10L, null))
        store.addSingleVideo(VideoRef(VideoId("drop"), ChannelId(""), "Drop", null, 10L, null))

        store.removeSingleVideo(VideoId("drop"))

        assertTrue(store.load().hasSingleVideo(VideoId("keep")))
    }

    @Test
    fun `removing a video drops any block on it`() {
        // Otherwise the block outlives the approval and silently re-applies if the same
        // video is ever approved again.
        store.addSingleVideo(VideoRef(VideoId("solo"), ChannelId(""), "Solo", null, 10L, null))
        store.block(VideoId("solo"))

        store.removeSingleVideo(VideoId("solo"))

        assertFalse(store.load().isBlocked(VideoId("solo")))
    }

    @Test
    fun `removing a video that was never approved changes nothing`() {
        store.addSingleVideo(VideoRef(VideoId("solo"), ChannelId(""), "Solo", null, 10L, null))

        store.removeSingleVideo(VideoId("never-there"))

        assertTrue(store.load().hasSingleVideo(VideoId("solo")))
    }
}
