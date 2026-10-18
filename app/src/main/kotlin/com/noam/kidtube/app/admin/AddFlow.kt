package com.noam.kidtube.app.admin

import com.noam.kidtube.app.sync.CatalogSync
import com.noam.kidtube.core.ChannelId
import com.noam.kidtube.core.VideoId
import com.noam.kidtube.core.VideoRef
import com.noam.kidtube.core.store.WhitelistStore
import com.noam.kidtube.engine.ChannelUrlParser
import com.noam.kidtube.engine.ContentSource
import com.noam.kidtube.engine.PastedLink
import com.noam.kidtube.engine.UnsupportedReason

sealed interface AddPreview {
    data class Channel(
        val channel: com.noam.kidtube.core.Channel,
        val alreadyApproved: Boolean,
    ) : AddPreview

    data class SingleVideo(val videoId: VideoId, val canonicalUrl: String) : AddPreview

    data class Rejected(val reason: UnsupportedReason, val detail: String) : AddPreview
}

/**
 * Paste, preview, confirm. The preview exists because resolution can quietly land on the
 * wrong channel: a mistyped handle is still a valid handle, and the probe of 2026-10-17
 * showed the legacy /c/ form answering 404 outright.
 */
class AddFlow(
    private val contentSource: ContentSource,
    private val whitelistStore: WhitelistStore,
    private val catalogSync: CatalogSync,
) {

    suspend fun preview(pasted: String): AddPreview {
        val link = ChannelUrlParser.parse(pasted)

        val channelUrl = when (link) {
            is PastedLink.ChannelById -> link.canonicalUrl
            is PastedLink.ChannelByHandle -> link.canonicalUrl
            is PastedLink.ChannelByLegacyPath -> link.canonicalUrl
            is PastedLink.SingleVideo ->
                return AddPreview.SingleVideo(link.id, link.canonicalUrl)
            is PastedLink.Unsupported ->
                return AddPreview.Rejected(link.reason, link.detail)
        }

        return contentSource.resolveChannel(channelUrl).fold(
            onSuccess = { resolved ->
                AddPreview.Channel(
                    channel = resolved,
                    alreadyApproved = whitelistStore.load().hasChannel(resolved.id),
                )
            },
            onFailure = { cause ->
                AddPreview.Rejected(
                    UnsupportedReason.UNRECOGNISED,
                    cause.message ?: ("could not resolve " + channelUrl),
                )
            },
        )
    }

    /** Returns false when the channel was already approved. Kicks off the deep sync. */
    suspend fun confirmChannel(channel: com.noam.kidtube.core.Channel): Boolean {
        if (!whitelistStore.addChannel(channel)) return false
        catalogSync.deepSync(channel.id)
        return true
    }

    fun confirmSingleVideo(videoId: VideoId, title: String): Boolean =
        whitelistStore.addSingleVideo(
            VideoRef(
                id = videoId,
                // A video approved on its own belongs to no approved channel, and the
                // blank id is what keeps it out of the per-channel catalogs.
                channelId = ChannelId(""),
                title = title,
                thumbnailUrl = "https://i.ytimg.com/vi/" + videoId.value + "/hqdefault.jpg",
                durationSeconds = null,
                publishedAtEpochSeconds = null,
            ),
        )
}
