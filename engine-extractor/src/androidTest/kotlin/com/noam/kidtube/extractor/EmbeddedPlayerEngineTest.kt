package com.noam.kidtube.extractor

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test
import kotlin.test.assertTrue

/**
 * The embedded player's page loads and YouTube's IFrame API accepts where it is served
 * from.
 *
 * That origin is the whole technical risk of this engine. The API checks who is
 * embedding it, and a page handed to a WebView as a string has no real origin: measured,
 * it answers "error 152" - which reads as "the owner forbids embedding" - even for a
 * video that embeds fine on the open web. The page is therefore served over
 * WebViewAssetLoader's `https://appassets.androidplatform.net`, and this asserts that
 * YouTube is content with it.
 *
 * It deliberately does not assert that video renders: an emulator's software-rendered
 * WebView cannot play YouTube at all, so a test demanding pixels would fail on the
 * machine and pass nowhere. What it does prove is that if the player is black on a real
 * device, the cause is not this.
 */
class EmbeddedPlayerEngineTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theIframeApiLoadsFromTheOriginThePageIsServedFrom() = runBlocking {
        val player = withContext(Dispatchers.Main) { EmbeddedVideoPlayer(context) }

        // "ready" is posted by the page only once YT.Player has been constructed, which
        // only happens if youtube.com served the API to this origin.
        val becameReady = withTimeoutOrNull(60_000) {
            while (true) {
                val settled = withContext(Dispatchers.Main) { player.isReadyForTest() }
                if (settled) return@withTimeoutOrNull true
                kotlinx.coroutines.delay(500)
            }
            @Suppress("UNREACHABLE_CODE")
            false
        }

        withContext(Dispatchers.Main) { player.release() }

        assertTrue(
            becameReady == true,
            "the IFrame API never reported ready, so the embedding origin was refused",
        )
    }
}
