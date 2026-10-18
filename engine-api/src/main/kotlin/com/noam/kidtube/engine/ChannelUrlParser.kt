package com.noam.kidtube.engine

import com.noam.kidtube.core.ChannelId
import com.noam.kidtube.core.VideoId
import java.net.URI

sealed interface PastedLink {
    data class ChannelById(val id: ChannelId, val canonicalUrl: String) : PastedLink
    data class ChannelByHandle(val handle: String, val canonicalUrl: String) : PastedLink

    /**
     * The /c/ and /user/ forms. Kept rather than refused, but flagged: the probe of
     * 2026-10-17 found /c/kidstv123 answering 404, so resolution may well fail and the
     * parent needs to be told why rather than shown a bare error.
     */
    data class ChannelByLegacyPath(
        val kind: String,
        val name: String,
        val canonicalUrl: String,
    ) : PastedLink

    data class SingleVideo(val id: VideoId, val canonicalUrl: String) : PastedLink

    data class Unsupported(
        val reason: UnsupportedReason,
        val raw: String,
        val detail: String,
    ) : PastedLink
}

enum class UnsupportedReason { EMPTY, NOT_A_URL, NOT_YOUTUBE, SHORTS, LIVE, PLAYLIST, UNRECOGNISED }

/**
 * Classifies a pasted string before any network call, so a typo or a wrong kind of link
 * produces a named refusal instead of a request that silently resolves to the wrong
 * channel.
 */
object ChannelUrlParser {

    private val CHANNEL_ID = Regex("^UC[A-Za-z0-9_-]{22}$")
    private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")
    private val HANDLE = Regex("^[A-Za-z0-9._-]{3,30}$")

    private val YOUTUBE_HOSTS = setOf(
        "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com",
    )
    private val SHORT_HOSTS = setOf("youtu.be", "www.youtu.be")

    /**
     * Whether a string is a canonical YouTube channel id (UC + 22 id characters).
     *
     * Exposed because the resolved id is the single point of failure behind "the same
     * channel added twice is one entry": deduplication, the catalog filename and every
     * paging URL key off it, so the extractor must refuse anything that is not one
     * rather than trusting whatever URL the library hands back.
     */
    fun isCanonicalChannelId(value: String): Boolean = CHANNEL_ID.matches(value)

    fun parse(raw: String): PastedLink {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return PastedLink.Unsupported(UnsupportedReason.EMPTY, raw, "nothing pasted")
        }

        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://" + trimmed
        }

        val uri = runCatching { URI(withScheme) }.getOrNull()
        val host = uri?.host?.lowercase()
        if (uri == null || host.isNullOrBlank() || !host.contains('.')) {
            return PastedLink.Unsupported(UnsupportedReason.NOT_A_URL, raw, "not parseable as a URL")
        }

        if (host in SHORT_HOSTS) {
            val id = uri.path.trim('/').substringBefore('/')
            return videoOrUnrecognised(id, raw)
        }

        // Exact host match only: youtube.com.evil.test must not pass.
        if (host !in YOUTUBE_HOSTS) {
            return PastedLink.Unsupported(UnsupportedReason.NOT_YOUTUBE, raw, "host is " + host)
        }

        val segments = uri.path.trim('/').split('/').filter { it.isNotEmpty() }
        val query = parseQuery(uri.rawQuery)

        if (segments.isEmpty()) {
            return PastedLink.Unsupported(UnsupportedReason.UNRECOGNISED, raw, "no path")
        }

        val first = segments[0]
        return when {
            first.startsWith("@") -> {
                val handle = first.removePrefix("@")
                if (HANDLE.matches(handle)) {
                    PastedLink.ChannelByHandle(handle, "https://www.youtube.com/@" + handle)
                } else {
                    PastedLink.Unsupported(UnsupportedReason.UNRECOGNISED, raw, "bad handle: " + handle)
                }
            }

            first == "channel" -> {
                val id = segments.getOrNull(1).orEmpty()
                if (CHANNEL_ID.matches(id)) {
                    PastedLink.ChannelById(ChannelId(id), "https://www.youtube.com/channel/" + id)
                } else {
                    PastedLink.Unsupported(UnsupportedReason.UNRECOGNISED, raw, "bad channel id: " + id)
                }
            }

            first == "c" || first == "user" -> {
                val name = segments.getOrNull(1).orEmpty()
                if (name.isNotBlank()) {
                    PastedLink.ChannelByLegacyPath(
                        first,
                        name,
                        "https://www.youtube.com/" + first + "/" + name,
                    )
                } else {
                    PastedLink.Unsupported(UnsupportedReason.UNRECOGNISED, raw, "empty " + first + " path")
                }
            }

            first == "watch" -> videoOrUnrecognised(query["v"].orEmpty(), raw)

            first == "shorts" -> PastedLink.Unsupported(
                UnsupportedReason.SHORTS, raw, "Shorts are excluded by design",
            )

            first == "live" -> PastedLink.Unsupported(
                UnsupportedReason.LIVE, raw, "live content cannot be reviewed in advance",
            )

            first == "playlist" -> PastedLink.Unsupported(
                UnsupportedReason.PLAYLIST, raw, "playlists are not supported yet",
            )

            else -> PastedLink.Unsupported(UnsupportedReason.UNRECOGNISED, raw, "unknown path: " + first)
        }
    }

    private fun videoOrUnrecognised(id: String, raw: String): PastedLink =
        if (VIDEO_ID.matches(id)) {
            PastedLink.SingleVideo(VideoId(id), "https://www.youtube.com/watch?v=" + id)
        } else {
            PastedLink.Unsupported(UnsupportedReason.UNRECOGNISED, raw, "bad video id: " + id)
        }

    private fun parseQuery(rawQuery: String?): Map<String, String> =
        rawQuery
            ?.split('&')
            ?.mapNotNull { pair ->
                val index = pair.indexOf('=')
                if (index <= 0) null else pair.substring(0, index) to pair.substring(index + 1)
            }
            ?.toMap()
            ?: emptyMap()
}
