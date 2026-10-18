package com.noam.kidtube.core

import kotlinx.serialization.Serializable

/**
 * Keys are the raw id strings rather than value classes, so the on-disk JSON stays a
 * plain object and remains readable by a human inspecting a backup.
 */
@Serializable
data class Whitelist(
    val channels: Map<String, Channel> = emptyMap(),
    val singleVideos: Map<String, VideoRef> = emptyMap(),
    val blockedVideoIds: Set<String> = emptySet(),
) {
    fun hasChannel(id: ChannelId): Boolean = channels.containsKey(id.value)
    fun hasSingleVideo(id: VideoId): Boolean = singleVideos.containsKey(id.value)
    fun isBlocked(id: VideoId): Boolean = blockedVideoIds.contains(id.value)
}
