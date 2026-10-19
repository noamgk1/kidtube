package com.noam.kidtube.app.admin

import com.noam.kidtube.app.sync.CatalogSync
import com.noam.kidtube.core.ChannelId
import com.noam.kidtube.core.VideoId
import com.noam.kidtube.core.VideoRef
import com.noam.kidtube.core.store.WhitelistStore
import com.noam.kidtube.engine.ChannelUrlParser
import com.noam.kidtube.engine.ContentSource
import com.noam.kidtube.engine.Cursor
import com.noam.kidtube.engine.PastedLink
import com.noam.kidtube.engine.SearchKind
import com.noam.kidtube.engine.SearchPage
import com.noam.kidtube.engine.SearchResult
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

    /**
     * Searches YouTube for the parent. Behind the password, and approving nothing by
     * itself: the results are a list to choose from.
     */
    suspend fun search(
        query: String,
        cursor: Cursor? = null,
        kind: SearchKind = SearchKind.EVERYTHING,
    ): Result<SearchPage> =
        if (query.isBlank()) {
            Result.success(SearchPage(emptyList(), null))
        } else {
            contentSource.search(query, cursor, kind)
        }

    /**
     * Approves a channel the parent picked out of a search, and returns it so the caller
     * can start its catalog sync somewhere that outlives the screen. Null means it was
     * already approved.
     *
     * Deliberately re-resolves rather than trusting what search returned: that is the
     * same path a pasted link takes, so the feed URL and the title come from the channel
     * itself, and a channel that has vanished since the search fails here instead of
     * being stored as something that can never sync.
     *
     * The sync is not started here on purpose. It runs for minutes on a large channel,
     * and a caller that awaited this to find out whether the approval worked would be
     * waiting for the wrong thing.
     */
    suspend fun approveFromSearch(
        hit: SearchResult.ChannelHit,
    ): Result<com.noam.kidtube.core.Channel?> {
        val url = CHANNEL_URL_PREFIX + hit.channel.id.value
        return contentSource.resolveChannel(url)
            .map { resolved -> if (whitelistStore.addChannel(resolved)) resolved else null }
    }

    /** Approves one video the parent picked, with the real thumbnail search gave us. */
    fun approveFromSearch(hit: SearchResult.VideoHit): Boolean =
        whitelistStore.addSingleVideo(
            // The blank channel id is what marks this as approved on its own: it keeps
            // the video out of the per-channel catalogs, and approving it grants nothing
            // else from the channel it happens to come from.
            hit.video.copy(channelId = ChannelId("")),
        )

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

    private companion object {
        const val CHANNEL_URL_PREFIX = "https://www.youtube.com/channel/"
    }
}
