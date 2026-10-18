package com.noam.kidtube.app.admin

import com.noam.kidtube.core.ChannelId
import com.noam.kidtube.core.VideoId
import com.noam.kidtube.core.VideoRef
import com.noam.kidtube.core.store.CatalogStore
import com.noam.kidtube.core.store.WhitelistStore

data class CatalogRow(val video: VideoRef, val blocked: Boolean)

/**
 * Lists a channel's catalog for vetting. Blocked videos stay in the list, marked, so the
 * parent can see and reverse what they blocked.
 */
class CatalogPresenter(
    private val whitelistStore: WhitelistStore,
    private val catalogStore: CatalogStore,
) {
    fun rows(channelId: ChannelId): List<CatalogRow> {
        val whitelist = whitelistStore.load()
        return catalogStore.load(channelId).map { CatalogRow(it, whitelist.isBlocked(it.id)) }
    }

    fun block(videoId: VideoId) = whitelistStore.block(videoId)

    fun unblock(videoId: VideoId) = whitelistStore.unblock(videoId)
}
