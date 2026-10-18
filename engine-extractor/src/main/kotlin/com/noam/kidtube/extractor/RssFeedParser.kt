package com.noam.kidtube.extractor

import com.noam.kidtube.core.ChannelId
import com.noam.kidtube.core.VideoId
import com.noam.kidtube.core.VideoRef
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.time.OffsetDateTime

/**
 * Parses a YouTube channel Atom feed. One HTTP request answers "what is new", which is
 * the whole daily refresh — far cheaper than the 1.2 to 7.3 seconds per page the
 * extractor costs, as measured on 2026-10-17.
 *
 * The feed carries no duration, so [VideoRef.durationSeconds] comes back null and is
 * filled in later by a deep sync or at play time.
 */
internal object RssFeedParser {

    fun parse(xml: String): List<VideoRef> {
        if (xml.isBlank()) return emptyList()

        val document = runCatching { Jsoup.parse(xml, "", Parser.xmlParser()) }
            .getOrNull() ?: return emptyList()

        return document.select("entry").mapNotNull { entry ->
            val videoId = entry.selectFirst("yt|videoId")?.text()?.trim()
                ?: entry.selectFirst("videoId")?.text()?.trim()
            if (videoId.isNullOrBlank()) return@mapNotNull null

            val channelId = entry.selectFirst("yt|channelId")?.text()?.trim()
                ?: entry.selectFirst("channelId")?.text()?.trim()
                ?: return@mapNotNull null

            VideoRef(
                id = VideoId(videoId),
                channelId = ChannelId(channelId),
                title = entry.selectFirst("title")?.text()?.trim().orEmpty(),
                thumbnailUrl = entry.selectFirst("media|thumbnail")
                    ?.attr("url")
                    ?.takeIf { it.isNotBlank() },
                durationSeconds = null,
                publishedAtEpochSeconds = entry.selectFirst("published")?.text()?.let { raw ->
                    runCatching { OffsetDateTime.parse(raw.trim()).toEpochSecond() }.getOrNull()
                },
            )
        }
    }
}
