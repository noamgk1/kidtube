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

@Composable
fun AdminHistoryScreen(
    rows: List<HistoryRow>,
    onBlock: (VideoId) -> Unit,
    onUnblock: (VideoId) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize().padding(16.dp)) {
        items(rows, key = { it.videoId }) { row ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(row.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            row.plays.toString() + " צפיות · " +
                                (row.totalSeconds / 60) + " דקות סך הכול",
                        )
                    }
                    if (row.blocked) {
                        TextButton(onClick = { onUnblock(VideoId(row.videoId)) }) {
                            Text("בטל חסימה")
                        }
                    } else {
                        TextButton(onClick = { onBlock(VideoId(row.videoId)) }) { Text("חסום") }
                    }
                }
            }
        }
    }
}
