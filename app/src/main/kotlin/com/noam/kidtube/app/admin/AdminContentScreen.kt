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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.noam.kidtube.core.Channel
import com.noam.kidtube.core.ChannelId
import com.noam.kidtube.core.SyncState
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
    onCheck: () -> Unit,
    onConfirm: () -> Unit,
    channels: List<Channel>,
    syncStates: Map<String, SyncState>,
    onOpenChannel: (ChannelId) -> Unit,
    onRebuild: (ChannelId) -> Unit,
    onRemove: (ChannelId) -> Unit,
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
                    Button(onClick = onConfirm, modifier = Modifier.padding(top = 8.dp)) {
                        Text("אשר את הסרטון הזה בלבד")
                    }
                }
            }

            is AddPreview.Channel -> Card(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(preview.channel.title, style = MaterialTheme.typography.titleLarge)
                    Text(preview.channel.subscriberCount.toString() + " עוקבים")
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

        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 24.dp)) {
            items(channels, key = { it.id.value }) { channel ->
                val state = syncStates[channel.id.value]
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(channel.title, style = MaterialTheme.typography.titleMedium)
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
        }
    }
}
