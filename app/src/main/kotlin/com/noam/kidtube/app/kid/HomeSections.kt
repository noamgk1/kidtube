package com.noam.kidtube.app.kid

import com.noam.kidtube.core.Channel
import com.noam.kidtube.core.VideoRef
import com.noam.kidtube.core.VideoSort
import com.noam.kidtube.core.VideoSorting
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

/**
 * What the child's home screen is made of: at most one row to pick up from, and then
 * every approved video.
 *
 * It used to carry a row per channel as well, and cap each row. Both are gone. Home is
 * one vertical grid now, because rows of rows asked a child to scroll down through them
 * and sideways inside each one to see everything, and the cap meant the library held
 * videos that home could not reach at all. Browsing by channel still exists, on the
 * screen that is actually about channels.
 */
data class HomeScreenModel(
    /** Most recently watched first. Empty when there is nothing part-watched. */
    val continueWatching: List<VideoRef>,
    /** Everything approved, newest first. */
    val all: List<VideoRef>,
) {
    val isEmpty: Boolean get() = all.isEmpty()
}

/** Builds the child's home screen out of a snapshot. Pure, so every rule is testable. */
object HomeSections {

    /** The resume row is a row, so it stays short enough to scan without scrolling far. */
    const val ROW_LIMIT: Int = 20

    fun build(
        snapshot: LibrarySnapshot,
        resume: List<ResumePoint>,
        sort: VideoSort = VideoSort.NEWEST,
    ): HomeScreenModel {
        if (snapshot.isEmpty) return HomeScreenModel(emptyList(), emptyList())

        // A resume point survives only while its video is still approved: the parent may
        // have blocked it, or removed its channel, since it was watched.
        val byId = snapshot.videos.associateBy { it.id.value }
        val resumable = resume.mapNotNull { byId[it.videoId] }.take(ROW_LIMIT)

        return HomeScreenModel(
            // Deliberately not sorted: this row is "what you were watching", and its
            // order is how recently, which no other order would improve on.
            continueWatching = resumable,
            all = VideoSorting.apply(snapshot.videos, sort),
        )
    }
}
