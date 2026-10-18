package com.noam.kidtube.app.kid

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull

/** The spec's three seconds. A system long press is about half a second. */
const val ADMIN_HOLD_MS: Long = 3_000

/**
 * The admin door: a deliberate three-second hold with no ripple and no affordance.
 *
 * `combinedClickable`'s onLongClick fires at the system long-press timeout — roughly
 * 500 ms — which is short enough for a child to hit by accident while poking at the
 * screen. This waits for the finger to still be down after [holdMs] instead.
 */
@Composable
fun Modifier.adminHold(holdMs: Long = ADMIN_HOLD_MS, onTriggered: () -> Unit): Modifier =
    composed {
        pointerInput(holdMs, onTriggered) {
            detectTapGestures(
                onPress = {
                    // tryAwaitRelease suspends until the finger lifts or the gesture is
                    // cancelled; timing out means it is still held.
                    val stillHeld = withTimeoutOrNull(holdMs) { tryAwaitRelease() } == null
                    if (stillHeld) onTriggered()
                },
            )
        }
    }
