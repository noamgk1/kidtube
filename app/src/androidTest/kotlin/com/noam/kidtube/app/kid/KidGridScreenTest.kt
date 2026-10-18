package com.noam.kidtube.app.kid

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import com.noam.kidtube.core.Channel
import com.noam.kidtube.core.ChannelId
import com.noam.kidtube.core.VideoId
import com.noam.kidtube.core.VideoRef
import org.junit.Rule
import org.junit.Test

/** Design spec section 13: the kid UI must offer no path to a blocked or unapproved video. */
class KidGridScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val kids = ChannelId("UC_kids")

    @Test
    fun onlyTheVideosInTheModelAreReachable() {
        val model = KidGridModel(
            channels = listOf(Channel(kids, "Kids", null, "f", 1L)),
            selectedChannelId = null,
            videos = listOf(
                VideoRef(VideoId("allowed"), kids, "Allowed Video", null, 60L, 1L),
            ),
        )

        compose.setContent {
            KidGridScreen(model, onSelectChannel = {}, onPlay = {}, onAdminLongPress = {})
        }

        compose.onNodeWithText("Allowed Video").assertIsDisplayed()
        compose.onAllNodesWithTag("video-tile").assertCountEquals(1)
    }

    @Test
    fun aModelWithNoVideosRendersNoTiles() {
        compose.setContent {
            KidGridScreen(
                KidGridModel(emptyList(), null, emptyList()),
                onSelectChannel = {},
                onPlay = {},
                onAdminLongPress = {},
            )
        }
        compose.onAllNodesWithTag("video-tile").assertCountEquals(0)
    }
}
