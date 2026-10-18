package com.noam.kidtube.extractor

import com.noam.kidtube.core.Channel
import com.noam.kidtube.core.ChannelId
import com.noam.kidtube.core.VideoId
import com.noam.kidtube.core.VideoRef
import com.noam.kidtube.engine.ChannelResolutionError
import com.noam.kidtube.engine.ChannelUrlParser
import com.noam.kidtube.engine.ContentSource
import com.noam.kidtube.engine.Cursor
import com.noam.kidtube.engine.PastedLink
import com.noam.kidtube.engine.VideoPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.services.youtube.PoTokenProvider
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import okhttp3.Request as OkRequest

class ExtractorContentSource(
    private val poTokenProvider: PoTokenProvider,
    private val downloader: Downloader = OkDownloader(),
    private val httpGet: (String) -> String = ::downloadText,
) : ContentSource {

    private val youtube get() = ServiceList.YouTube

    /**
     * NewPipe's Page is not something worth serialising, so cursors are opaque tokens
     * mapped to live Page objects for the life of the process. A sync interrupted by
     * process death resumes by re-paging from the first page and relying on id
     * de-duplication in CatalogStore.merge, rather than by persisting these.
     */
    private val pages = ConcurrentHashMap<String, Page>()
    private val videoTabs = ConcurrentHashMap<String, ListLinkHandler>()
    private val cursorSequence = AtomicLong()

    override suspend fun resolveChannel(url: String): Result<Channel> = withContext(Dispatchers.IO) {
        ExtractorInit.ensureInitialised(downloader, poTokenProvider)

        val canonical = when (val link = ChannelUrlParser.parse(url)) {
            is PastedLink.ChannelById -> link.canonicalUrl
            is PastedLink.ChannelByHandle -> link.canonicalUrl
            is PastedLink.ChannelByLegacyPath -> link.canonicalUrl
            is PastedLink.SingleVideo -> return@withContext Result.failure(
                ChannelResolutionError.NotAChannelUrl(url, "this is a single video, not a channel"),
            )
            is PastedLink.Unsupported -> return@withContext Result.failure(
                ChannelResolutionError.NotAChannelUrl(url, link.detail),
            )
        }

        try {
            val info = ChannelInfo.getInfo(youtube, canonical)
            Result.success(
                Channel(
                    id = ChannelId(info.url.substringAfterLast('/')),
                    title = info.name.orEmpty(),
                    avatarUrl = info.avatars.firstOrNull()?.url,
                    feedUrl = info.feedUrl.orEmpty(),
                    subscriberCount = info.subscriberCount,
                ),
            )
        } catch (notAvailable: ContentNotAvailableException) {
            Result.failure(ChannelResolutionError.ChannelNotFound(canonical))
        } catch (other: Exception) {
            Result.failure(ChannelResolutionError.Unreachable(canonical, other))
        }
    }

    override suspend fun listVideos(
        channelId: ChannelId,
        cursor: Cursor?,
    ): Result<VideoPage> = withContext(Dispatchers.IO) {
        ExtractorInit.ensureInitialised(downloader, poTokenProvider)

        runCatching {
            if (cursor == null) {
                val info = ChannelInfo.getInfo(youtube, channelUrl(channelId))
                // Only the videos tab. Shorts and livestreams are never read, so they
                // cannot appear in the app at all.
                val tab = info.tabs.firstOrNull { it.contentFilters.contains(ChannelTabs.VIDEOS) }
                    ?: return@runCatching VideoPage(emptyList(), null)
                videoTabs[channelId.value] = tab

                val tabInfo = ChannelTabInfo.getInfo(youtube, tab)
                toPage(channelId, tabInfo.relatedItems, tabInfo.nextPage)
            } else {
                val tab = videoTabs[channelId.value]
                    ?: error(
                        "no videos tab cached for " + channelId.value +
                            "; restart paging from the first page",
                    )
                val page = pages[cursor.token]
                    ?: error(
                        "unknown cursor " + cursor.token + "; restart paging from the first page",
                    )
                val more = ChannelTabInfo.getMoreItems(youtube, tab, page)
                toPage(channelId, more.items, more.nextPage)
            }
        }
    }

    override suspend fun latestVideos(
        channelId: ChannelId,
        feedUrl: String,
    ): Result<List<VideoRef>> = withContext(Dispatchers.IO) {
        runCatching { RssFeedParser.parse(httpGet(feedUrl)) }
    }

    private fun toPage(
        channelId: ChannelId,
        items: List<InfoItem>,
        next: Page?,
    ): VideoPage {
        val videos = items
            .filterIsInstance<StreamInfoItem>()
            // A past livestream still appears under videos; keep those out too.
            .filter { it.streamType != StreamType.LIVE_STREAM && !it.isShortFormContent }
            .map { item ->
                VideoRef(
                    id = VideoId(videoIdFrom(item.url)),
                    channelId = channelId,
                    title = item.name.orEmpty(),
                    thumbnailUrl = item.thumbnails.firstOrNull()?.url,
                    durationSeconds = item.duration.takeIf { it > 0 },
                    publishedAtEpochSeconds = item.uploadDate?.offsetDateTime()?.toEpochSecond(),
                )
            }
            .filter { it.id.value.isNotBlank() }

        val cursor = next?.let {
            val token = "p" + cursorSequence.incrementAndGet()
            pages[token] = it
            Cursor(token)
        }
        return VideoPage(videos, cursor)
    }

    private fun videoIdFrom(url: String): String =
        url.substringAfter("v=", "").substringBefore('&')

    private fun channelUrl(channelId: ChannelId) =
        "https://www.youtube.com/channel/" + channelId.value
}

private val feedClient by lazy { OkHttpClient() }

private fun downloadText(url: String): String =
    feedClient.newCall(OkRequest.Builder().url(url).build()).execute().use { response ->
        check(response.isSuccessful) { "feed request failed: HTTP " + response.code }
        response.body?.string().orEmpty()
    }
