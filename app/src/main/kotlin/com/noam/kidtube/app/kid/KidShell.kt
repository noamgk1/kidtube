package com.noam.kidtube.app.kid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import com.noam.kidtube.core.Channel
import com.noam.kidtube.core.VideoRef
import com.noam.kidtube.core.VideoSort

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
    home: HomeScreenModel,
    sort: VideoSort,
    onSortChange: (VideoSort) -> Unit,
    resumePositions: Map<String, Long>,
    openChannel: Channel?,
    onOpenChannel: (Channel?) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    onPlay: (VideoRef) -> Unit,
    onOpenAdmin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        KidTopBar(onOpenAdmin = onOpenAdmin)

        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            KidRail(
                destination = destination,
                onDestinationChange = {
                    // Leaving a channel page when the rail is used, so the back arrow is
                    // never the only way out of it.
                    onOpenChannel(null)
                    onDestinationChange(it)
                },
            )

            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                val channel = openChannel
                if (channel != null) {
                    KidChannelScreen(
                        channel = channel,
                        videos = snapshot.forChannel(channel),
                        resumePositions = resumePositions,
                        sort = sort,
                        onSortChange = onSortChange,
                        onPlay = onPlay,
                        onBack = { onOpenChannel(null) },
                    )
                } else {
                    when (destination) {
                        KidDestination.HOME -> KidHomeScreen(
                            model = home,
                            resumePositions = resumePositions,
                            sort = sort,
                            onSortChange = onSortChange,
                            onPlay = onPlay,
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
}

@Composable
private fun KidRail(
    destination: KidDestination,
    onDestinationChange: (KidDestination) -> Unit,
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
