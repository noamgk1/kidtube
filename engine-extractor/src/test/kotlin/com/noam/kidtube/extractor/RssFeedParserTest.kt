package com.noam.kidtube.extractor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RssFeedParserTest {

    private val feed = """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns:yt="http://www.youtube.com/xml/schemas/2015"
              xmlns:media="http://search.yahoo.com/mrss/"
              xmlns="http://www.w3.org/2005/Atom">
          <title>Super Simple Songs</title>
          <entry>
            <id>yt:video:KKNHyhl6aXU</id>
            <yt:videoId>KKNHyhl6aXU</yt:videoId>
            <yt:channelId>UCLsooMJoIpl_7ux2jvdPB-Q</yt:channelId>
            <title>Top 20 Halloween Songs</title>
            <published>2026-10-15T12:00:00+00:00</published>
            <media:group>
              <media:thumbnail url="https://i.ytimg.com/vi/KKNHyhl6aXU/hqdefault.jpg"/>
            </media:group>
          </entry>
          <entry>
            <id>yt:video:Ne-ZcF58px4</id>
            <yt:videoId>Ne-ZcF58px4</yt:videoId>
            <yt:channelId>UCLsooMJoIpl_7ux2jvdPB-Q</yt:channelId>
            <title>Halloween Night</title>
            <published>2026-10-08T09:30:00+00:00</published>
          </entry>
        </feed>
    """.trimIndent()

    @Test
    fun `every entry becomes a video reference`() {
        val videos = RssFeedParser.parse(feed)
        assertEquals(listOf("KKNHyhl6aXU", "Ne-ZcF58px4"), videos.map { it.id.value })
        assertEquals("Top 20 Halloween Songs", videos[0].title)
        assertEquals("UCLsooMJoIpl_7ux2jvdPB-Q", videos[0].channelId.value)
    }

    @Test
    fun `the publish timestamp becomes epoch seconds`() {
        // 2026-10-15T12:00:00Z
        assertEquals(1_792_065_600L, RssFeedParser.parse(feed)[0].publishedAtEpochSeconds)
        // 2026-10-08T09:30:00Z, to prove the offset is read rather than assumed
        assertEquals(1_791_451_800L, RssFeedParser.parse(feed)[1].publishedAtEpochSeconds)
    }

    @Test
    fun `a thumbnail is picked up when present and null when absent`() {
        val videos = RssFeedParser.parse(feed)
        assertEquals("https://i.ytimg.com/vi/KKNHyhl6aXU/hqdefault.jpg", videos[0].thumbnailUrl)
        assertNull(videos[1].thumbnailUrl)
    }

    @Test
    fun `duration is null because the feed does not carry it`() {
        assertNull(
            RssFeedParser.parse(feed)[0].durationSeconds,
            "the Atom feed has no duration field; it must be reported unknown, not zero",
        )
    }

    @Test
    fun `an empty feed yields nothing rather than failing`() {
        val empty = """<?xml version="1.0"?><feed xmlns="http://www.w3.org/2005/Atom"></feed>"""
        assertEquals(emptyList(), RssFeedParser.parse(empty))
    }

    @Test
    fun `malformed xml yields nothing rather than throwing`() {
        assertEquals(emptyList(), RssFeedParser.parse("<feed><entry>"))
    }

    @Test
    fun `an entry missing its video id is skipped rather than poisoning the batch`() {
        val broken = """
            <feed xmlns:yt="http://www.youtube.com/xml/schemas/2015" xmlns="http://www.w3.org/2005/Atom">
              <entry><title>no id here</title></entry>
              <entry>
                <yt:videoId>KKNHyhl6aXU</yt:videoId>
                <yt:channelId>UC_abc</yt:channelId>
                <title>fine</title>
                <published>2026-10-15T12:00:00+00:00</published>
              </entry>
            </feed>
        """.trimIndent()
        assertEquals(listOf("KKNHyhl6aXU"), RssFeedParser.parse(broken).map { it.id.value })
    }

    @Test
    fun `an unparseable published date leaves the timestamp null without dropping the entry`() {
        val odd = """
            <feed xmlns:yt="http://www.youtube.com/xml/schemas/2015" xmlns="http://www.w3.org/2005/Atom">
              <entry>
                <yt:videoId>KKNHyhl6aXU</yt:videoId>
                <yt:channelId>UC_abc</yt:channelId>
                <title>t</title>
                <published>not-a-date</published>
              </entry>
            </feed>
        """.trimIndent()
        val videos = RssFeedParser.parse(odd)
        assertTrue(videos.size == 1)
        assertNull(videos[0].publishedAtEpochSeconds)
    }

    @Test
    fun `an entirely empty string yields nothing`() {
        assertEquals(emptyList(), RssFeedParser.parse(""))
    }
}
