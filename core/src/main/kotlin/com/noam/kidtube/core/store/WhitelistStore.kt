package com.noam.kidtube.core.store

import com.noam.kidtube.core.Channel
import com.noam.kidtube.core.ChannelId
import com.noam.kidtube.core.VideoId
import com.noam.kidtube.core.VideoRef
import com.noam.kidtube.core.Whitelist

private const val FILE = "whitelist.json"

class WhitelistStore(private val files: FileStore) {

    fun load(): Whitelist = files.read(FILE, Whitelist.serializer()) { Whitelist() }

    fun save(whitelist: Whitelist) {
        files.write(FILE, Whitelist.serializer(), whitelist)
    }

    /**
     * Identity is the channel id, never the pasted URL — an @handle and a /channel/UC...
     * link resolve to the same id, and approving the same channel twice must not produce
     * two entries and two catalogs. The first add wins, so a later paste cannot relabel a
     * channel the parent already vetted.
     */
    fun addChannel(channel: Channel): Boolean {
        val current = load()
        if (current.hasChannel(channel.id)) return false
        save(current.copy(channels = current.channels + (channel.id.value to channel)))
        return true
    }

    fun removeChannel(id: ChannelId) {
        val current = load()
        save(current.copy(channels = current.channels - id.value))
    }

    fun addSingleVideo(video: VideoRef): Boolean {
        val current = load()
        if (current.hasSingleVideo(video.id)) return false
        save(current.copy(singleVideos = current.singleVideos + (video.id.value to video)))
        return true
    }

    /**
     * Takes an individually approved video back off the list.
     *
     * Also drops any block on it: the block only ever existed to hide a video from an
     * approved set, and once the video is not approved at all the entry is a leftover
     * that would silently re-apply if the same video were approved again later.
     */
    fun removeSingleVideo(id: VideoId) {
        val current = load()
        save(
            current.copy(
                singleVideos = current.singleVideos - id.value,
                blockedVideoIds = current.blockedVideoIds - id.value,
            ),
        )
    }

    fun block(videoId: VideoId) {
        val current = load()
        save(current.copy(blockedVideoIds = current.blockedVideoIds + videoId.value))
    }

    fun unblock(videoId: VideoId) {
        val current = load()
        save(current.copy(blockedVideoIds = current.blockedVideoIds - videoId.value))
    }
}
