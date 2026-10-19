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
    /** Extracted streams played by our own player. The default. */
    val playbackResolver: PlaybackResolver,
    val playerEngine: PlayerEngine,
    /**
     * The same videos played by YouTube's embedded player instead.
     *
     * Built alongside the other rather than instead of it, because the parent switches
     * between them while the app is running. Building one at startup meant the choice
     * only took effect when the process next died, which on Android is whenever it
     * happens to - the setting looked like it did nothing.
     */
    val embeddedPlaybackResolver: PlaybackResolver,
    val embeddedPlayerEngine: PlayerEngine,
    val health: StateFlow<EngineHealth>,
    private val releaseSession: () -> Unit,
) {
    /** Tears down the WebView that backs PO token generation. */
    fun release() = releaseSession()
}

/** The only entry point into the extractor engine. */
object ExtractorEngine {

    /**
     * [knownVideo] looks a video up in what is already approved and stored.
     *
     * The embedded path uses it to skip a network round trip it does not need: see
     * EmbeddedPlaybackResolver. Returning null for everything is safe and simply means
     * every playback resolves the slow way.
     */
    fun create(
        appContext: Context,
        clock: Clock = SystemClockImpl,
        maxHeight: Int = 1080,
        preferredLocaleTag: String = "he-IL",
        knownVideo: (com.noam.kidtube.core.VideoId) -> com.noam.kidtube.core.VideoRef? = { null },
        /** Reports what the embedded player settled on, e.g. "hd1080". */
        onEmbeddedQuality: (String) -> Unit = {},
    ): ExtractorEngineBundle {
        val session = PoTokenSession(WebViewPoTokenGenerator(appContext), clock)
        val downloader = OkDownloader()
        return ExtractorEngineBundle(
            contentSource = ExtractorContentSource(
                session,
                downloader,
                localeTag = preferredLocaleTag,
            ),
            playbackResolver = ExtractorPlaybackResolver(
                session,
                downloader,
                maxHeight,
                preferredLocaleTag,
            ),
            playerEngine = ExoPlayerEngine(appContext),
            embeddedPlaybackResolver = EmbeddedPlaybackResolver(knownVideo, session, downloader),
            embeddedPlayerEngine = EmbeddedPlayerEngine(appContext, onEmbeddedQuality),
            health = session.health,
            releaseSession = session::release,
        )
    }
}
