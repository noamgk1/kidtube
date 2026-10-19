package com.noam.kidtube.engine.fake

import com.noam.kidtube.core.Channel
import com.noam.kidtube.core.ChannelId
import com.noam.kidtube.core.VideoRef
import com.noam.kidtube.engine.ChannelResolutionError
import com.noam.kidtube.engine.ContentSource
import com.noam.kidtube.engine.Cursor
import com.noam.kidtube.engine.SearchPage
import com.noam.kidtube.engine.SearchResult
import com.noam.kidtube.engine.VideoPage

/** Convenience so tests can write videoPage(videos, nextToken = "p2"). */
fun videoPage(videos: List<VideoRef>, nextToken: String?): VideoPage =
    VideoPage(videos, nextToken?.let { Cursor(it) })

/** Scriptable stand-in. Pages are addressed by the cursor that leads to them. */
class FakeContentSource : ContentSource {

    private val channelsByUrl = mutableMapOf<String, Channel>()
    private val pagesByChannel = mutableMapOf<String, List<VideoPage>>()
    private val feeds = mutableMapOf<String, List<VideoRef>>()
    private val searches = mutableMapOf<String, List<SearchPage>>()

    var listVideosCalls: Int = 0
        private set

    /** After this many successful page calls, the next one fails. Null disables. */
    var failPagesAfter: Int? = null

    fun scriptChannel(url: String, channel: Channel) { channelsByUrl[url] = channel }

    fun scriptPages(channelId: ChannelId, pages: List<VideoPage>) {
        pagesByChannel[channelId.value] = pages
    }

    fun scriptFeed(channelId: ChannelId, videos: List<VideoRef>) {
        feeds[channelId.value] = videos
    }

    fun scriptSearch(query: String, results: List<SearchResult>, nextToken: String? = null) {
        searches[query] = listOf(SearchPage(results, nextToken?.let { Cursor(it) }))
    }

    fun scriptSearchPages(query: String, pages: List<SearchPage>) {
        searches[query] = pages
    }

    override suspend fun search(query: String, cursor: Cursor?): Result<SearchPage> {
        val pages = searches[query]
            ?: return Result.success(SearchPage(emptyList(), null))
        val index = if (cursor == null) {
            0
        } else {
            pages.indexOfFirst { it.next?.token == cursor.token } + 1
        }
        return Result.success(pages.getOrNull(index) ?: SearchPage(emptyList(), null))
    }

    override suspend fun resolveChannel(url: String): Result<Channel> =
        channelsByUrl[url]
            ?.let { Result.success(it) }
            ?: Result.failure(ChannelResolutionError.NotAChannelUrl(url, "not scripted"))

    override suspend fun listVideos(channelId: ChannelId, cursor: Cursor?): Result<VideoPage> {
        val limit = failPagesAfter
        if (limit != null && listVideosCalls >= limit) {
            return Result.failure(IllegalStateException("scripted paging failure"))
        }
        val pages = pagesByChannel[channelId.value].orEmpty()
        val index = if (cursor == null) {
            0
        } else {
            pages.indexOfFirst { it.next?.token == cursor.token } + 1
        }
        val page = pages.getOrNull(index)
            ?: return Result.failure(
                IllegalStateException("no page for cursor " + cursor?.token),
            )
        listVideosCalls++
        return Result.success(page)
    }

    override suspend fun latestVideos(
        channelId: ChannelId,
        feedUrl: String,
    ): Result<List<VideoRef>> = Result.success(feeds[channelId.value].orEmpty())
}
