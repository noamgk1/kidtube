package com.noam.kidtube.extractor

import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlin.test.assertTrue

/**
 * Does YouTube's own embedded player offer better than 360p on Made-for-Kids content?
 *
 * The extraction path does not: measured again today, a made-for-kids video returns a
 * single muxed 360p stream and nothing else, with a healthy PO token served. That is the
 * whole of the quality complaint, and the remaining lever is to stop extracting and let
 * YouTube's own player serve the video instead.
 *
 * This is meant to answer whether that lever is worth pulling before anything is built
 * around it - the same order the PO token question was settled in.
 *
 * **It does not answer it on an emulator.** Measured 2026-10-19 on a software-rendered
 * AVD: the IFrame API returns error 150 for a known-embeddable control as readily as for
 * the kids video, and the embed page loaded directly never produces a video element with
 * a size at all. Both failures are about the environment - YouTube's web player does not
 * run in this WebView - and neither is evidence about the cap. The control exists
 * precisely so that cannot be mistaken for a result.
 *
 * Run it on real hardware to get an answer. A control reading a real `WIDTHxHEIGHT`
 * above 360 while the kids video does not would mean the cap follows the content; both
 * above 360 would mean the embedded player is the way out of it.
 */
class EmbeddedQualityProbeTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** CoComelon, the same made-for-kids channel the PO token gate measures. */
    private val madeForKidsChannel = "https://www.youtube.com/@CoComelon"

    private class Bridge(val answer: CompletableDeferred<String>) {
        @JavascriptInterface
        fun report(value: String) {
            answer.complete(value)
        }
    }

    @Test
    fun theEmbeddedPlayerReportsWhatQualitiesItHas() = runBlocking {
        val source = ExtractorContentSource(
            poTokenProvider = NoPoTokens,
            downloader = OkDownloader(),
        )
        val channel = source.resolveChannel(madeForKidsChannel).getOrThrow()
        val videoId = source.listVideos(channel.id, null).getOrThrow().videos.first().id.value

        val control = probe("dQw4w9WgXcQ")
        val kids = probe(videoId)
        val controlDirect = probeDirect("dQw4w9WgXcQ")
        val kidsDirect = probeDirect(videoId)

        println("EMBED/control  " + control)
        println("EMBED/kids     " + kids + " (" + videoId + ")")
        println("EMBED/control-direct " + controlDirect)
        println("EMBED/kids-direct    " + kidsDirect)

        // Deliberately not asserting a verdict: on an emulator every one of these comes
        // back a failure of the environment, and a test that turned that into a pass or
        // a fail would be reporting something it did not measure. The printed lines are
        // the output; read them on a device.
        assertTrue(
            controlDirect != null && kids != null,
            "the probe did not complete at all",
        )
    }

    private suspend fun probe(videoId: String): String? {
        val answer = CompletableDeferred<String>()
        PageServer { origin -> pageFor(videoId, origin) }.use { server ->
            val webView = withContext(Dispatchers.Main) {
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    addJavascriptInterface(Bridge(answer), "Probe")
                    loadUrl(server.origin + "/")
                }
            }

            val reported = withTimeoutOrNull(90_000) { answer.await() }
            withContext(Dispatchers.Main) { webView.destroy() }
            return reported
        }
    }

    /**
     * The embed page loaded as itself, which is what an app embedding YouTube actually
     * does - and a real youtube.com origin rather than a page of ours pretending to be
     * one. The video element is then in the top-level document, so its height can simply
     * be read back.
     */
    private suspend fun probeDirect(videoId: String): String? {
        val webView = withContext(Dispatchers.Main) {
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                loadUrl(
                    "https://www.youtube.com/embed/" + videoId +
                        "?autoplay=1&controls=0&playsinline=1&rel=0&mute=1",
                )
            }
        }

        // Long enough for the player to load, start, and settle on a quality.
        val answer = CompletableDeferred<String>()
        repeat(12) {
            kotlinx.coroutines.delay(5_000)
            val reading = withContext(Dispatchers.Main) {
                val slot = CompletableDeferred<String>()
                webView.evaluateJavascript(
                    "(function(){var v=document.querySelector('video');" +
                        "return v ? (v.videoWidth+'x'+v.videoHeight+' t='+v.currentTime) " +
                        ": 'no-video-element';})()",
                ) { slot.complete(it) }
                slot.await()
            }
            if (reading.contains("x") && !reading.contains("0x0")) {
                answer.complete(reading)
                return@repeat
            }
        }

        val reported = withTimeoutOrNull(1_000) { answer.await() }
        withContext(Dispatchers.Main) { webView.destroy() }
        return reported ?: "never-reported-a-size"
    }

    private fun pageFor(videoId: String, origin: String): String = """
        <!doctype html><html><body style="margin:0">
        <div id="player"></div>
        <script src="https://www.youtube.com/iframe_api"></script>
        <script>
          var player;
          var sent = false;
          function send(what) { if (!sent) { sent = true; Probe.report(what); } }
          function onYouTubeIframeAPIReady() {
            player = new YT.Player('player', {
              height: '720', width: '1280', videoId: '$videoId',
              playerVars: {
                autoplay: 1, controls: 0, playsinline: 1, rel: 0, origin: '$origin'
              },
              events: {
                onReady: function (e) { e.target.mute(); e.target.playVideo(); },
                onStateChange: function (e) {
                  if (e.data === YT.PlayerState.PLAYING) {
                    // Let the player settle on a quality before asking it.
                    setTimeout(function () {
                      send(JSON.stringify(player.getAvailableQualityLevels())
                        + ' current=' + player.getPlaybackQuality());
                    }, 5000);
                  }
                },
                onError: function (e) { send('error ' + e.data); }
              }
            });
          }
          setTimeout(function () { send('timeout-in-page'); }, 70000);
        </script>
        </body></html>
    """.trimIndent()
}

/** A one-page HTTP server on loopback, so the WebView has a real origin to embed from. */
private class PageServer(private val page: (origin: String) -> String) : Closeable {

    private val socket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))

    val origin: String = "http://127.0.0.1:" + socket.localPort

    init {
        thread(isDaemon = true) {
            while (!socket.isClosed) {
                runCatching {
                    socket.accept().use { client ->
                        client.getInputStream().read(ByteArray(2048))
                        val body = page(origin).toByteArray()
                        client.getOutputStream().apply {
                            write(
                                (
                                    "HTTP/1.1 200 OK\r\n" +
                                        "Content-Type: text/html; charset=utf-8\r\n" +
                                        "Content-Length: " + body.size + "\r\n" +
                                        "Connection: close\r\n\r\n"
                                    ).toByteArray(),
                            )
                            write(body)
                            flush()
                        }
                    }
                }
            }
        }
    }

    override fun close() {
        runCatching { socket.close() }
    }
}

/** The probe is about the embedded player, which attests itself. */
private object NoPoTokens : org.schabi.newpipe.extractor.services.youtube.PoTokenProvider {
    override fun getWebClientPoToken(videoId: String) = null
    override fun getWebEmbedClientPoToken(videoId: String) = null
    override fun getAndroidClientPoToken(videoId: String) = null
    override fun getIosClientPoToken(videoId: String) = null
}
