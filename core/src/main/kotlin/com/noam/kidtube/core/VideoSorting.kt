package com.noam.kidtube.core

/** How the child's home screen is ordered. */
enum class VideoSort { NEWEST, OLDEST, SHORTEST, LONGEST }

/**
 * Orders what the child sees.
 *
 * The one rule worth stating is about missing values, because they are not rare: the RSS
 * fast path carries no duration, so every day's newest arrivals have none until a deep
 * sync fills them in, and an individually approved video may have neither date nor
 * duration. Anything the sort cannot place goes last rather than being treated as zero -
 * otherwise the videos nothing is known about would lead "shortest" and "oldest", which
 * is the opposite of what was asked for. It is never dropped.
 */
object VideoSorting {

    fun apply(videos: List<VideoRef>, sort: VideoSort): List<VideoRef> = when (sort) {
        VideoSort.NEWEST -> videos.sortedWith(byDate(newestFirst = true))
        VideoSort.OLDEST -> videos.sortedWith(byDate(newestFirst = false))
        VideoSort.SHORTEST -> videos.sortedWith(byDuration(longestFirst = false))
        VideoSort.LONGEST -> videos.sortedWith(byDuration(longestFirst = true))
    }

    private fun byDate(newestFirst: Boolean): Comparator<VideoRef> =
        // The first clause is what keeps the undated at the end in both directions.
        compareByDescending<VideoRef> { it.publishedAtEpochSeconds != null }
            .then(
                if (newestFirst) {
                    compareByDescending { it.publishedAtEpochSeconds ?: 0L }
                } else {
                    compareBy { it.publishedAtEpochSeconds ?: 0L }
                },
            )

    private fun byDuration(longestFirst: Boolean): Comparator<VideoRef> =
        compareByDescending<VideoRef> { it.durationSeconds != null }
            .then(
                if (longestFirst) {
                    compareByDescending { it.durationSeconds ?: 0L }
                } else {
                    compareBy { it.durationSeconds ?: 0L }
                },
            )
}
