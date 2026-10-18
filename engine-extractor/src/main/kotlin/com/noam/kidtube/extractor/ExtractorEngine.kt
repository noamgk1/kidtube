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

    fun create(
        appContext: Context,
        clock: Clock = SystemClockImpl,
        maxHeight: Int = 1080,
        preferredLocaleTag: String = "he",
    ): ExtractorEngineBundle {
        val session = PoTokenSession(WebViewPoTokenGenerator(appContext), clock)
        val downloader = OkDownloader()
        return ExtractorEngineBundle(
            contentSource = ExtractorContentSource(session, downloader),
            playbackResolver = ExtractorPlaybackResolver(
                session,
                downloader,
                maxHeight,
                preferredLocaleTag,
            ),
            playerEngine = ExoPlayerEngine(appContext),
            health = session.health,
            releaseSession = session::release,
        )
    }
}
