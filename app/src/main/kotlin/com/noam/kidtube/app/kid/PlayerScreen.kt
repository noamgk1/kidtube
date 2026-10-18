package com.noam.kidtube.app.kid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noam.kidtube.engine.PlaybackPhase
import com.noam.kidtube.engine.PlayerEngine
import com.noam.kidtube.engine.VideoPlayer

/**
 * Full screen, oversized controls, nothing else. No related videos, no share, no
 * "up next" - the video ends and the grid returns. That absence is the product: the
 * automatic chain is the mechanism that builds the rabbit hole, and it is the reason for
 * leaving YouTube in the first place.
 */
@Composable
fun PlayerScreen(
    engine: PlayerEngine,
    controller: VideoPlayer,
    warning: TimeLimitState,
    onBack: () -> Unit,
) {
    val state by controller.state.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        engine.Surface(controller, Modifier.fillMaxSize())

        // The child meets the daily limit exactly twice: here, gently, and then at the
        // lock screen. No clock runs through the whole video.
        if (warning is TimeLimitState.RunningOut) {
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.align(Alignment.TopCenter).padding(24.dp),
            ) {
                Text(
                    text = "עוד " + ((warning.remainingSeconds + 59) / 60) + " דקות",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(24.dp),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(72.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "חזרה",
                    tint = Color.White,
                )
            }

            val playing = state.phase == PlaybackPhase.PLAYING
            IconButton(
                onClick = { if (playing) controller.pause() else controller.play() },
                modifier = Modifier.size(72.dp),
            ) {
                Icon(
                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "השהה" else "נגן",
                    tint = Color.White,
                )
            }

            // Media time reads left-to-right everywhere, so the bar stays LTR in an
            // otherwise right-to-left interface.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Slider(
                    value = state.positionMs.toFloat(),
                    onValueChange = { controller.seekTo(it.toLong()) },
                    valueRange = 0f..state.durationMs.coerceAtLeast(1).toFloat(),
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                )
            }
        }
    }
}
