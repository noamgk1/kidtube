package com.noam.kidtube.app.kid

import com.noam.kidtube.core.Channel
import com.noam.kidtube.core.ChannelId
import com.noam.kidtube.core.VideoRef
import com.noam.kidtube.core.store.CatalogStore
import com.noam.kidtube.core.store.WhitelistStore

data class KidGridModel(
    val channels: List<Channel>,
    val selectedChannelId: ChannelId?,
    val videos: List<VideoRef>,
)

/**
 * Reads from local files only - never the network - so the grid is on screen the instant
 * the app opens, and still works on a day when the engine is broken.
 *
 * The blocklist is applied here as well as in Policy. Belt and braces: Policy is the gate
 * that matters, but a blocked video should not even be visible to tap.
 */
class KidGridLoader(
    private val whitelistStore: WhitelistStore,
    private val catalogStore: CatalogStore,
) {
    fun load(selected: ChannelId?): KidGridModel {
        val snapshot = snapshot()
        val videos = if (selected == null) {
            snapshot.videos
        } else {
            snapshot.videos.filter { it.channelId == selected }
        }
        return KidGridModel(snapshot.channels, selected, videos)
    }

    /**
     * Everything the child may see, already filtered and ordered.
     *
     * Home, search and the channel pages all read from this one snapshot rather than
     * each re-deriving what is allowed, so a blocked video cannot reappear through a
     * screen that forgot to check.
     */
    fun snapshot(): LibrarySnapshot {
        val whitelist = whitelistStore.load()

        val approved = whitelist.channels.values
            .map { channel -> channel to catalogStore.load(channel.id) }
            // A channel with nothing in it yet would be an avatar leading nowhere.
            .filter { (_, videos) -> videos.isNotEmpty() }

        val channels = approved.map { it.first }.sortedBy { it.title }

        val videos = (approved.flatMap { it.second } + whitelist.singleVideos.values)
            .filterNot { whitelist.isBlocked(it.id) }
            .distinctBy { it.id.value }
            .sortedWith(
                compareByDescending<VideoRef> { it.publishedAtEpochSeconds != null }
                    .thenByDescending { it.publishedAtEpochSeconds ?: 0L },
            )

        return LibrarySnapshot(channels, videos)
    }
}
