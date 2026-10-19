package com.noam.kidtube.app.kid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.noam.kidtube.core.VideoRef
import com.noam.kidtube.core.VideoSort

/**
 * The child's home: one grid of pictures that scrolls straight down.
 *
 * The resume row is the only thing laid out sideways, because it is short and because
 * "the one you were watching" earns its own place at the top. Everything else is the
 * grid, so reaching the end of the library is one gesture repeated rather than a mixture
 * of two.
 */
@Composable
fun KidHomeScreen(
    model: HomeScreenModel,
    resumePositions: Map<String, Long>,
    sort: VideoSort,
    onSortChange: (VideoSort) -> Unit,
    onPlay: (VideoRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (model.isEmpty) {
        EmptyLibraryMessage(modifier)
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = CARD_WIDTH),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 40.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        if (model.continueWatching.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionHeading("המשך לצפות") }
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow {
                    items(model.continueWatching, key = { it.id.value }) { video ->
                        VideoCard(
                            video = video,
                            resumePositionMs = resumePositions[video.id.value] ?: 0L,
                            onClick = { onPlay(video) },
                        )
                    }
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) { SectionHeading("הסרטונים שלי") }
        item(span = { GridItemSpan(maxLineSpan) }) {
            SortChips(sort = sort, onSortChange = onSortChange)
        }

        items(model.all, key = { it.id.value }) { video ->
            VideoCard(
                video = video,
                resumePositionMs = resumePositions[video.id.value] ?: 0L,
                onClick = { onPlay(video) },
            )
        }
    }
}

@Composable
private fun SortChips(sort: VideoSort, onSortChange: (VideoSort) -> Unit) {
    Row(modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
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

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
    )
}

/**
 * What a fresh install looks like. It says what to do next rather than simply being
 * blank, but says it to the parent — the child cannot act on it.
 */
@Composable
fun EmptyLibraryMessage(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "עדיין אין כאן סרטונים",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "הורים: הקישו על \"הורים\" למעלה כדי להוסיף ערוצים",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/** Resume positions keyed by video id, for the bars across the thumbnails. */
fun resumeMap(points: List<com.noam.kidtube.core.store.ResumePoint>): Map<String, Long> =
    points.associate { it.videoId to it.positionMs }
