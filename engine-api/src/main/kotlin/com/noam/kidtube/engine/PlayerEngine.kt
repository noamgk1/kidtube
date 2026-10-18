package com.noam.kidtube.engine

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.noam.kidtube.core.ConfirmedTicket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

enum class PlaybackPhase { IDLE, BUFFERING, PLAYING, PAUSED, ENDED, FAILED }

data class PlayerState(
    val phase: PlaybackPhase = PlaybackPhase.IDLE,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val failure: String? = null,
    /** True for an expired stream URL, which a single re-resolve usually fixes. */
    val failureIsRecoverable: Boolean = false,
)

interface VideoPlayer {
    val state: StateFlow<PlayerState>

    /** Accepts only a confirmed ticket: the whitelist promise, enforced by the type system. */
    fun start(confirmed: ConfirmedTicket, positionMs: Long = 0)

    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun release()
}

/**
 * An engine supplies both the controller and its own rendering surface, because the two
 * candidate engines render completely differently: ExoPlayer in an AndroidView versus an
 * embedded player inside a WebView.
 */
interface PlayerEngine {
    fun createController(scope: CoroutineScope): VideoPlayer

    @Composable
    fun Surface(controller: VideoPlayer, modifier: Modifier)
}
