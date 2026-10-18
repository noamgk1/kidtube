package com.noam.kidtube.extractor

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Test
import kotlin.test.assertEquals

/**
 * ExoPlayer throws if it is touched from anywhere but the thread it was built on.
 *
 * The coordinator calls the player from an IO dispatcher — everything around a playback
 * reads or writes a file — and the budget ticker drives pause() from there too, so every
 * attempt to play a video crashed the app. No unit test could catch it: the fake player
 * has no thread of its own.
 */
class PlayerThreadAffinityTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Test
    fun theControllerCanBeDrivenFromABackgroundThread() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Built on the main thread, the way the app builds it.
        val player = withContext(Dispatchers.Main) {
            ExoPlayerEngine(context).createController(scope)
        }

        // Driven from IO, the way the coordinator drives it. Before the fix each of
        // these threw IllegalStateException: "Player is accessed on the wrong thread".
        withContext(Dispatchers.IO) {
            player.play()
            player.pause()
            player.seekTo(1_000)
        }

        withContext(Dispatchers.Main) { player.release() }

        assertEquals(0L, player.state.value.positionMs)
    }
}
