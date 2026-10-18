package com.noam.kidtube.extractor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamChooserTest {

    private fun video(height: Int, muxed: Boolean = false) =
        VideoCandidate(
            url = "https://v/" + height + (if (muxed) "m" else ""),
            height = height,
            isMuxed = muxed,
            mimeType = "video/mp4",
        )

    private fun audio(locale: String?, original: Boolean = false, bitrate: Int = 128) =
        AudioCandidate(
            url = "https://a/" + (locale ?: "none") + "-" + bitrate,
            localeTag = locale,
            isOriginal = original,
            averageBitrate = bitrate,
            mimeType = "audio/mp4",
        )

    @Test
    fun `the highest video at or under the cap wins`() {
        val chosen = StreamChooser.choose(
            videos = listOf(video(2160), video(1080), video(480)),
            audios = listOf(audio("en", original = true)),
            maxHeight = 1080,
            preferredLocaleTag = "he",
        )
        assertEquals(1080, assertNotNull(chosen).video.height)
    }

    @Test
    fun `when everything exceeds the cap the smallest is taken rather than nothing`() {
        val chosen = StreamChooser.choose(
            videos = listOf(video(2160), video(1440)),
            audios = listOf(audio("en", original = true)),
            maxHeight = 720,
            preferredLocaleTag = "he",
        )
        assertEquals(1440, assertNotNull(chosen).video.height)
    }

    // The Made-for-Kids shape measured on 2026-10-17: one muxed 360p stream, no audio list.
    @Test
    fun `a lone muxed stream with no audio tracks is usable on its own`() {
        val chosen = assertNotNull(
            StreamChooser.choose(
                videos = listOf(video(360, muxed = true)),
                audios = emptyList(),
                maxHeight = 1080,
                preferredLocaleTag = "he",
            ),
        )
        assertEquals(360, chosen.video.height)
        assertTrue(chosen.video.isMuxed)
        assertNull(chosen.audio, "a muxed stream already carries its audio")
    }

    @Test
    fun `an adaptive video stream is never chosen without an audio track`() {
        assertNull(
            StreamChooser.choose(
                videos = listOf(video(1080, muxed = false)),
                audios = emptyList(),
                maxHeight = 1080,
                preferredLocaleTag = "he",
            ),
            "video-only with no audio would play silently; prefer reporting no choice",
        )
    }

    @Test
    fun `a muxed stream is preferred over a video-only one when no audio exists`() {
        val chosen = assertNotNull(
            StreamChooser.choose(
                videos = listOf(video(1080, muxed = false), video(360, muxed = true)),
                audios = emptyList(),
                maxHeight = 1080,
                preferredLocaleTag = "he",
            ),
        )
        assertTrue(chosen.video.isMuxed)
    }

    @Test
    fun `audio matching the device locale is chosen`() {
        val chosen = assertNotNull(
            StreamChooser.choose(
                videos = listOf(video(1080)),
                audios = listOf(audio("en", original = true), audio("he"), audio("hi")),
                maxHeight = 1080,
                preferredLocaleTag = "he",
            ),
        )
        assertEquals("he", assertNotNull(chosen.audio).localeTag)
    }

    @Test
    fun `a locale is matched on its language even when the tag carries a region`() {
        val chosen = assertNotNull(
            StreamChooser.choose(
                videos = listOf(video(1080)),
                audios = listOf(audio("en-US", original = true), audio("he-IL")),
                maxHeight = 1080,
                preferredLocaleTag = "he",
            ),
        )
        assertEquals("he-IL", assertNotNull(chosen.audio).localeTag)
    }

    @Test
    fun `the original track is the fallback when the locale is absent`() {
        val chosen = assertNotNull(
            StreamChooser.choose(
                videos = listOf(video(1080)),
                audios = listOf(audio("hi"), audio("en", original = true), audio("ar")),
                maxHeight = 1080,
                preferredLocaleTag = "he",
            ),
        )
        assertEquals("en", assertNotNull(chosen.audio).localeTag)
    }

    @Test
    fun `the highest bitrate is the last resort when nothing is marked original`() {
        val chosen = assertNotNull(
            StreamChooser.choose(
                videos = listOf(video(1080)),
                audios = listOf(audio("hi", bitrate = 70), audio("ar", bitrate = 160)),
                maxHeight = 1080,
                preferredLocaleTag = "he",
            ),
        )
        assertEquals(160, assertNotNull(chosen.audio).averageBitrate)
    }

    // The Mark Rober case from the design spec: 270 dubbed audio tracks.
    @Test
    fun `with hundreds of dubbed tracks the locale still decides, not list order`() {
        val many = buildList {
            add(audio("hi", bitrate = 128))
            addAll((1..268).map { audio("lang" + it, bitrate = 128) })
            add(audio("he", bitrate = 128))
        }
        assertEquals(270, many.size)
        val chosen = assertNotNull(
            StreamChooser.choose(listOf(video(1080)), many, 1080, "he"),
        )
        assertEquals("he", assertNotNull(chosen.audio).localeTag)
    }

    @Test
    fun `no video candidates means no choice`() {
        assertNull(StreamChooser.choose(emptyList(), listOf(audio("he")), 1080, "he"))
    }

    @Test
    fun `a null locale tag never matches the preferred language`() {
        val chosen = assertNotNull(
            StreamChooser.choose(
                videos = listOf(video(1080)),
                audios = listOf(audio(null, bitrate = 200), audio("he", bitrate = 64)),
                maxHeight = 1080,
                preferredLocaleTag = "he",
            ),
        )
        assertEquals("he", assertNotNull(chosen.audio).localeTag)
    }
}
