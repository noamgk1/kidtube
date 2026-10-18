package com.noam.kidtube.app.kid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.noam.kidtube.core.Channel
import com.noam.kidtube.core.VideoRef

enum class KidDestination { HOME, CHANNELS, SEARCH }

/**
 * The child's whole app: a rail of three places, and whichever one is open.
 *
 * Every piece of state is hoisted, so this composable is pure layout — which is what
 * makes it testable without a store, a player or a clock behind it.
 */
@Composable
fun KidShell(
    destination: KidDestination,
    onDestinationChange: (KidDestination) -> Unit,
    snapshot: LibrarySnapshot,
    sections: List<HomeSection>,
    resumePositions: Map<String, Long>,
    openChannel: Channel?,
    onOpenChannel: (Channel?) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    onPlay: (VideoRef) -> Unit,
    onAdminHold: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxSize()) {
        KidRail(
            destination = destination,
            onDestinationChange = {
                // Leaving a channel page when the rail is used, so the back arrow is never
                // the only way out of it.
                onOpenChannel(null)
                onDestinationChange(it)
            },
            onAdminHold = onAdminHold,
        )

        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
            val channel = openChannel
            if (channel != null) {
                KidChannelScreen(
                    channel = channel,
                    videos = snapshot.forChannel(channel),
                    resumePositions = resumePositions,
                    onPlay = onPlay,
                    onBack = { onOpenChannel(null) },
                )
            } else {
                when (destination) {
                    KidDestination.HOME -> KidHomeScreen(
                        sections = sections,
                        resumePositions = resumePositions,
                        onPlay = onPlay,
                        onOpenChannel = { onOpenChannel(it) },
                    )

                    KidDestination.CHANNELS -> KidChannelsScreen(
                        channels = snapshot.channels,
                        onOpenChannel = { onOpenChannel(it) },
                    )

                    KidDestination.SEARCH -> KidSearchScreen(
                        query = query,
                        onQueryChange = onQueryChange,
                        videos = snapshot.videos,
                        resumePositions = resumePositions,
                        onPlay = onPlay,
                    )
                }
            }
        }
    }
}

@Composable
private fun KidRail(
    destination: KidDestination,
    onDestinationChange: (KidDestination) -> Unit,
    onAdminHold: () -> Unit,
) {
    NavigationRail {
        RailDestination(KidDestination.HOME, "בית", Icons.Filled.Home, destination, onDestinationChange)
        RailDestination(
            KidDestination.CHANNELS,
            "ערוצים",
            Icons.Filled.Subscriptions,
            destination,
            onDestinationChange,
        )
        RailDestination(
            KidDestination.SEARCH,
            "חיפוש",
            Icons.Filled.Search,
            destination,
            onDestinationChange,
        )

        Spacer(modifier = Modifier.weight(1f))

        // The admin door. No icon, no label, no ripple: a child poking at the screen finds
        // nothing here, and a parent who knows where to hold gets in. The empty-library
        // message is the only place the app says where it is.
        // A fixed size, deliberately: fillMaxWidth here would stretch the whole rail to
        // the width of the screen and leave the content nothing to lay out in.
        Box(
            modifier = Modifier
                .size(72.dp)
                .testTag("admin-door")
                .adminHold(onTriggered = onAdminHold),
        )
    }
}

@Composable
private fun RailDestination(
    target: KidDestination,
    label: String,
    icon: ImageVector,
    current: KidDestination,
    onSelect: (KidDestination) -> Unit,
) {
    NavigationRailItem(
        selected = current == target,
        onClick = { onSelect(target) },
        icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp)) },
        label = { Text(label) },
    )
}
