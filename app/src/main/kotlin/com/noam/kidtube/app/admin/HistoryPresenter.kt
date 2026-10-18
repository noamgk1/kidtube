package com.noam.kidtube.app.admin

import com.noam.kidtube.core.VideoId
import com.noam.kidtube.core.store.WatchLedger
import com.noam.kidtube.core.store.WhitelistStore

data class HistoryRow(
    val videoId: String,
    val title: String,
    val plays: Int,
    val totalSeconds: Int,
    val lastAtEpochSeconds: Long,
    val blocked: Boolean,
)

/**
 * History is how a parent finds out what to block, so blocking lives on the row rather
 * than on another screen. That is what closes the loop between the two features.
 */
class HistoryPresenter(
    private val ledger: WatchLedger,
    private val whitelistStore: WhitelistStore,
) {
    fun rows(): List<HistoryRow> {
        val whitelist = whitelistStore.load()
        return ledger.aggregate().map { aggregate ->
            HistoryRow(
                videoId = aggregate.videoId,
                title = aggregate.title,
                plays = aggregate.plays,
                totalSeconds = aggregate.totalSeconds,
                lastAtEpochSeconds = aggregate.lastAtEpochSeconds,
                blocked = whitelist.isBlocked(VideoId(aggregate.videoId)),
            )
        }
    }

    fun block(videoId: VideoId) = whitelistStore.block(videoId)

    fun unblock(videoId: VideoId) = whitelistStore.unblock(videoId)
}
