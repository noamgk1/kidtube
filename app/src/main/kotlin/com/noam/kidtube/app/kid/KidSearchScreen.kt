package com.noam.kidtube.app.kid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.noam.kidtube.core.VideoRef
import com.noam.kidtube.core.VideoSearch

/**
 * Search across the approved library, and only it.
 *
 * This is deliberately not a YouTube search. A query that reached YouTube would put
 * unapproved results in front of the child and make the whitelist decorative, so the
 * haystack here is the same filtered snapshot the home screen draws from.
 */
@Composable
fun KidSearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    videos: List<VideoRef>,
    resumePositions: Map<String, Long>,
    onPlay: (VideoRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    // A blank box shows the whole approved library rather than a hint. A child who
    // cannot type yet still gets somewhere to browse, and this is the only screen that
    // shows everything at once; typing narrows it.
    val results = if (query.isBlank()) videos else VideoSearch.search(videos, query)

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium,
            placeholder = {
                Text("חפשו סרטון", style = MaterialTheme.typography.titleMedium)
            },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "נקה")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .testTag("search-field"),
        )

        when {
            results.isEmpty() && query.isNotBlank() -> SearchHint("לא מצאנו סרטון כזה")
            results.isEmpty() -> EmptyLibraryMessage()
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = CARD_WIDTH),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(results, key = { it.id.value }) { video ->
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

@Composable
private fun SearchHint(text: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
