package com.noam.kidtube.core

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * How the child's home screen is ordered.
 *
 * The awkward cases are the missing values, and they are not rare: the RSS fast path
 * carries no duration, so the newest arrivals have none until a deep sync fills them in,
 * and an individually approved video may have neither date nor duration. A video with
 * nothing to sort by must still be reachable, so it goes last in every order rather than
 * being treated as zero and jumping to the front of "shortest".
 */
class VideoSortingTest {

    private val channel = ChannelId("UC_kids")

    private fun video(id: String, published: Long?, duration: Long?) =
        VideoRef(VideoId(id), channel, id, null, duration, published)

    private val videos = listOf(
        video("old-long", published = 100, duration = 600),
        video("new-short", published = 900, duration = 60),
        video("mid", published = 500, duration = 300),
    )

    private fun order(sort: VideoSort, of: List<VideoRef> = videos) =
        VideoSorting.apply(of, sort).map { it.id.value }

    @Test
    fun `newest first`() {
        assertEquals(listOf("new-short", "mid", "old-long"), order(VideoSort.NEWEST))
    }

    @Test
    fun `oldest first`() {
        assertEquals(listOf("old-long", "mid", "new-short"), order(VideoSort.OLDEST))
    }

    @Test
    fun `shortest first`() {
        assertEquals(listOf("new-short", "mid", "old-long"), order(VideoSort.SHORTEST))
    }

    @Test
    fun `longest first`() {
        assertEquals(listOf("old-long", "mid", "new-short"), order(VideoSort.LONGEST))
    }

    @Test
    fun `a video with no date sorts last whichever way the dates run`() {
        val withUndated = videos + video("undated", published = null, duration = 120)

        assertEquals("undated", order(VideoSort.NEWEST, withUndated).last())
        assertEquals("undated", order(VideoSort.OLDEST, withUndated).last())
    }

    @Test
    fun `a video with no duration sorts last whichever way the durations run`() {
        // The RSS fast path stores none, so this is the newest arrivals every day.
        val withUnknown = videos + video("unknown", published = 700, duration = null)

        assertEquals("unknown", order(VideoSort.SHORTEST, withUnknown).last())
        assertEquals("unknown", order(VideoSort.LONGEST, withUnknown).last())
    }

    @Test
    fun `nothing is ever dropped`() {
        val messy = videos + video("nothing", published = null, duration = null)

        VideoSort.entries.forEach { sort ->
            assertEquals(
                messy.size,
                VideoSorting.apply(messy, sort).size,
                "a video the sort cannot place must still be reachable: " + sort,
            )
        }
    }

    @Test
    fun `an empty library sorts to nothing rather than throwing`() {
        VideoSort.entries.forEach { sort ->
            assertEquals(emptyList(), VideoSorting.apply(emptyList(), sort))
        }
    }
}
