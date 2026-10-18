package com.noam.kidtube.app.kid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.noam.kidtube.core.ChannelId
import com.noam.kidtube.core.VideoRef

/**
 * One screen. Navigation is entirely by picture, so it works for a child who cannot
 * read; titles are shown but never required. There is no text input anywhere here.
 */
@Composable
fun KidGridScreen(
    model: KidGridModel,
    onSelectChannel: (ChannelId?) -> Unit,
    onPlay: (VideoRef) -> Unit,
    onAdminLongPress: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            item {
                ChannelAvatar(
                    label = "הכל",
                    imageUrl = null,
                    selected = model.selectedChannelId == null,
                    onClick = { onSelectChannel(null) },
                    // The admin door: a deliberate three-second hold on a fixed spot.
                    onAdminHold = onAdminLongPress,
                )
            }
            items(model.channels, key = { it.id.value }) { channel ->
                ChannelAvatar(
                    label = channel.title,
                    imageUrl = channel.avatarUrl,
                    selected = model.selectedChannelId == channel.id,
                    onClick = { onSelectChannel(channel.id) },
                    onAdminHold = null,
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 220.dp),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(model.videos, key = { it.id.value }) { video ->
                VideoTile(video = video, onClick = { onPlay(video) })
            }
        }
    }
}

@Composable
private fun ChannelAvatar(
    label: String,
    imageUrl: String?,
    selected: Boolean,
    onClick: () -> Unit,
    onAdminHold: (() -> Unit)?,
) {
    val base = Modifier.padding(horizontal = 8.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (onAdminHold == null) {
            base.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
        } else {
            base
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .adminHold(onTriggered = onAdminHold)
        },
    ) {
        Box(
            modifier = Modifier
                .size(if (selected) 96.dp else 84.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun VideoTile(video: VideoRef, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(8.dp)
            .testTag("video-tile")
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (video.thumbnailUrl != null) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            text = video.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
