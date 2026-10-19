package com.noam.kidtube.app.kid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.noam.kidtube.core.BidiText
import com.noam.kidtube.core.Channel
import com.noam.kidtube.core.ChannelId
import com.noam.kidtube.core.VideoId
import com.noam.kidtube.core.VideoRef
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Design spec section 13: the kid UI must offer no path to a blocked or unapproved video. */
class KidShellTest {

    @get:Rule
    val compose = createComposeRule()

    private val kids = ChannelId("UC_kids")
    private val kidsChannel = Channel(kids, "Kids Songs", null, "f", 1L)
    private val allowed = VideoRef(VideoId("allowed"), kids, "Allowed Video", null, 60L, 1L)

    private val snapshot = LibrarySnapshot(listOf(kidsChannel), listOf(allowed))

    /**
     * Drives the shell with its state held outside, the way AppNavigation does, so a tap
     * on the rail actually changes what is on screen.
     */
    @Composable
    private fun Shell(
        onPlay: (VideoRef) -> Unit = {},
        onOpenAdmin: () -> Unit = {},
        library: LibrarySnapshot = snapshot,
    ) {
        var destination by remember { mutableStateOf(KidDestination.HOME) }
        var openChannel by remember { mutableStateOf<Channel?>(null) }
        var query by remember { mutableStateOf("") }
        KidShell(
            destination = destination,
            onDestinationChange = { destination = it },
            snapshot = library,
            sections = HomeSections.build(library, emptyList()),
            resumePositions = emptyMap(),
            openChannel = openChannel,
            onOpenChannel = { openChannel = it },
            query = query,
            onQueryChange = { query = it },
            onPlay = onPlay,
            onOpenAdmin = onOpenAdmin,
        )
    }

    @Test
    fun theHomeScreenShowsOnlyTheVideosInTheSnapshot() {
        compose.setContent { Shell() }

        // Once. With a single approved channel there is no per-channel row, because it
        // would repeat the "חדש" row exactly.
        compose.onAllNodesWithText("Allowed Video", substring = true).assertCountEquals(1)
        compose.onAllNodesWithTag("video-tile").assertCountEquals(1)
    }

    @Test
    fun anEmptyLibraryRendersNoTilesAnywhere() {
        compose.setContent { Shell(library = LibrarySnapshot(emptyList(), emptyList())) }

        compose.onAllNodesWithTag("video-tile").assertCountEquals(0)
        compose.onNodeWithText("עדיין אין כאן סרטונים").assertIsDisplayed()
    }

    @Test
    fun theRailReachesTheChannelsAndTheSearch() {
        compose.setContent { Shell() }

        compose.onNodeWithText("ערוצים").performClick()
        compose.onNodeWithText("Kids Songs", substring = true).assertIsDisplayed()

        compose.onNodeWithText("חיפוש").performClick()
        compose.onNodeWithTag("search-field").assertIsDisplayed()
    }

    /** The whole point of the search: it is a filter over the snapshot, not a YouTube query. */
    @Test
    fun searchFindsOnlyApprovedVideos() {
        compose.setContent { Shell() }

        compose.onNodeWithText("חיפוש").performClick()
        compose.onNodeWithTag("search-field").performTextInput("Allowed")

        compose.onAllNodesWithTag("video-tile").assertCountEquals(1)
        compose.onNodeWithText("Allowed Video", substring = true).assertIsDisplayed()
    }

    /** A child who cannot type yet still needs somewhere to go from the search tab. */
    @Test
    fun anEmptySearchBoxShowsTheWholeApprovedLibrary() {
        compose.setContent { Shell() }

        compose.onNodeWithText("חיפוש").performClick()

        compose.onAllNodesWithTag("video-tile").assertCountEquals(1)
    }

    @Test
    fun aSearchThatMatchesNothingOffersNothing() {
        compose.setContent { Shell() }

        compose.onNodeWithText("חיפוש").performClick()
        compose.onNodeWithTag("search-field").performTextInput("Peppa")

        compose.onAllNodesWithTag("video-tile").assertCountEquals(0)
        compose.onNodeWithText("לא מצאנו סרטון כזה").assertIsDisplayed()
    }

    @Test
    fun tappingAVideoPlaysThatVideo() {
        var played: VideoRef? = null
        compose.setContent { Shell(onPlay = { played = it }) }

        compose.onAllNodesWithTag("video-tile")[0].performClick()

        assertEquals(allowed.id, played?.id)
    }

    /**
     * The isolation that keeps an English title from being reordered by the bidi
     * algorithm when it sits inside the Hebrew layout.
     */
    @Test
    fun anEnglishTitleIsRenderedAsItsOwnDirectionalRun() {
        compose.setContent { Shell() }

        compose.onNodeWithText(
            BidiText.FIRST_STRONG_ISOLATE + "Allowed Video" + BidiText.POP_DIRECTIONAL_ISOLATE,
        ).assertIsDisplayed()
    }

    /**
     * The parent's way in is a plain button now. What guards the parent screens is the
     * password behind it, not the button being hard to find - which it was, for the
     * parent as much as for the child.
     */
    @Test
    fun theParentButtonAsksToOpenTheParentScreen() {
        var opened = false
        compose.setContent { Shell(onOpenAdmin = { opened = true }) }

        compose.onNodeWithTag("parent-button").performClick()

        assertTrue(opened)
    }

    @Test
    fun aChannelPageShowsThatChannelsVideos() {
        compose.setContent { Shell() }

        compose.onNodeWithText("ערוצים").performClick()
        compose.onNodeWithText("Kids Songs", substring = true).performClick()

        compose.onAllNodesWithTag("video-tile").assertCountEquals(1)
        compose.onNodeWithText("Allowed Video", substring = true).assertIsDisplayed()
    }
}
