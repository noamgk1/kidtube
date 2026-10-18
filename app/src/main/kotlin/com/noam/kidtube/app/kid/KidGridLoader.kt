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
        val whitelist = whitelistStore.load()

        val approved = whitelist.channels.values
            .map { channel -> channel to catalogStore.load(channel.id) }
            // A channel with nothing in it yet would be an avatar leading nowhere.
            .filter { (_, videos) -> videos.isNotEmpty() }

        val channels = approved.map { it.first }.sortedBy { it.title }

        val fromChannels = approved
            .filter { (channel, _) -> selected == null || channel.id == selected }
            .flatMap { it.second }

        val singles = if (selected == null) whitelist.singleVideos.values else emptyList()

        val videos = (fromChannels + singles)
            .filterNot { whitelist.isBlocked(it.id) }
            .distinctBy { it.id.value }
            .sortedWith(
                compareByDescending<VideoRef> { it.publishedAtEpochSeconds != null }
                    .thenByDescending { it.publishedAtEpochSeconds ?: 0L },
            )

        return KidGridModel(channels, selected, videos)
    }
}
