package com.noam.kidtube.app.kid

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.noam.kidtube.core.VideoSort

/**
 * How the child reorders a list of videos.
 *
 * Shared by the home screen and a channel's page on purpose: the same four words in the
 * same place, so the control is learned once. A child who found "קצרים" on one screen
 * and had to look for it on the other would reasonably conclude it was not there.
 */
@Composable
fun SortChips(
    sort: VideoSort,
    onSortChange: (VideoSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
        listOf(
            VideoSort.NEWEST to "חדשים",
            VideoSort.OLDEST to "ישנים",
            VideoSort.SHORTEST to "קצרים",
            VideoSort.LONGEST to "ארוכים",
        ).forEach { (option, label) ->
            FilterChip(
                selected = sort == option,
                onClick = { onSortChange(option) },
                label = { Text(label, style = MaterialTheme.typography.titleMedium) },
                modifier = Modifier.padding(end = 8.dp).testTag("sort-" + option.name),
            )
        }
    }
}
