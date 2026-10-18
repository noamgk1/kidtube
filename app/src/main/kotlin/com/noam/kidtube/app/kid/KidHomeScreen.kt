package com.noam.kidtube.app.kid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.noam.kidtube.core.BidiText
import com.noam.kidtube.core.VideoRef

/**
 * The child's home: rows of pictures, the newest first, then one row per approved
 * channel. Everything here came out of an already-filtered snapshot.
 */
@Composable
fun KidHomeScreen(
    sections: List<HomeSection>,
    resumePositions: Map<String, Long>,
    onPlay: (VideoRef) -> Unit,
    onOpenChannel: (com.noam.kidtube.core.Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (sections.isEmpty()) {
        EmptyLibraryMessage(modifier)
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(sections, key = { it.kind.name + (it.channel?.id?.value ?: "") }) { section ->
            Column {
                Text(
                    text = sectionTitle(section),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
                )
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
                    items(section.videos, key = { it.id.value }) { video ->
                        VideoCard(
                            video = video,
                            resumePositionMs = resumePositions[video.id.value] ?: 0L,
                            onClick = { onPlay(video) },
                        )
                    }
                }
            }
        }
    }
}

private fun sectionTitle(section: HomeSection): String = when (section.kind) {
    SectionKind.CONTINUE_WATCHING -> "המשך לצפות"
    SectionKind.NEWEST -> "חדש"
    SectionKind.CHANNEL -> BidiText.isolate(section.channel?.title.orEmpty())
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
            text = "הורים: החזיקו שלוש שניות על הפינה כדי להוסיף ערוצים",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/** Resume positions keyed by video id, for the bars across the thumbnails. */
fun resumeMap(points: List<com.noam.kidtube.core.store.ResumePoint>): Map<String, Long> =
    points.associate { it.videoId to it.positionMs }
