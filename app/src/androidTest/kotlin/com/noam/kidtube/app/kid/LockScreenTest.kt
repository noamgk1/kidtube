package com.noam.kidtube.app.kid

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

/** Design spec section 13: the lock screen must have no escape a child can find. */
class LockScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theLockScreenOffersNoOrdinaryWayOut() {
        compose.setContent { LockScreen(onAdminLongPress = {}) }

        compose.onNodeWithText("נגמר זמן הצפייה להיום").assertExists()
        // The only interactive element is the hidden admin long-press target, which has
        // no click action of its own.
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
    }
}
