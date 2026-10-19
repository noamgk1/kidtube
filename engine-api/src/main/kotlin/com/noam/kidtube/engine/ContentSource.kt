package com.noam.kidtube.engine

import com.noam.kidtube.core.Channel
import com.noam.kidtube.core.ChannelId
import com.noam.kidtube.core.VideoRef

/**
 * An opaque paging token. Opaque on purpose: the extractor engine's real cursor is a
 * NewPipe Page object, which must not leak past that module.
 */
@JvmInline
value class Cursor(val token: String)

data class VideoPage(val videos: List<VideoRef>, val next: Cursor?)

/** Why a pasted URL could not become an approved channel. Each case gets its own message. */
sealed class ChannelResolutionError(message: String) : Exception(message) {
    class NotAChannelUrl(val url: String, val detail: String) :
        ChannelResolutionError("not a channel URL: " + url + " (" + detail + ")")

    class ChannelNotFound(val url: String) :
        ChannelResolutionError("no channel at: " + url)

    class Unreachable(val url: String, cause: Throwable) :
        ChannelResolutionError("could not reach: " + url + " (" + cause.message + ")")
}

/**
 * One thing a parent could approve out of a search.
 *
 * Search exists on the parent's side only. The child's search filters the approved
 * library and never leaves the device; this one really does query YouTube, which is
 * exactly why it is behind the password. Nothing here is approved by being found - the
 * parent ticks what they want, and only that reaches the whitelist.
 */
sealed interface SearchResult {
    data class ChannelHit(val channel: Channel) : SearchResult

    /** A single video, with the channel it came from so the parent can judge it. */
    data class VideoHit(val video: VideoRef, val channelTitle: String) : SearchResult
}

data class SearchPage(val results: List<SearchResult>, val next: Cursor?)

/**
 * What a search should come back with.
 *
 * YouTube's unfiltered search is overwhelmingly videos, so a parent looking for a
 * channel to approve had to hunt for the one channel buried among them. Asking for one
 * kind is the difference between finding a channel and giving up on it.
 */
enum class SearchKind { EVERYTHING, CHANNELS, VIDEOS }

interface ContentSource {
    suspend fun resolveChannel(url: String): Result<Channel>

    /** One page of a channel's uploads. Pass null for the first page. */
    suspend fun listVideos(channelId: ChannelId, cursor: Cursor?): Result<VideoPage>

    /** The cheap daily path: one request against the channel's RSS feed. */
    suspend fun latestVideos(channelId: ChannelId, feedUrl: String): Result<List<VideoRef>>

    /** Searches YouTube on the parent's behalf. Pass null for the first page. */
    suspend fun search(
        query: String,
        cursor: Cursor?,
        kind: SearchKind = SearchKind.EVERYTHING,
    ): Result<SearchPage>
}
