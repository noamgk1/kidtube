package com.noam.kidtube.app.kid

import com.noam.kidtube.core.Channel
import com.noam.kidtube.core.VideoRef
import com.noam.kidtube.core.store.ResumePoint

/**
 * Everything the child is allowed to see, already filtered.
 *
 * Blocked and unapproved videos are removed before a snapshot is built, so nothing
 * downstream — home, search, a channel page — can surface one by forgetting a check.
 */
data class LibrarySnapshot(
    val channels: List<Channel>,
    val videos: List<VideoRef>,
) {
    fun forChannel(channel: Channel): List<VideoRef> =
        videos.filter { it.channelId == channel.id }

    val isEmpty: Boolean get() = videos.isEmpty()
}

enum class SectionKind { CONTINUE_WATCHING, NEWEST, CHANNEL }

data class HomeSection(
    val kind: SectionKind,
    val videos: List<VideoRef>,
    /** Set only for [SectionKind.CHANNEL]. */
    val channel: Channel? = null,
)

/** Builds the child's home screen out of a snapshot. Pure, so every rule is testable. */
object HomeSections {

    /** A home screen is a starting point, not the whole library. */
    const val ROW_LIMIT: Int = 20

    fun build(snapshot: LibrarySnapshot, resume: List<ResumePoint>): List<HomeSection> {
        if (snapshot.isEmpty) return emptyList()

        val sections = mutableListOf<HomeSection>()

        // A resume point survives only while its video is still approved: the parent may
        // have blocked it, or removed its channel, since it was watched.
        val byId = snapshot.videos.associateBy { it.id.value }
        val resumable = resume.mapNotNull { byId[it.videoId] }.take(ROW_LIMIT)
        if (resumable.isNotEmpty()) {
            sections += HomeSection(SectionKind.CONTINUE_WATCHING, resumable)
        }

        sections += HomeSection(SectionKind.NEWEST, snapshot.videos.take(ROW_LIMIT))

        // With a single approved channel its row would repeat the newest row exactly,
        // and the child would see every tile twice. Grouping by channel only says
        // something once there is more than one channel to tell apart.
        if (snapshot.channels.size > 1) {
            snapshot.channels.forEach { channel ->
                val videos = snapshot.forChannel(channel).take(ROW_LIMIT)
                if (videos.isNotEmpty()) {
                    sections += HomeSection(SectionKind.CHANNEL, videos, channel)
                }
            }
        }

        return sections
    }
}
