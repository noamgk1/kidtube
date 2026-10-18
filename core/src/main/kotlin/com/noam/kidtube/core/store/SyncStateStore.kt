package com.noam.kidtube.core.store

import com.noam.kidtube.core.ChannelId
import com.noam.kidtube.core.SyncState

private const val DIR = "sync"

class SyncStateStore(private val files: FileStore) {

    fun load(channelId: ChannelId): SyncState =
        files.read(path(channelId), SyncState.serializer()) { SyncState(channelId.value) }

    fun save(state: SyncState) {
        files.write(path(ChannelId(state.channelId)), SyncState.serializer(), state)
    }

    fun all(): List<SyncState> =
        files.listNames(DIR)
            .filter { it.endsWith(".json") }
            .map { load(ChannelId(it.removeSuffix(".json"))) }

    private fun path(channelId: ChannelId): String {
        require(channelId.value.isNotBlank()) { "blank channel id" }
        // Char(92) is the backslash, written this way to keep the source free of
        // escape sequences that shell heredocs mangle.
        require(channelId.value.none { it == '/' || it == Char(92) }) {
            "channel id must not contain a path separator: " + channelId.value
        }
        return DIR + "/" + channelId.value + ".json"
    }
}
