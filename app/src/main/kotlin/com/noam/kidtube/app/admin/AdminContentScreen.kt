package com.noam.kidtube.app.admin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.noam.kidtube.app.ui.formatCount
import com.noam.kidtube.core.BidiText
import com.noam.kidtube.core.Channel
import com.noam.kidtube.core.ChannelId
import com.noam.kidtube.core.SyncState
import com.noam.kidtube.core.VideoId
import com.noam.kidtube.core.VideoSearch
import com.noam.kidtube.core.VideoRef
import com.noam.kidtube.engine.UnsupportedReason

/** Hebrew wording for every refusal the parser can produce. */
fun rejectionMessage(reason: UnsupportedReason): String = when (reason) {
    UnsupportedReason.EMPTY -> "לא הודבק כלום"
    UnsupportedReason.NOT_A_URL -> "זה לא נראה כמו קישור"
    UnsupportedReason.NOT_YOUTUBE -> "הקישור הזה לא מיוטיוב"
    UnsupportedReason.SHORTS -> "Shorts לא נתמכים באפליקציה הזאת"
    UnsupportedReason.LIVE -> "שידור חי לא ניתן לאישור מראש"
    UnsupportedReason.PLAYLIST -> "פלייליסטים עדיין לא נתמכים"
    UnsupportedReason.UNRECOGNISED -> "לא הצלחנו לזהות את הקישור"
}

@Composable
fun AdminContentScreen(
    pasted: String,
    onPastedChange: (String) -> Unit,
    preview: AddPreview?,
    singleVideoTitle: String,
    onSingleVideoTitleChange: (String) -> Unit,
    onCheck: () -> Unit,
    onConfirm: () -> Unit,
    channels: List<Channel>,
    syncStates: Map<String, SyncState>,
    onOpenChannel: (ChannelId) -> Unit,
    onRebuild: (ChannelId) -> Unit,
    onRemove: (ChannelId) -> Unit,
    /** Videos approved on their own, which until now had no way off the list at all. */
    singleVideos: List<VideoRef>,
    onRemoveSingleVideo: (VideoId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = pasted,
                onValueChange = onPastedChange,
                label = { Text("הדבק קישור לערוץ או לסרטון") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onCheck, modifier = Modifier.padding(start = 12.dp)) {
                Text("בדוק")
            }
        }

        when (preview) {
            null -> Unit

            is AddPreview.Rejected -> Text(
                text = rejectionMessage(preview.reason) + " — " + preview.detail,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp),
            )

            is AddPreview.SingleVideo -> Card(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("זהו סרטון בודד, לא ערוץ.", style = MaterialTheme.typography.titleMedium)
                    // Without a name the grid tile and the history row would show the URL.
                    OutlinedTextField(
                        value = singleVideoTitle,
                        onValueChange = onSingleVideoTitleChange,
                        label = { Text("שם לסרטון") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Button(
                        onClick = onConfirm,
                        enabled = singleVideoTitle.isNotBlank(),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text("אשר את הסרטון הזה בלבד")
                    }
                }
            }

            is AddPreview.Channel -> Card(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(preview.channel.title, style = MaterialTheme.typography.titleLarge)
                    Text(formatCount(preview.channel.subscriberCount) + " עוקבים")
                    if (preview.alreadyApproved) {
                        Text("הערוץ הזה כבר מאושר", color = MaterialTheme.colorScheme.error)
                    } else {
                        Button(onClick = onConfirm, modifier = Modifier.padding(top = 8.dp)) {
                            Text("זה הערוץ הנכון — אשר")
                        }
                    }
                }
            }
        }

        // Approving is easy to do a lot of, and a list of fifty channels and a hundred
        // single videos is one a parent has to scroll to change their mind about.
        var filter by remember { mutableStateOf("") }
        if (channels.size + singleVideos.size > FILTER_APPEARS_ABOVE) {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                singleLine = true,
                label = { Text("סינון לפי שם") },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
        }

        val shownChannels = channels.filter { VideoSearch.matches(it.title, filter) }
        val shownSingles = singleVideos.filter { VideoSearch.matches(it.title, filter) }

        if (filter.isNotBlank() && shownChannels.isEmpty() && shownSingles.isEmpty()) {
            Text(
                text = "אין תוכן מאושר בשם הזה",
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 24.dp)) {
            if (shownChannels.isNotEmpty()) {
                item { SectionLabel("ערוצים מאושרים") }
            }

            items(shownChannels, key = { it.id.value }) { channel ->
                val state = syncStates[channel.id.value]
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = BidiText.isolate(channel.title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (state != null && !state.complete) {
                            Text(
                                "בסנכרון: " + state.pagesDone + " עמודים, " +
                                    state.videosStored + " סרטונים",
                            )
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            )
                        } else if (state != null) {
                            Text(state.videosStored.toString() + " סרטונים")
                        }
                        val error = state?.lastError
                        if (error != null) {
                            Text(error, color = MaterialTheme.colorScheme.error)
                        }
                        Row(modifier = Modifier.padding(top = 8.dp)) {
                            TextButton(onClick = { onOpenChannel(channel.id) }) {
                                Text("פתח קטלוג")
                            }
                            TextButton(onClick = { onRebuild(channel.id) }) { Text("בנה מחדש") }
                            TextButton(onClick = { onRemove(channel.id) }) { Text("הסר") }
                        }
                    }
                }
            }

            if (shownSingles.isNotEmpty()) {
                item { SectionLabel("סרטונים בודדים") }
            }

            items(shownSingles, key = { it.id.value }) { video ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = BidiText.isolate(video.title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                // So a parent can tell two similarly named clips apart,
                                // and match one against YouTube if they need to.
                                text = video.id.value,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(
                            onClick = { onRemoveSingleVideo(video.id) },
                            modifier = Modifier.testTag("remove-" + video.id.value),
                        ) {
                            Text("הסר")
                        }
                    }
                }
            }
        }
    }
}

/** Below this, scrolling is quicker than typing. */
private const val FILTER_APPEARS_ABOVE = 5

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}
