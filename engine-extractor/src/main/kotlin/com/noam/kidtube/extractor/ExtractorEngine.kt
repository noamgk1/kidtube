package com.noam.kidtube.extractor

import android.content.Context
import com.noam.kidtube.core.Clock
import com.noam.kidtube.engine.ContentSource
import com.noam.kidtube.engine.EngineHealth
import com.noam.kidtube.engine.PlaybackResolver
import com.noam.kidtube.engine.PlayerEngine
import kotlinx.coroutines.flow.StateFlow

/**
 * Everything this module hands out, and nothing else.
 *
 * Every type here comes from :core or :engine-api. That is the point: NewPipeExtractor
 * is an `implementation` dependency of this module, so if a NewPipeExtractor type
 * appeared in any public signature, `:app` would fail to compile. The module boundary
 * from the design spec is enforced by the compiler rather than by discipline.
 */
class ExtractorEngineBundle internal constructor(
    val contentSource: ContentSource,
    val playbackResolver: PlaybackResolver,
    val playerEngine: PlayerEngine,
    val health: StateFlow<EngineHealth>,
    private val releaseSession: () -> Unit,
) {
    /** Tears down the WebView that backs PO token generation. */
    fun release() = releaseSession()
}

/** The only entry point into the extractor engine. */
object ExtractorEngine {

    /**
     * [embedded] swaps extracted streams for YouTube's own embedded player.
     *
     * Only the playback half changes: the same content source lists the same approved
     * videos either way. It exists because Made-for-Kids content is served a single 360p
     * stream through extraction and nothing better exists to choose, while YouTube's own
     * player is not held to that. Everything else about it is worse - a WebView, and the
     * video drawn by someone else's player - so it is the parent's choice, not the
     * default.
     */
    fun create(
        appContext: Context,
        clock: Clock = SystemClockImpl,
        maxHeight: Int = 1080,
        preferredLocaleTag: String = "he-IL",
        embedded: Boolean = false,
    ): ExtractorEngineBundle {
        val session = PoTokenSession(WebViewPoTokenGenerator(appContext), clock)
        val downloader = OkDownloader()
        return ExtractorEngineBundle(
            contentSource = ExtractorContentSource(
                session,
                downloader,
                localeTag = preferredLocaleTag,
            ),
            playbackResolver = if (embedded) {
                EmbeddedPlaybackResolver(session, downloader)
            } else {
                ExtractorPlaybackResolver(session, downloader, maxHeight, preferredLocaleTag)
            },
            playerEngine = if (embedded) {
                EmbeddedPlayerEngine(appContext)
            } else {
                ExoPlayerEngine(appContext)
            },
            health = session.health,
            releaseSession = session::release,
        )
    }
}
