package com.noam.kidtube.app.admin

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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

    // A parent who has approved a lot has to scroll to change their mind about any of
    // it, which is the complaint the filter answers.

    @Test
    fun aShortListNeedsNoFilter() {
        compose.setContent { Screen(channels = listOf(channel), singleVideos = listOf(solo)) }

        compose.onNodeWithText("סינון לפי שם").assertDoesNotExist()
    }

    @Test
    fun aLongListOffersOneAndItNarrowsBothKinds() {
        val many = (1..6).map {
            Channel(ChannelId("UC_" + it), "Channel " + it, null, "feed", 1L)
        }
        compose.setContent { Screen(channels = many, singleVideos = listOf(solo)) }

        compose.onNodeWithText("סינון לפי שם").performTextInput("Channel 3")

        // Twice: the filter box now holds the text as well as the row it matched.
        compose.onAllNodesWithText("Channel 3", substring = true).assertCountEquals(2)
        compose.onNodeWithText("Channel 4", substring = true).assertDoesNotExist()
        // The single video is filtered by the same box, not left behind by it.
        compose.onNodeWithText("A Single Song", substring = true).assertDoesNotExist()
    }

    @Test
    fun aFilterThatMatchesNothingSaysSoRatherThanLookingEmpty() {
        val many = (1..6).map {
            Channel(ChannelId("UC_" + it), "Channel " + it, null, "feed", 1L)
        }
        compose.setContent { Screen(channels = many) }

        compose.onNodeWithText("סינון לפי שם").performTextInput("nothing like this")

        compose.onNodeWithText("אין תוכן מאושר בשם הזה").assertIsDisplayed()
    }
}
