package com.noam.kidtube.app.admin

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.noam.kidtube.core.Channel
import com.noam.kidtube.core.ChannelId
import com.noam.kidtube.core.VideoId
import com.noam.kidtube.core.VideoRef
import com.noam.kidtube.engine.SearchKind
import com.noam.kidtube.engine.SearchResult
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Changing your mind from the same search you approved in.
 *
 * Removal used to live only on the content list, so a parent who had just found
 * something and approved it had to go and find it again somewhere else to undo that.
 */
class AdminSearchScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val channelHit = SearchResult.ChannelHit(
        Channel(ChannelId("UC_kids"), "Kids Songs", null, "feed", 47_000_000L),
    )
    private val videoHit = SearchResult.VideoHit(
        video = VideoRef(VideoId("v1"), ChannelId("UC_kids"), "A Song", null, 90L, null),
        channelTitle = "Kids Songs",
    )

    @androidx.compose.runtime.Composable
    private fun Screen(
        results: List<SearchResult>,
        approvedKeys: Set<String> = emptySet(),
        onRemove: (SearchResult) -> Unit = {},
        onToggle: (SearchResult) -> Unit = {},
    ) {
        AdminSearchScreen(
            query = "kids",
            onQueryChange = {},
            onSearch = {},
            kind = SearchKind.EVERYTHING,
            onKindChange = {},
            searching = false,
            results = results,
            selected = emptySet(),
            onToggle = onToggle,
            approvedKeys = approvedKeys,
            onApproveSelected = {},
            onRemove = onRemove,
            onLoadMore = null,
            notice = null,
        )
    }

    @Test
    fun anApprovedChannelCanBeRemovedFromTheSearch() {
        var removed: SearchResult? = null
        compose.setContent {
            Screen(
                results = listOf(channelHit),
                approvedKeys = setOf("c:UC_kids"),
                onRemove = { removed = it },
            )
        }

        compose.onNodeWithText("כבר מאושר").assertIsDisplayed()
        compose.onNodeWithTag("unapprove-c:UC_kids").performClick()

        assertEquals(channelHit, removed)
    }

    @Test
    fun anApprovedVideoCanBeRemovedFromTheSearch() {
        var removed: SearchResult? = null
        compose.setContent {
            Screen(
                results = listOf(videoHit),
                approvedKeys = setOf("v:v1"),
                onRemove = { removed = it },
            )
        }

        compose.onNodeWithTag("unapprove-v:v1").performClick()

        assertEquals(videoHit, removed)
    }

    @Test
    fun somethingNotYetApprovedOffersNoRemoval() {
        compose.setContent { Screen(results = listOf(channelHit)) }

        compose.onNodeWithTag("unapprove-c:UC_kids").assertDoesNotExist()
    }

    @Test
    fun removingIsNotTheSameGestureAsTicking() {
        // Ticking stages an approval; removing happens at once. Mixing them up would
        // make a parent's tap mean two different things on two rows.
        var toggled: SearchResult? = null
        var removed: SearchResult? = null
        compose.setContent {
            Screen(
                results = listOf(channelHit),
                approvedKeys = setOf("c:UC_kids"),
                onRemove = { removed = it },
                onToggle = { toggled = it },
            )
        }

        compose.onNodeWithTag("unapprove-c:UC_kids").performClick()

        assertEquals(channelHit, removed)
        assertNull(toggled)
    }
}
