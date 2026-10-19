package com.noam.kidtube.app.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.noam.kidtube.app.kid.formatDuration
import com.noam.kidtube.app.ui.formatCount
import com.noam.kidtube.core.BidiText
import com.noam.kidtube.engine.SearchResult

/** What the parent has ticked, keyed so a channel and a video can never collide. */
fun searchKeyOf(result: SearchResult): String = when (result) {
    is SearchResult.ChannelHit -> "c:" + result.channel.id.value
    is SearchResult.VideoHit -> "v:" + result.video.id.value
}

/**
 * Searching YouTube from the parent screen and ticking what to allow.
 *
 * The one place in the app that asks YouTube for something not yet approved. It is
 * behind the password for that reason, and finding something approves nothing: the
 * parent ticks, then presses the button, and only then does anything reach the child.
 */
@Composable
fun AdminSearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    searching: Boolean,
    results: List<SearchResult>,
    selected: Set<String>,
    onToggle: (SearchResult) -> Unit,
    approvedKeys: Set<String>,
    onApproveSelected: () -> Unit,
    onLoadMore: (() -> Unit)?,
    notice: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                label = { Text("חיפוש ביוטיוב") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.weight(1f).testTag("admin-search-field"),
            )
            Button(
                onClick = onSearch,
                modifier = Modifier.padding(start = 12.dp).testTag("admin-search-button"),
            ) {
                Text("חפש")
            }
        }

        if (notice != null) {
            Text(
                text = notice,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (selected.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Text(selected.size.toString() + " נבחרו")
                Button(
                    onClick = onApproveSelected,
                    modifier = Modifier.padding(start = 12.dp).testTag("approve-selected"),
                ) {
                    Text("אשר את הנבחרים")
                }
            }
        }

        if (searching && results.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(vertical = 12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(results, key = { searchKeyOf(it) }) { result ->
                val key = searchKeyOf(result)
                SearchResultRow(
                    result = result,
                    checked = key in selected,
                    alreadyApproved = key in approvedKeys,
                    onToggle = { onToggle(result) },
                )
            }

            if (onLoadMore != null) {
                item {
                    TextButton(
                        onClick = onLoadMore,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Text(if (searching) "טוען…" else "עוד תוצאות")
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    result: SearchResult,
    checked: Boolean,
    alreadyApproved: Boolean,
    onToggle: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) {
            Checkbox(
                checked = checked || alreadyApproved,
                onCheckedChange = { if (!alreadyApproved) onToggle() },
                enabled = !alreadyApproved,
                modifier = Modifier.testTag("pick-" + searchKeyOf(result)),
            )

            when (result) {
                is SearchResult.ChannelHit -> {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        if (result.channel.avatarUrl != null) {
                            AsyncImage(
                                model = result.channel.avatarUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(
                            text = BidiText.isolate(result.channel.title),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "ערוץ · " + formatCount(result.channel.subscriberCount) +
                                " עוקבים",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            // The difference the parent has to understand before ticking.
                            text = "אישור ערוץ מתיר גם את מה שיעלה בו בעתיד",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                is SearchResult.VideoHit -> {
                    Box(
                        modifier = Modifier
                            .width(140.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        if (result.video.thumbnailUrl != null) {
                            AsyncImage(
                                model = result.video.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(
                            text = BidiText.isolate(result.video.title),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val duration = formatDuration(result.video.durationSeconds)
                        Text(
                            text = BidiText.isolate(result.channelTitle) +
                                (if (duration.isEmpty()) "" else " · " + duration),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (alreadyApproved) {
                Text(
                    text = "כבר מאושר",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
