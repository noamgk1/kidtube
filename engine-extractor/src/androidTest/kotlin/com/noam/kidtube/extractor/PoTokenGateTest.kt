package com.noam.kidtube.extractor

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.PoTokenProvider
import org.schabi.newpipe.extractor.services.youtube.PoTokenResult
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertTrue

/**
 * Counts what the library actually asks for. EngineHealth.Healthy is also the initial
 * value, so health alone cannot tell "a token was minted" from "a token was never
 * requested" - and that distinction is the whole meaning of the verdict below.
 */
private class CountingPoTokenProvider(
    private val delegate: PoTokenProvider,
    /** When true, every client is served the web attestation rather than only the web one. */
    private val serveAllClients: Boolean = false,
) : PoTokenProvider {
    val calls = java.util.Collections.synchronizedList(mutableListOf<String>())
    val served = AtomicInteger()

    private fun record(client: String, token: PoTokenResult?): PoTokenResult? {
        calls.add(client + "=" + (if (token == null) "null" else "token"))
        if (token != null) served.incrementAndGet()
        return token
    }

    override fun getWebClientPoToken(videoId: String): PoTokenResult? =
        record("web", delegate.getWebClientPoToken(videoId))

    override fun getWebEmbedClientPoToken(videoId: String): PoTokenResult? =
        record(
            "webEmbed",
            if (serveAllClients) delegate.getWebClientPoToken(videoId) else null,
        )

    override fun getAndroidClientPoToken(videoId: String): PoTokenResult? =
        record(
            "android",
            if (serveAllClients) delegate.getWebClientPoToken(videoId) else null,
        )

    override fun getIosClientPoToken(videoId: String): PoTokenResult? =
        record(
            "ios",
            if (serveAllClients) delegate.getWebClientPoToken(videoId) else null,
        )
}

/**
 * THE VERIFICATION GATE (design spec section 6).
 *
 * Question: does a real PO token, generated in a device WebView, lift YouTube's 360p cap
 * on Made-for-Kids content?
 *
 * The JVM probe of 2026-10-17 measured a deterministic single 360p muxed stream for every
 * Made-for-Kids video with no token installed, across six channels. This test re-measures
 * on-device, with a token, and prints a verdict line.
 *
 * Run it, then record the answer in docs/notes/notes/gate-result.md:
 *
 *   ./gradlew :engine-extractor:connectedDebugAndroidTest --tests '*PoTokenGateTest*' --info
 *
 *   maxHeight > 360  -> the cap lifts; the extractor engine stands as planned.
 *   maxHeight == 360 -> the cap holds; return to design spec section 2 and decide between
 *                       accepting 360p for kids content and bringing the official engine
 *                       forward.
 */
class PoTokenGateTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // Cocomelon and a general-audience control, the same pairing the probe used.
    private val madeForKidsChannel = "https://www.youtube.com/@CoComelon"
    private val controlVideo = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"

    private var session: PoTokenSession? = null

    @After
    fun tearDown() {
        session?.release()
    }

    @Test
    fun gateReportsWhetherAPoTokenLiftsTheMadeForKidsCap() = runBlocking {
        val poTokenSession = PoTokenSession(WebViewPoTokenGenerator(context), SystemClockImpl)
        session = poTokenSession
        // serveAllClients: the library asked only the non-web clients on v0.26.5, so
        // returning null for them meant nothing was ever attested. Serve them the web
        // attestation and see whether the cap moves.
        val counting = CountingPoTokenProvider(poTokenSession, serveAllClients = true)
        val downloader = OkDownloader()
        ExtractorInit.ensureInitialised(downloader, counting)

        val source = ExtractorContentSource(counting, downloader)
        val channel = source.resolveChannel(madeForKidsChannel).getOrThrow()
        val firstVideo = source.listVideos(channel.id, null).getOrThrow().videos.first()

        val kids = StreamInfo.getInfo(
            ServiceList.YouTube,
            "https://www.youtube.com/watch?v=" + firstVideo.id.value,
        )
        val control = StreamInfo.getInfo(ServiceList.YouTube, controlVideo)

        println("GATE/po-token calls=" + counting.calls.joinToString(",") + " served=" + counting.served.get())
        println("GATE/health=" + poTokenSession.health.value)
        println("GATE/made-for-kids " + profile(kids))
        println("GATE/control       " + profile(control))

        val maxHeight = (kids.videoStreams + kids.videoOnlyStreams).maxOfOrNull { it.height } ?: 0
        println("GATE/VERDICT maxHeight=" + maxHeight + " (greater than 360 means the cap lifts)")

        assertTrue(
            control.videoOnlyStreams.isNotEmpty(),
            "the control video must offer adaptive streams; if it does not, the network or " +
                "the library is broken and the Made-for-Kids number above means nothing",
        )
        assertTrue(maxHeight > 0, "no video stream at all - extraction failed outright")
    }

    private fun profile(info: StreamInfo): String =
        "muxed=" + info.videoStreams.size +
            " vonly=" + info.videoOnlyStreams.size +
            " audio=" + info.audioStreams.size +
            " hls=" + (!info.hlsUrl.isNullOrEmpty()) +
            " maxH=" + ((info.videoStreams + info.videoOnlyStreams).maxOfOrNull { it.height } ?: 0)
}
