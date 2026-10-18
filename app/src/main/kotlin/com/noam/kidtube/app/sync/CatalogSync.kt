package com.noam.kidtube.app.sync

import com.noam.kidtube.core.ChannelId
import com.noam.kidtube.core.Clock
import com.noam.kidtube.core.SyncState
import com.noam.kidtube.core.store.CatalogStore
import com.noam.kidtube.core.store.SyncStateStore
import com.noam.kidtube.core.store.WhitelistStore
import com.noam.kidtube.engine.ContentSource
import com.noam.kidtube.engine.Cursor

/**
 * Two speeds.
 *
 *  - [deepSync] pages the whole catalog. It cost between 1.2 and 7.3 seconds per page
 *    when measured on 2026-10-17, so it runs on add and on explicit rebuild only, in the
 *    background, with visible progress.
 *  - [refresh] reads the channel's RSS feed: one request, the newest entries, cheap
 *    enough to run on every launch.
 *
 * A sync interrupted by process death simply re-pages: NewPipe's cursors cannot be
 * serialised, and [CatalogStore.merge] de-duplicates by id, so re-walking is correct
 * even if it costs seconds. The visible progress is what tells the parent a catalog is
 * still partial.
 */
class CatalogSync(
    private val contentSource: ContentSource,
    private val whitelistStore: WhitelistStore,
    private val catalogStore: CatalogStore,
    private val syncStateStore: SyncStateStore,
    private val clock: Clock,
    private val maxPages: Int = MAX_PAGES,
) {

    /** Returns how many videos were newly stored. */
    suspend fun deepSync(channelId: ChannelId): Result<Int> {
        var cursor: Cursor? = null
        var pages = 0
        var added = 0

        while (pages < maxPages) {
            val page = contentSource.listVideos(channelId, cursor).getOrElse { cause ->
                writeState(channelId, pages, complete = false, error = cause.message ?: "paging failed")
                return Result.failure(cause)
            }

            added += catalogStore.merge(channelId, page.videos)
            pages++

            val next = page.next
            if (next == null) {
                writeState(channelId, pages, complete = true, error = null)
                return Result.success(added)
            }
            cursor = next
        }

        // Hit the cap with pages still outstanding: an honest partial, not a silent success.
        writeState(channelId, pages, complete = false, error = null)
        return Result.success(added)
    }

    /** Returns how many videos the feed contributed that were not already stored. */
    suspend fun refresh(channelId: ChannelId): Result<Int> {
        val channel = whitelistStore.load().channels[channelId.value]
            ?: return Result.failure(
                IllegalArgumentException("channel " + channelId.value + " is not approved"),
            )

        return contentSource.latestVideos(channelId, channel.feedUrl)
            .map { catalogStore.merge(channelId, it) }
    }

    suspend fun refreshAll(): Map<ChannelId, Result<Int>> =
        whitelistStore.load().channels.keys.associate { id ->
            val channelId = ChannelId(id)
            channelId to refresh(channelId)
        }

    private fun writeState(channelId: ChannelId, pages: Int, complete: Boolean, error: String?) {
        syncStateStore.save(
            SyncState(
                channelId = channelId.value,
                pagesDone = pages,
                videosStored = catalogStore.load(channelId).size,
                complete = complete,
                lastError = error,
                updatedAtEpochSeconds = clock.nowEpochMillis() / 1_000,
            ),
        )
    }

    companion object {
        /** 60 pages is roughly 1,800 videos - beyond any plausible kids channel. */
        const val MAX_PAGES: Int = 60
    }
}
