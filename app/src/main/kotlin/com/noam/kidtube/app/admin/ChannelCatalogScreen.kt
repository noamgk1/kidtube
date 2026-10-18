package com.noam.kidtube.app.admin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.noam.kidtube.core.BidiText
import com.noam.kidtube.core.VideoId
import com.noam.kidtube.core.VideoSearch

/**
 * The second of the two places the blocklist is reachable from (the other is the history
 * screen). This is the one a parent uses when vetting a channel they have just approved.
 */
@Composable
fun ChannelCatalogScreen(
    channelTitle: String,
    rows: List<CatalogRow>,
    onBlock: (VideoId) -> Unit,
    onUnblock: (VideoId) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("חזרה") }
            Text(BidiText.isolate(channelTitle), style = MaterialTheme.typography.titleLarge)
        }
        Text(rows.count { it.blocked }.toString() + " חסומים מתוך " + rows.size)

        // A deeply synced channel runs to hundreds of rows, which is more than anyone can
        // scroll through to find the one video they want to block.
        var filter by remember { mutableStateOf("") }
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            singleLine = true,
            label = { Text("סינון לפי שם") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )

        val shown = rows.filter { VideoSearch.matches(it.video.title, filter) }
        if (shown.isEmpty() && filter.isNotBlank()) {
            Text(
                text = "אין סרטון בשם הזה בערוץ",
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 12.dp)) {
            items(shown, key = { it.video.id.value }) { row ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = BidiText.isolate(row.video.title),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (row.blocked) {
                                    MaterialTheme.colorScheme.outline
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                            if (row.blocked) {
                                Text("חסום", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        if (row.blocked) {
                            TextButton(onClick = { onUnblock(row.video.id) }) {
                                Text("בטל חסימה")
                            }
                        } else {
                            TextButton(onClick = { onBlock(row.video.id) }) { Text("חסום") }
                        }
                    }
                }
            }
        }
    }
}
