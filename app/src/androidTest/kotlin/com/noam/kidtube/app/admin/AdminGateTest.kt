package com.noam.kidtube.app.admin

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.noam.kidtube.core.Clock
import com.noam.kidtube.core.store.CatalogStore
import com.noam.kidtube.core.store.FileStore
import com.noam.kidtube.core.store.SettingsStore
import com.noam.kidtube.core.store.WatchLedger
import com.noam.kidtube.core.store.WhitelistStore
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Design spec section 13: the admin UI must not open without the password. */
class AdminGateTest {

    @get:Rule
    val compose = createComposeRule()

    private object DeviceClock : Clock {
        override fun nowEpochMillis(): Long = System.currentTimeMillis()
        override fun elapsedRealtimeMillis(): Long = android.os.SystemClock.elapsedRealtime()
        override fun zone(): ZoneId = ZoneId.systemDefault()
    }

    private fun authenticator(): AdminAuthenticator {
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
            .resolve("admin-gate-test")
            .apply { deleteRecursively(); mkdirs() }
        val files = FileStore(dir)
        return AdminAuthenticator(
            SettingsStore(files),
            WhitelistStore(files),
            CatalogStore(files),
            WatchLedger(files, DeviceClock),
            iterations = 1_000,
        ).also { it.setPassword("correct-horse".toCharArray()) }
    }

    @Test
    fun aWrongPasswordDoesNotUnlock() {
        var unlocked = false
        compose.setContent {
            AdminGate(authenticator(), onUnlocked = { unlocked = true }, onCancel = {})
        }

        compose.onNodeWithTag("admin-password").performTextInput("nope")
        compose.onNodeWithText("כניסה").performClick()
        compose.waitForIdle()

        assertFalse(unlocked)
        compose.onNodeWithText("סיסמה שגויה").assertExists()
    }

    @Test
    fun theCorrectPasswordUnlocks() {
        var unlocked = false
        compose.setContent {
            AdminGate(authenticator(), onUnlocked = { unlocked = true }, onCancel = {})
        }

        compose.onNodeWithTag("admin-password").performTextInput("correct-horse")
        compose.onNodeWithText("כניסה").performClick()
        compose.waitForIdle()

        assertTrue(unlocked)
    }
}
