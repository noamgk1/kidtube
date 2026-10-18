package com.noam.kidtube.core

import kotlinx.serialization.Serializable

@Serializable
data class Channel(
    val id: ChannelId,
    val title: String,
    val avatarUrl: String?,
    /** Captured once at add time so the daily refresh never needs the extractor. */
    val feedUrl: String,
    val subscriberCount: Long,
)

@Serializable
data class VideoRef(
    val id: VideoId,
    val channelId: ChannelId,
    val title: String,
    val thumbnailUrl: String?,
    /** Null when unknown: the RSS fast path carries no duration. */
    val durationSeconds: Long?,
    val publishedAtEpochSeconds: Long?,
)

/** Known only after extraction. Carries the two facts Policy cannot learn from a listing. */
data class VideoDetails(
    val id: VideoId,
    val title: String,
    val durationSeconds: Long,
    val ageLimit: Int,
    val isLive: Boolean,
    val source: PlaybackSource,
)

/** Engine-specific playback payload: the extractor engine yields streams, an embed engine a bare id. */
sealed interface PlaybackSource {
    data class Streams(
        val videoUrl: String,
        val audioUrl: String?,
        val mimeType: String,
    ) : PlaybackSource

    data class Embedded(val videoId: VideoId) : PlaybackSource
}
