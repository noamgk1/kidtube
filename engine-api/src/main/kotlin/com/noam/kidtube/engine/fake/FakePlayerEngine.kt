package com.noam.kidtube.engine.fake

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.noam.kidtube.core.ConfirmedTicket
import com.noam.kidtube.engine.PlaybackPhase
import com.noam.kidtube.engine.PlayerEngine
import com.noam.kidtube.engine.PlayerState
import com.noam.kidtube.engine.VideoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Transitions are driven one call at a time rather than chained inside start(), because
 * a StateFlow conflates values set synchronously and a test could never observe the
 * intermediate phase.
 */
class FakeVideoPlayer : VideoPlayer {

    private val mutableState = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = mutableState.asStateFlow()

    var startedWith: ConfirmedTicket? = null
        private set

    override fun start(confirmed: ConfirmedTicket, positionMs: Long) {
        startedWith = confirmed
        mutableState.value = PlayerState(
            phase = PlaybackPhase.BUFFERING,
            positionMs = positionMs,
            durationMs = confirmed.details.durationSeconds * 1_000L,
        )
    }

    override fun play() {
        mutableState.value = mutableState.value.copy(phase = PlaybackPhase.PLAYING)
    }

    override fun pause() {
        mutableState.value = mutableState.value.copy(phase = PlaybackPhase.PAUSED)
    }

    override fun seekTo(positionMs: Long) {
        mutableState.value = mutableState.value.copy(positionMs = positionMs)
    }

    override fun release() {
        mutableState.value = PlayerState()
    }

    /** Test hook: buffering has finished. */
    fun becomeReady() = play()

    /** Test hook: drive the video to its end. */
    fun completeVideo() {
        mutableState.value = mutableState.value.copy(
            phase = PlaybackPhase.ENDED,
            positionMs = mutableState.value.durationMs,
        )
    }

    /** Test hook: drive the video into failure. */
    fun fail(reason: String, recoverable: Boolean = false) {
        mutableState.value = mutableState.value.copy(
            phase = PlaybackPhase.FAILED,
            failure = reason,
            failureIsRecoverable = recoverable,
        )
    }
}

class FakePlayerEngine : PlayerEngine {

    var lastController: FakeVideoPlayer? = null
        private set

    override fun createController(scope: CoroutineScope): VideoPlayer =
        FakeVideoPlayer().also { lastController = it }

    @Composable
    override fun Surface(controller: VideoPlayer, modifier: Modifier) {
        Box(modifier = modifier)
    }
}
