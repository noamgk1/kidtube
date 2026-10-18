package com.noam.kidtube.extractor

import androidx.test.platform.app.InstrumentationRegistry
import com.noam.kidtube.core.Channel
import com.noam.kidtube.core.ChannelId
import com.noam.kidtube.core.IssueResult
import com.noam.kidtube.core.PlaybackSource
import com.noam.kidtube.core.Policy
import com.noam.kidtube.core.Whitelist
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Asserts the contract measured on 2026-10-17. These tests call YouTube, so they are
 * excluded from `check` on purpose: breakage here means YouTube changed, not that this
 * code is wrong. That is the early-warning system the extractor engine needs.
 *
 *     ./gradlew :engine-extractor:connectedDebugAndroidTest -PnetworkTest=true
 */
class NetworkContractTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var session: PoTokenSession
    private lateinit var source: ExtractorContentSource
    private lateinit var resolver: ExtractorPlaybackResolver

    private val kidsChannelUrl = "https://www.youtube.com/@SuperSimpleSongs"
    private val kidsChannelId = ChannelId("UCLsooMJoIpl_7ux2jvdPB-Q")

    @Before
    fun setUp() {
        assumeTrue(
            "network suite is opt-in; pass -PnetworkTest=true",
            InstrumentationRegistry.getArguments().getString("networkTest") == "true",
        )
        session = PoTokenSession(WebViewPoTokenGenerator(context), SystemClockImpl)
        val downloader = OkDownloader()
        ExtractorInit.ensureInitialised(downloader, session)
        source = ExtractorContentSource(session, downloader)
        resolver = ExtractorPlaybackResolver(session, downloader)
    }

    @After
    fun tearDown() {
        if (::session.isInitialized) session.release()
    }

    @Test
    fun aHandleResolvesToTheExpectedChannel() = runBlocking {
        val channel = source.resolveChannel(kidsChannelUrl).getOrThrow()
        assertEquals(kidsChannelId, channel.id)
        assertTrue(channel.feedUrl.contains("channel_id=" + kidsChannelId.value))
        assertTrue(channel.subscriberCount > 1_000_000)
    }

    @Test
    fun aLegacySlashCPathStillFails() = runBlocking {
        // Measured 2026-10-17. If this starts passing, the parser can stop warning.
        assertTrue(source.resolveChannel("https://www.youtube.com/c/kidstv123").isFailure)
    }

    @Test
    fun aPastedVideoUrlIsRefusedAsAChannel() = runBlocking {
        assertTrue(source.resolveChannel("https://youtu.be/dQw4w9WgXcQ").isFailure)
    }

    @Test
    fun pagingReturnsThirtyPerPageAndKeepsGoing() = runBlocking {
        val first = source.listVideos(kidsChannelId, null).getOrThrow()
        assertEquals(30, first.videos.size)
        val next = requireNotNull(first.next) { "a large channel must offer a second page" }
        assertTrue(source.listVideos(kidsChannelId, next).getOrThrow().videos.isNotEmpty())
    }

    @Test
    fun theRssFastPathReturnsRecentUploadsWithoutDurations() = runBlocking {
        val channel = source.resolveChannel(kidsChannelUrl).getOrThrow()
        val latest = source.latestVideos(kidsChannelId, channel.feedUrl).getOrThrow()
        assertTrue(latest.isNotEmpty() && latest.size <= 20)
        assertTrue(latest.all { it.durationSeconds == null }, "the feed carries no duration")
    }

    /**
     * THE CANARY. Freezes the verdict of the PO token gate.
     *
     * Measured, not assumed. The gate ran on 2026-10-18 against NewPipeExtractor
     * v0.26.5 on an Android 15 emulator, with a real BotGuard token served to the
     * client that actually asks for it (android). Made-for-Kids content still came
     * back as a single muxed 360p stream with no separate audio track, so adaptive
     * is false. The control video returned full adaptive 2160p in the same run.
     *
     * If this flips, YouTube changed its Made-for-Kids policy and design spec
     * section 2 is worth revisiting.
     */
    @Test
    fun madeForKidsContentStillReturnsTheShapeTheGateMeasured() = runBlocking {
        val first = source.listVideos(kidsChannelId, null).getOrThrow().videos.first()
        val whitelist = Whitelist(
            channels = mapOf(
                kidsChannelId.value to Channel(kidsChannelId, "Kids", null, "f", 1L),
            ),
        )
        val ticket = assertIs<IssueResult.Allowed>(
            Policy().issue(first.id, kidsChannelId, whitelist, 600),
        ).ticket

        val details = resolver.resolve(ticket).getOrThrow()
        val streams = assertIs<PlaybackSource.Streams>(details.source)
        assertTrue(streams.videoUrl.isNotBlank())

        println(
            "CANARY/made-for-kids adaptive=" + (streams.audioUrl != null) +
                " mime=" + streams.mimeType +
                " health=" + session.health.value,
        )
        assertEquals(
            EXPECTED_ADAPTIVE,
            streams.audioUrl != null,
            "Made-for-Kids stream shape changed since the verification gate",
        )
    }

    private companion object {
        /** Measured by the gate on 2026-10-18; see the canary's KDoc above. */
        const val EXPECTED_ADAPTIVE = false
    }
}
