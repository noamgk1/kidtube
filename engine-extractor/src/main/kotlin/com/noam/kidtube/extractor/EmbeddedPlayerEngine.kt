package com.noam.kidtube.extractor

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.noam.kidtube.core.ConfirmedTicket
import com.noam.kidtube.core.PlaybackSource
import com.noam.kidtube.engine.PlaybackPhase
import com.noam.kidtube.engine.PlayerEngine
import com.noam.kidtube.engine.PlayerState
import com.noam.kidtube.engine.VideoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the local page is served as. A real https origin, which is the whole point. */
private const val EMBED_ORIGIN = "https://appassets.androidplatform.net"
private const val EMBED_URL = EMBED_ORIGIN + "/assets/embed_player.html"

/**
 * Playback through YouTube's own embedded player instead of extracted streams.
 *
 * It exists for one measured reason: Made-for-Kids content returns a single 360p stream
 * through extraction and nothing else, while YouTube's own player serves the same video
 * at full quality. Everything else about this engine is worse than the extractor one -
 * it is a WebView, it needs YouTube's JavaScript, and the video is drawn by someone
 * else's player - so it is a choice the parent makes, not the default.
 *
 * Two things keep the whitelist promise intact. [start] still takes a [ConfirmedTicket],
 * so only a video Policy has approved can ever be loaded. And every touch is swallowed
 * before it reaches the iframe: what is tappable inside YouTube's player is YouTube's -
 * end screens, the channel name, "watch on YouTube" - and none of it should be reachable
 * by a child. The app draws its own controls outside the WebView.
 */
internal class EmbeddedVideoPlayer(
    context: Context,
    /** Told what YouTube settled on, so the parent can see whether the quality claim held. */
    private val onQuality: (String) -> Unit = {},
) : VideoPlayer {

    private val mutableState = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = mutableState.asStateFlow()

    private val main = Handler(Looper.getMainLooper())

    /** Set once the page says the IFrame API is up; commands before that are queued. */
    private var ready = false
    private var pending: (() -> Unit)? = null

    private val assets = WebViewAssetLoader.Builder()
        .setDomain("appassets.androidplatform.net")
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .build()

    internal val webView: WebView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        setBackgroundColor(android.graphics.Color.BLACK)
        addJavascriptInterface(Bridge(), "KidTube")
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? = assets.shouldInterceptRequest(request.url)

            /**
             * Nothing navigates. A tap that got through would otherwise be able to leave
             * for youtube.com, and there is no route back from there into the app's rules.
             */
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean = request.url.toString() != EMBED_URL
        }
        loadUrl(EMBED_URL)
    }

    private inner class Bridge {
        @JavascriptInterface
        fun onEvent(kind: String, value: String) {
            when (kind) {
                "ready" -> main.post {
                    ready = true
                    pending?.invoke()
                    pending = null
                }

                "state" -> mutableState.value = mutableState.value.copy(
                    phase = phaseFor(value.toIntOrNull()),
                )

                "time" -> {
                    val position = value.substringBefore(':').toDoubleOrNull() ?: return
                    val duration = value.substringAfter(':').toDoubleOrNull() ?: return
                    mutableState.value = mutableState.value.copy(
                        positionMs = (position * 1_000).toLong(),
                        durationMs = (duration * 1_000).toLong(),
                    )
                }

                "quality" -> onQuality(value)

                // 101 and 150 are "the owner does not allow embedded playback", which is
                // the one failure this engine has and the extractor engine does not.
                "error" -> mutableState.value = mutableState.value.copy(
                    phase = PlaybackPhase.FAILED,
                    failure = "embedded player error " + value,
                    failureIsRecoverable = false,
                )
            }
        }
    }

    /** YouTube's player states; -1 unstarted, 0 ended, 1 playing, 2 paused, 3 buffering. */
    private fun phaseFor(code: Int?): PlaybackPhase = when (code) {
        0 -> PlaybackPhase.ENDED
        1 -> PlaybackPhase.PLAYING
        2 -> PlaybackPhase.PAUSED
        3 -> PlaybackPhase.BUFFERING
        else -> mutableState.value.phase
    }

    override fun start(confirmed: ConfirmedTicket, positionMs: Long) {
        val source = confirmed.details.source
        if (source !is PlaybackSource.Embedded) {
            mutableState.value = PlayerState(
                phase = PlaybackPhase.FAILED,
                failure = "the embedded engine was handed extracted streams",
            )
            return
        }

        mutableState.value = PlayerState(
            phase = PlaybackPhase.BUFFERING,
            positionMs = positionMs,
            durationMs = confirmed.details.durationSeconds * 1_000L,
        )
        val seconds = positionMs.coerceAtLeast(0) / 1_000
        run("loadVideo('" + source.videoId.value + "', " + seconds + ")")
    }

    override fun play() = run("play()")

    override fun pause() = run("pause()")

    override fun seekTo(positionMs: Long) {
        run("seek(" + (positionMs.coerceAtLeast(0) / 1_000) + ")")
        mutableState.value = mutableState.value.copy(positionMs = positionMs)
    }

    override fun release() {
        onMain {
            webView.loadUrl("about:blank")
            webView.destroy()
        }
        mutableState.value = PlayerState()
    }

    /**
     * A WebView may only be touched from the main thread, and the coordinator calls this
     * player from an IO dispatcher - the same rule ExoPlayer enforces with a throw, which
     * this one would hit as an obscure crash instead. Marshalling here means no caller
     * has to know.
     */
    private fun run(script: String) = onMain {
        if (ready) {
            webView.evaluateJavascript(script, null)
        } else {
            // The page is still loading; the app's first command is almost always a
            // start(), and dropping it would show a black screen forever.
            pending = { webView.evaluateJavascript(script, null) }
        }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    /**
     * Whether YouTube's IFrame API has loaded and built its player. Exposed for the
     * instrumented test that checks the embedding origin is accepted - the one failure
     * mode of this engine that is invisible from the outside, because a refused origin
     * looks exactly like a video that is simply slow to start.
     */
    internal fun isReadyForTest(): Boolean = ready
}

internal class EmbeddedPlayerEngine(
    private val appContext: Context,
    private val onQuality: (String) -> Unit = {},
) : PlayerEngine {

    override fun createController(scope: CoroutineScope): VideoPlayer =
        EmbeddedVideoPlayer(appContext.applicationContext, onQuality)

    @Composable
    override fun Surface(controller: VideoPlayer, modifier: Modifier) {
        val webView = (controller as EmbeddedVideoPlayer).webView
        AndroidView(modifier = modifier, factory = { webView })
    }
}
