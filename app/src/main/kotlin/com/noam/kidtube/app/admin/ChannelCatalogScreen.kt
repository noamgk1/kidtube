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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.noam.kidtube.core.VideoId

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
            Text(channelTitle, style = MaterialTheme.typography.titleLarge)
        }
        Text(rows.count { it.blocked }.toString() + " חסומים מתוך " + rows.size)

        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 12.dp)) {
            items(rows, key = { it.video.id.value }) { row ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = row.video.title,
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
