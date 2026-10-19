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
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Taking content back off the list.
 *
 * Channels always had a הסר. Videos approved on their own - from a search, or a pasted
 * link - were listed nowhere at all, so once approved they were permanent. That is what
 * these cover.
 */
class AdminContentScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val kids = ChannelId("UC_kids")
    private val channel = Channel(kids, "Kids Songs", null, "feed", 1_000L)
    private val solo = VideoRef(VideoId("solo1"), ChannelId(""), "A Single Song", null, 90L, null)

    @androidx.compose.runtime.Composable
    private fun Screen(
        channels: List<Channel> = emptyList(),
        singleVideos: List<VideoRef> = emptyList(),
        onRemoveSingleVideo: (VideoId) -> Unit = {},
        onRemove: (ChannelId) -> Unit = {},
    ) {
        AdminContentScreen(
            pasted = "",
            onPastedChange = {},
            preview = null,
            singleVideoTitle = "",
            onSingleVideoTitleChange = {},
            onCheck = {},
            onConfirm = {},
            channels = channels,
            syncStates = emptyMap(),
            onOpenChannel = {},
            onRebuild = {},
            onRemove = onRemove,
            singleVideos = singleVideos,
            onRemoveSingleVideo = onRemoveSingleVideo,
        )
    }

    @Test
    fun anIndividuallyApprovedVideoIsListed() {
        compose.setContent { Screen(singleVideos = listOf(solo)) }

        compose.onNodeWithText("A Single Song", substring = true).assertIsDisplayed()
        // The id, so two similarly named clips can be told apart.
        compose.onNodeWithText("solo1", substring = true).assertIsDisplayed()
    }

    @Test
    fun itCanBeRemoved() {
        var removed: VideoId? = null
        compose.setContent {
            Screen(singleVideos = listOf(solo), onRemoveSingleVideo = { removed = it })
        }

        compose.onNodeWithTag("remove-solo1").performClick()

        assertEquals(VideoId("solo1"), removed)
    }

    @Test
    fun channelsAndSingleVideosAreToldApart() {
        compose.setContent { Screen(channels = listOf(channel), singleVideos = listOf(solo)) }

        compose.onNodeWithText("ערוצים מאושרים").assertIsDisplayed()
        compose.onNodeWithText("סרטונים בודדים").assertIsDisplayed()
    }

    @Test
    fun withNothingApprovedNeitherHeadingAppears() {
        // Otherwise a fresh install shows two labels over nothing.
        compose.setContent { Screen() }

        compose.onNodeWithText("ערוצים מאושרים").assertDoesNotExist()
        compose.onNodeWithText("סרטונים בודדים").assertDoesNotExist()
    }
}
