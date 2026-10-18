package com.noam.kidtube.core

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WhitelistTest {

    private val channel = Channel(
        id = ChannelId("UC_abc"),
        title = "Super Simple Songs",
        avatarUrl = "https://example.test/a.jpg",
        feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=UC_abc",
        subscriberCount = 47_100_000,
    )

    private val video = VideoRef(
        id = VideoId("vid1"),
        channelId = ChannelId("UC_abc"),
        title = "Halloween Night",
        thumbnailUrl = "https://example.test/t.jpg",
        durationSeconds = 164L,
        publishedAtEpochSeconds = 1_760_000_000L,
    )

    @Test
    fun `an empty whitelist contains nothing`() {
        val w = Whitelist()
        assertFalse(w.hasChannel(ChannelId("UC_abc")))
        assertFalse(w.hasSingleVideo(VideoId("vid1")))
        assertFalse(w.isBlocked(VideoId("vid1")))
    }

    @Test
    fun `a channel and a single video are recognised`() {
        val w = Whitelist(
            channels = mapOf(channel.id.value to channel),
            singleVideos = mapOf(video.id.value to video),
        )
        assertTrue(w.hasChannel(ChannelId("UC_abc")))
        assertTrue(w.hasSingleVideo(VideoId("vid1")))
    }

    @Test
    fun `the blocklist is global and not scoped to a channel`() {
        val w = Whitelist(blockedVideoIds = setOf("vid1"))
        assertTrue(w.isBlocked(VideoId("vid1")))
        assertFalse(w.isBlocked(VideoId("vid2")))
    }

    @Test
    fun `a whitelist survives a json round trip`() {
        val w = Whitelist(
            channels = mapOf(channel.id.value to channel),
            singleVideos = mapOf(video.id.value to video),
            blockedVideoIds = setOf("vid9"),
        )
        val json = Json.encodeToString(Whitelist.serializer(), w)
        assertEquals(w, Json.decodeFromString(Whitelist.serializer(), json))
    }

    @Test
    fun `a video with no known duration round trips as null`() {
        val undated = video.copy(durationSeconds = null, publishedAtEpochSeconds = null)
        val w = Whitelist(singleVideos = mapOf(undated.id.value to undated))
        val back = Json.decodeFromString(Whitelist.serializer(), Json.encodeToString(Whitelist.serializer(), w))
        assertEquals(null, back.singleVideos.getValue("vid1").durationSeconds)
    }
}
