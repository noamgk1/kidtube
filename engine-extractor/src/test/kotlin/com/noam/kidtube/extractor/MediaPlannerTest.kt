package com.noam.kidtube.extractor

import com.noam.kidtube.core.PlaybackSource
import com.noam.kidtube.core.VideoId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class MediaPlannerTest {

    @Test
    fun `a muxed source becomes a single media item`() {
        val plan = MediaPlanner.plan(
            PlaybackSource.Streams("https://v/360", audioUrl = null, mimeType = "video/mp4"),
        )
        val single = assertIs<MediaPlan.Single>(plan)
        assertEquals("https://v/360", single.url)
        assertEquals("video/mp4", single.mimeType)
    }

    @Test
    fun `an adaptive source becomes a merged plan`() {
        val plan = MediaPlanner.plan(
            PlaybackSource.Streams("https://v/1080", "https://a/160", "video/webm"),
        )
        val merged = assertIs<MediaPlan.Merged>(plan)
        assertEquals("https://v/1080", merged.videoUrl)
        assertEquals("https://a/160", merged.audioUrl)
        assertEquals("video/webm", merged.mimeType)
    }

    @Test
    fun `a blank video url yields no plan rather than a broken player`() {
        assertNull(MediaPlanner.plan(PlaybackSource.Streams("", null, "video/mp4")))
    }

    @Test
    fun `a blank audio url degrades to a single plan instead of merging nothing`() {
        assertIs<MediaPlan.Single>(
            MediaPlanner.plan(PlaybackSource.Streams("https://v/360", "   ", "video/mp4")),
        )
    }

    @Test
    fun `an embedded source has no plan because this engine does not render embeds`() {
        assertNull(MediaPlanner.plan(PlaybackSource.Embedded(VideoId("abc"))))
    }
}
