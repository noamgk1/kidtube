package com.noam.kidtube.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.noam.kidtube.app.sync.SyncWorker

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = Wiring.graph(this)
        SyncWorker.schedule(this)
        goImmersive()

        setContent {
            // The whole app is Hebrew, so the layout direction is set once here. The theme
            // is chosen per screen inside AppNavigation: the two sides have different
            // readers.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                AppNavigation(graph)
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // The bars come back whenever the window loses focus, so they have to be hidden
        // again on the way back in.
        if (hasFocus) goImmersive()
    }

    /**
     * Hides the status and navigation bars.
     *
     * Not decoration: with the bars on screen, the top row of thumbnails sits under the
     * status bar, and a child tapping a video pulls down the notification shade instead
     * of playing it - straight out of the app and into the rest of the tablet. Hidden,
     * the bars need a deliberate swipe from the edge to appear at all, and the child gets
     * the whole screen for the thing they actually came for.
     */
    private fun goImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
