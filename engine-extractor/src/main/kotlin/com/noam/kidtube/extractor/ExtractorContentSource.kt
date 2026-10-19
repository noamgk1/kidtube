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
import com.noam.kidtube.engine.SearchPage
import com.noam.kidtube.engine.SearchResult
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
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.services.youtube.PoTokenProvider
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import okhttp3.Request as OkRequest

internal class ExtractorContentSource(
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
            val resolvedId = info.url.substringAfterLast('/')
            // Everything downstream keys off this id, so a non-canonical one is refused
            // rather than stored: it would duplicate the channel, write a catalog file
            // under a handle, and build paging URLs that cannot work.
            if (!ChannelUrlParser.isCanonicalChannelId(resolvedId)) {
                return@withContext Result.failure(
                    ChannelResolutionError.Unreachable(
                        canonical,
                        IllegalStateException(
                            "resolved to a non-canonical channel id: '" + resolvedId + "'",
                        ),
                    ),
                )
            }
            Result.success(
                Channel(
                    id = ChannelId(resolvedId),
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

    /**
     * Searches YouTube for the parent.
     *
     * Only channels and videos come back. A playlist hit is dropped rather than shown:
     * approving one would mean approving a list whose owner can add to it afterwards,
     * which is a hole in the whitelist rather than a feature of it.
     */
    override suspend fun search(query: String, cursor: Cursor?): Result<SearchPage> =
        withContext(Dispatchers.IO) {
            ExtractorInit.ensureInitialised(downloader, poTokenProvider)

            runCatching {
                val handler = youtube.searchQHFactory.fromQuery(query)
                if (cursor == null) {
                    val info = SearchInfo.getInfo(youtube, handler)
                    toSearchPage(info.relatedItems, info.nextPage)
                } else {
                    val page = pages[cursor.token]
                        ?: error("unknown search cursor: " + cursor.token)
                    val more = SearchInfo.getMoreItems(youtube, handler, page)
                    toSearchPage(more.items, more.nextPage)
                }
            }
        }

    private fun toSearchPage(items: List<InfoItem>, next: Page?): SearchPage {
        val results = items.mapNotNull { item ->
            when (item) {
                is ChannelInfoItem -> {
                    val id = item.url.substringAfterLast('/')
                    // The same rule as a pasted link: everything downstream keys off a
                    // canonical UC id, so a hit that does not resolve to one is dropped
                    // rather than offered and then stored under a handle.
                    if (ChannelUrlParser.isCanonicalChannelId(id)) {
                        SearchResult.ChannelHit(
                            Channel(
                                id = ChannelId(id),
                                title = item.name.orEmpty(),
                                avatarUrl = item.thumbnails.firstOrNull()?.url,
                                feedUrl = FEED_URL_PREFIX + id,
                                subscriberCount = item.subscriberCount.coerceAtLeast(0),
                            ),
                        )
                    } else {
                        null
                    }
                }

                is StreamInfoItem -> {
                    val videoId = videoIdFrom(item.url)
                    val channelId = item.uploaderUrl?.substringAfterLast('/').orEmpty()
                    // A video whose uploader cannot be pinned down would be approved
                    // without the channel it belongs to, and Policy checks both.
                    if (videoId.isNotBlank() &&
                        ChannelUrlParser.isCanonicalChannelId(channelId) &&
                        item.streamType != StreamType.LIVE_STREAM
                    ) {
                        SearchResult.VideoHit(
                            video = VideoRef(
                                id = VideoId(videoId),
                                channelId = ChannelId(channelId),
                                title = item.name.orEmpty(),
                                thumbnailUrl = item.thumbnails.firstOrNull()?.url,
                                durationSeconds = item.duration.takeIf { it > 0 },
                                publishedAtEpochSeconds =
                                    item.uploadDate?.offsetDateTime()?.toEpochSecond(),
                            ),
                            channelTitle = item.uploaderName.orEmpty(),
                        )
                    } else {
                        null
                    }
                }

                else -> null
            }
        }

        val nextCursor = next?.let {
            val token = "s" + cursorSequence.incrementAndGet()
            pages[token] = it
            Cursor(token)
        }
        return SearchPage(results, nextCursor)
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

/**
 * YouTube's per-channel RSS feed. The same URL `resolveChannel` reads off ChannelInfo;
 * constructed here because a search hit carries no feed of its own, and re-resolved for
 * real when the parent actually approves the channel.
 */
private const val FEED_URL_PREFIX = "https://www.youtube.com/feeds/videos.xml?channel_id="

private val feedClient by lazy { OkHttpClient() }

private fun downloadText(url: String): String =
    feedClient.newCall(OkRequest.Builder().url(url).build()).execute().use { response ->
        check(response.isSuccessful) { "feed request failed: HTTP " + response.code }
        response.body?.string().orEmpty()
    }
