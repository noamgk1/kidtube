package com.noam.kidtube.app.kid

import com.noam.kidtube.core.Channel
import com.noam.kidtube.core.ChannelId
import com.noam.kidtube.core.VideoId
import com.noam.kidtube.core.VideoRef
import com.noam.kidtube.core.store.ResumePoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the child's home screen is made of. Pure: it takes an already-filtered library
 * snapshot, so a blocked or unapproved video cannot reach it by construction, and every
 * ordering rule is checkable without a device.
 */
class HomeSectionsTest {

    private val kids = ChannelId("UC_kids")
    private val science = ChannelId("UC_sci")

    private fun video(channel: ChannelId, id: String, published: Long) =
        VideoRef(VideoId(id), channel, "t " + id, null, 120L, published)

    private val snapshot = LibrarySnapshot(
        channels = listOf(
            Channel(kids, "Kids", null, "f1", 1L),
            Channel(science, "Science", null, "f2", 1L),
        ),
        videos = listOf(
            video(kids, "k1", 900),
            video(science, "s1", 800),
            video(kids, "k2", 700),
            video(science, "s2", 600),
        ),
    )

    private fun resume(vararg ids: String) =
        ids.mapIndexed { index, id ->
            ResumePoint(id, 60_000, 600_000, updatedAtEpochSeconds = 1_000L - index)
        }

    @Test
    fun `an empty library has nothing on it`() {
        val model = HomeSections.build(LibrarySnapshot(emptyList(), emptyList()), emptyList())

        assertTrue(model.isEmpty)
        assertTrue(model.all.isEmpty())
        assertTrue(model.continueWatching.isEmpty())
    }

    @Test
    fun `the grid holds the whole library, newest first`() {
        val model = HomeSections.build(snapshot, emptyList())

        assertEquals(listOf("k1", "s1", "k2", "s2"), model.all.map { it.id.value })
    }

    @Test
    fun `the whole library is reachable, however large it is`() {
        // Home used to cap each row, which meant a library of a hundred videos had
        // eighty the child could not get to from the home screen at all.
        val many = (1..100).map { video(kids, "v" + it, it.toLong()) }
        val big = LibrarySnapshot(listOf(Channel(kids, "Kids", null, "f", 1L)), many)

        assertEquals(100, HomeSections.build(big, emptyList()).all.size)
    }

    @Test
    fun `continue watching holds what was part-watched`() {
        val model = HomeSections.build(snapshot, resume("k2"))

        assertEquals(listOf("k2"), model.continueWatching.map { it.id.value })
    }

    @Test
    fun `continue watching is empty when there is nothing to resume`() {
        assertTrue(HomeSections.build(snapshot, emptyList()).continueWatching.isEmpty())
    }

    @Test
    fun `a resume point for a video no longer approved is dropped`() {
        // The parent removed the channel, or blocked the video, after it was watched.
        val model = HomeSections.build(snapshot, resume("gone-from-library"))

        assertTrue(model.continueWatching.isEmpty())
    }

    @Test
    fun `continue watching keeps the order the resume list gave it`() {
        val model = HomeSections.build(snapshot, resume("s2", "k1"))

        assertEquals(listOf("s2", "k1"), model.continueWatching.map { it.id.value })
    }

    @Test
    fun `the resume row stays short enough to scan`() {
        val many = (1..100).map { video(kids, "v" + it, it.toLong()) }
        val big = LibrarySnapshot(listOf(Channel(kids, "Kids", null, "f", 1L)), many)
        val everything = many.map { it.id.value }.toTypedArray()

        val model = HomeSections.build(big, resume(*everything))

        assertEquals(HomeSections.ROW_LIMIT, model.continueWatching.size)
    }

    @Test
    fun `individually approved videos sit in the grid like any other`() {
        val loose = video(ChannelId(""), "single1", 950)
        val withSingle = snapshot.copy(videos = listOf(loose) + snapshot.videos)

        assertEquals("single1", HomeSections.build(withSingle, emptyList()).all.first().id.value)
    }
}
