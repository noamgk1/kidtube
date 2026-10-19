package com.noam.kidtube.app.kid

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

/** Design spec section 13: the lock screen must have no escape a child can find. */
class LockScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theOnlyWayOffTheLockScreenIsThroughTheParentsPassword() {
        compose.setContent { LockScreen(onOpenAdmin = {}) }

        compose.onNodeWithText("נגמר זמן הצפייה להיום").assertExists()

        // Exactly one: the parent's button, which leads to the password prompt and no
        // further. What this guards against is the lock screen quietly growing a second
        // control - a "just one more" or a dismiss - that a child could use.
        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
        compose.onNodeWithTag("parent-button").assertExists()
    }
}
