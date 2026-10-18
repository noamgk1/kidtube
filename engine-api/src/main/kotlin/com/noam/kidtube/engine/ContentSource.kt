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

interface ContentSource {
    suspend fun resolveChannel(url: String): Result<Channel>

    /** One page of a channel's uploads. Pass null for the first page. */
    suspend fun listVideos(channelId: ChannelId, cursor: Cursor?): Result<VideoPage>

    /** The cheap daily path: one request against the channel's RSS feed. */
    suspend fun latestVideos(channelId: ChannelId, feedUrl: String): Result<List<VideoRef>>
}
