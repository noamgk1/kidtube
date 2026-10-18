package com.noam.kidtube.extractor

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.noam.kidtube.core.ConfirmedTicket
import com.noam.kidtube.engine.PlaybackPhase
import com.noam.kidtube.engine.PlayerEngine
import com.noam.kidtube.engine.PlayerState
import com.noam.kidtube.engine.VideoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val POSITION_TICK_MS = 500L

@OptIn(UnstableApi::class)
internal class ExoVideoPlayer(
    context: Context,
    private val scope: CoroutineScope,
) : VideoPlayer {

    private val mutableState = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = mutableState.asStateFlow()

    private var ticker: Job? = null

    internal val exo: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    mutableState.value = mutableState.value.copy(
                        phase = when (playbackState) {
                            Player.STATE_BUFFERING -> PlaybackPhase.BUFFERING
                            Player.STATE_READY ->
                                if (playWhenReady) PlaybackPhase.PLAYING else PlaybackPhase.PAUSED
                            // No autoplay: ENDED is terminal and nothing follows it.
                            Player.STATE_ENDED -> PlaybackPhase.ENDED
                            else -> mutableState.value.phase
                        },
                        durationMs = duration.coerceAtLeast(0),
                    )
                }

                override fun onPlayerError(error: PlaybackException) {
                    // Stream URLs expire after a few hours, so a long video or a device
                    // waking from sleep lands here, and one re-resolve normally fixes it.
                    val recoverable =
                        error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                            error.errorCode ==
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                            error.errorCode ==
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                    mutableState.value = mutableState.value.copy(
                        phase = PlaybackPhase.FAILED,
                        failure = error.errorCodeName + ": " + error.message,
                        failureIsRecoverable = recoverable,
                    )
                }
            },
        )
    }

    /**
     * ExoPlayer may only be touched from the thread it was built on, and it enforces that
     * with a hard throw rather than a race.
     *
     * The callers here are not all on that thread: the coordinator does its work on an IO
     * dispatcher because every step around playback reads or writes a file, and the
     * budget ticker drives pause() from there too. Marshalling inside the player means no
     * caller has to know, which is the only version of this rule that stays true as
     * callers are added.
     */
    private val handler = Handler(exo.applicationLooper)

    private fun onPlayerThread(block: () -> Unit) {
        if (Looper.myLooper() == exo.applicationLooper) block() else handler.post(block)
    }

    override fun start(confirmed: ConfirmedTicket, positionMs: Long) {
        val plan = MediaPlanner.plan(confirmed.details.source)
        if (plan == null) {
            mutableState.value = PlayerState(
                phase = PlaybackPhase.FAILED,
                failure = "no playable media plan for " + confirmed.details.id.value,
            )
            return
        }

        val factory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent(BOTGUARD_USER_AGENT)

        val source = when (plan) {
            is MediaPlan.Single ->
                ProgressiveMediaSource.Factory(factory)
                    .createMediaSource(MediaItem.fromUri(plan.url))

            is MediaPlan.Merged -> MergingMediaSource(
                ProgressiveMediaSource.Factory(factory)
                    .createMediaSource(MediaItem.fromUri(plan.videoUrl)),
                ProgressiveMediaSource.Factory(factory)
                    .createMediaSource(MediaItem.fromUri(plan.audioUrl)),
            )
        }

        onPlayerThread {
            exo.setMediaSource(source)
            exo.prepare()
            if (positionMs > 0) exo.seekTo(positionMs)
            exo.playWhenReady = true
        }
        startTicker()
    }

    override fun play() = onPlayerThread { exo.playWhenReady = true }

    override fun pause() = onPlayerThread { exo.playWhenReady = false }

    override fun seekTo(positionMs: Long) {
        onPlayerThread { exo.seekTo(positionMs) }
        mutableState.value = mutableState.value.copy(positionMs = positionMs)
    }

    override fun release() {
        ticker?.cancel()
        ticker = null
        onPlayerThread { exo.release() }
        mutableState.value = PlayerState()
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                onPlayerThread {
                    mutableState.value = mutableState.value.copy(
                        positionMs = exo.currentPosition.coerceAtLeast(0),
                    )
                }
                delay(POSITION_TICK_MS)
            }
        }
    }
}

@OptIn(UnstableApi::class)
internal class ExoPlayerEngine(private val appContext: Context) : PlayerEngine {

    override fun createController(scope: CoroutineScope): VideoPlayer =
        ExoVideoPlayer(appContext.applicationContext, scope)

    @Composable
    override fun Surface(controller: VideoPlayer, modifier: Modifier) {
        val exo = (controller as ExoVideoPlayer).exo
        AndroidView(
            modifier = modifier,
            factory = { context ->
                PlayerView(context).apply {
                    player = exo
                    // The app draws its own oversized controls.
                    useController = false
                }
            },
        )
    }
}
