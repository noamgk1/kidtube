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
    fun `an empty library has no sections at all`() {
        assertTrue(HomeSections.build(LibrarySnapshot(emptyList(), emptyList()), emptyList()).isEmpty())
    }

    @Test
    fun `the newest row comes first and is ordered newest first`() {
        val sections = HomeSections.build(snapshot, emptyList())
        assertEquals(SectionKind.NEWEST, sections.first().kind)
        assertEquals(listOf("k1", "s1", "k2", "s2"), sections.first().videos.map { it.id.value })
    }

    @Test
    fun `continue watching comes before everything when there is something to resume`() {
        val sections = HomeSections.build(snapshot, resume("k2"))
        assertEquals(SectionKind.CONTINUE_WATCHING, sections.first().kind)
        assertEquals(listOf("k2"), sections.first().videos.map { it.id.value })
    }

    @Test
    fun `continue watching is omitted entirely when there is nothing to resume`() {
        assertTrue(HomeSections.build(snapshot, emptyList()).none { it.kind == SectionKind.CONTINUE_WATCHING })
    }

    @Test
    fun `a resume point for a video no longer approved is dropped`() {
        // The parent removed the channel, or blocked the video, after it was watched.
        val sections = HomeSections.build(snapshot, resume("gone-from-library"))
        assertTrue(sections.none { it.kind == SectionKind.CONTINUE_WATCHING })
    }

    @Test
    fun `continue watching keeps the order the resume list gave it`() {
        val sections = HomeSections.build(snapshot, resume("s2", "k1"))
        assertEquals(listOf("s2", "k1"), sections.first().videos.map { it.id.value })
    }

    @Test
    fun `every channel with videos gets its own row`() {
        val sections = HomeSections.build(snapshot, emptyList())
        val channelRows = sections.filter { it.kind == SectionKind.CHANNEL }
        assertEquals(listOf("Kids", "Science"), channelRows.map { it.channel?.title })
    }

    @Test
    fun `a channel row holds only that channel's videos, newest first`() {
        val row = HomeSections.build(snapshot, emptyList())
            .first { it.kind == SectionKind.CHANNEL && it.channel?.id == kids }
        assertEquals(listOf("k1", "k2"), row.videos.map { it.id.value })
    }

    @Test
    fun `a channel with nothing in it produces no row`() {
        val withEmpty = snapshot.copy(
            channels = snapshot.channels + Channel(ChannelId("UC_empty"), "Empty", null, "f", 1L),
        )
        val rows = HomeSections.build(withEmpty, emptyList()).filter { it.kind == SectionKind.CHANNEL }
        assertEquals(2, rows.size)
    }

    @Test
    fun `individually approved videos appear in the newest row but make no channel row`() {
        val loose = video(ChannelId(""), "single1", 950)
        val withSingle = snapshot.copy(videos = listOf(loose) + snapshot.videos)
        val sections = HomeSections.build(withSingle, emptyList())

        assertEquals("single1", sections.first { it.kind == SectionKind.NEWEST }.videos.first().id.value)
        assertEquals(2, sections.count { it.kind == SectionKind.CHANNEL })
    }

    @Test
    fun `rows are capped so home stays a home rather than the whole library`() {
        val many = (1..100).map { video(kids, "v" + it, it.toLong()) }
        val big = LibrarySnapshot(listOf(Channel(kids, "Kids", null, "f", 1L)), many)
        val sections = HomeSections.build(big, emptyList())
        assertTrue(sections.all { it.videos.size <= HomeSections.ROW_LIMIT })
    }
}
