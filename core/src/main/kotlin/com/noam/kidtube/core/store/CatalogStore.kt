package com.noam.kidtube.core.store

import com.noam.kidtube.core.ChannelId
import com.noam.kidtube.core.VideoRef
import kotlinx.serialization.builtins.ListSerializer

private const val DIR = "catalog"

class CatalogStore(private val files: FileStore) {

    fun load(channelId: ChannelId): List<VideoRef> =
        files.read(path(channelId), ListSerializer(VideoRef.serializer())) { emptyList() }

    fun save(channelId: ChannelId, videos: List<VideoRef>) {
        files.write(path(channelId), ListSerializer(VideoRef.serializer()), videos)
    }

    /**
     * Adds only ids not already stored and returns how many were new, so the daily RSS
     * refresh is cheap and idempotent — and so a deep sync interrupted by process death
     * can simply re-page without duplicating anything. Ordering is newest first; undated
     * entries sort last because a missing publish date means we cannot place them.
     */
    fun merge(channelId: ChannelId, incoming: List<VideoRef>): Int {
        val existing = load(channelId)
        val known = existing.mapTo(mutableSetOf()) { it.id.value }
        val fresh = incoming.filter { known.add(it.id.value) }
        if (fresh.isEmpty()) return 0

        val combined = (existing + fresh).sortedWith(
            compareByDescending<VideoRef> { it.publishedAtEpochSeconds != null }
                .thenByDescending { it.publishedAtEpochSeconds ?: 0L },
        )
        save(channelId, combined)
        return fresh.size
    }

    fun delete(channelId: ChannelId) {
        files.delete(path(channelId))
    }

    fun channelIds(): List<ChannelId> =
        files.listNames(DIR)
            .filter { it.endsWith(".json") }
            .map { ChannelId(it.removeSuffix(".json")) }

    private fun path(channelId: ChannelId): String {
        require(channelId.value.isNotBlank()) { "blank channel id" }
        // Char(92) is the backslash, written this way to keep the source free of
        // escape sequences that shell heredocs mangle.
        require(channelId.value.none { it == '/' || it == Char(92) }) {
            "channel id must not contain a path separator: ${channelId.value}"
        }
        require(channelId.value != "." && channelId.value != "..") {
            "channel id must not be a relative path: ${channelId.value}"
        }
        return "$DIR/${channelId.value}.json"
    }
}
