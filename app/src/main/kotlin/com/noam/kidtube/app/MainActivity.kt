package com.noam.kidtube.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.noam.kidtube.app.kid.KidGridLoader
import com.noam.kidtube.app.kid.KidGridScreen
import com.noam.kidtube.app.sync.SyncWorker
import com.noam.kidtube.core.ChannelId

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = Wiring.graph(this)
        SyncWorker.schedule(this)

        val loader = KidGridLoader(graph.whitelistStore, graph.catalogStore)

        setContent {
            MaterialTheme {
                // The whole app is Hebrew, so the layout direction is set once here.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface {
                        var selected by remember { mutableStateOf<ChannelId?>(null) }
                        val model = remember(selected) { loader.load(selected) }
                        KidGridScreen(
                            model = model,
                            onSelectChannel = { selected = it },
                            // Playback and the admin door arrive with AppNavigation; until
                            // then browsing and channel filtering are the whole app.
                            onPlay = {},
                            onAdminLongPress = {},
                        )
                    }
                }
            }
        }
    }
}
