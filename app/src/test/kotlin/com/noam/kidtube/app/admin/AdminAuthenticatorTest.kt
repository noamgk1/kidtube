package com.noam.kidtube.app.admin

import com.noam.kidtube.core.Channel
import com.noam.kidtube.core.ChannelId
import com.noam.kidtube.core.Clock
import com.noam.kidtube.core.VideoId
import com.noam.kidtube.core.VideoRef
import com.noam.kidtube.core.WatchEntry
import com.noam.kidtube.core.store.CatalogStore
import com.noam.kidtube.core.store.FileStore
import com.noam.kidtube.core.store.SettingsStore
import com.noam.kidtube.core.store.WatchLedger
import com.noam.kidtube.core.store.WhitelistStore
import java.io.File
import java.nio.file.Files
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private object ZeroClock : Clock {
    override fun nowEpochMillis(): Long = 1_760_000_000_000L
    override fun elapsedRealtimeMillis(): Long = 0
    override fun zone(): ZoneId = ZoneId.of("Asia/Jerusalem")
}

class AdminAuthenticatorTest {

    private val root: File = Files.createTempDirectory("admin").toFile()
    private val files = FileStore(root)
    private val settingsStore = SettingsStore(files)
    private val whitelistStore = WhitelistStore(files)
    private val catalogStore = CatalogStore(files)
    private val ledger = WatchLedger(files, ZeroClock)

    // Fast iterations keep the suite quick; production uses PasswordHasher.ITERATIONS.
    private val auth = AdminAuthenticator(
        settingsStore, whitelistStore, catalogStore, ledger, iterations = 1_000,
    )

    @AfterTest
    fun tearDown() { root.deleteRecursively() }

    private fun seed() {
        whitelistStore.addChannel(Channel(ChannelId("UC_abc"), "Kids", null, "f", 1L))
        catalogStore.save(
            ChannelId("UC_abc"),
            listOf(VideoRef(VideoId("v1"), ChannelId("UC_abc"), "t", null, 60L, 1L)),
        )
        ledger.append(WatchEntry("v1", "UC_abc", "t", 1L, 10, true))
    }

    @Test
    fun `a fresh install has no password set`() {
        assertFalse(auth.isPasswordSet())
    }

    @Test
    fun `a set password authenticates`() {
        auth.setPassword("sod1234".toCharArray())
        assertTrue(auth.isPasswordSet())
        assertTrue(auth.authenticate("sod1234".toCharArray()))
    }

    @Test
    fun `a wrong password is rejected`() {
        auth.setPassword("sod1234".toCharArray())
        assertFalse(auth.authenticate("wrong".toCharArray()))
    }

    @Test
    fun `nothing authenticates before a password is set`() {
        assertFalse(auth.authenticate("".toCharArray()))
        assertFalse(auth.authenticate("guess".toCharArray()))
    }

    @Test
    fun `changing the password invalidates the old one`() {
        auth.setPassword("first".toCharArray())
        auth.setPassword("second".toCharArray())
        assertFalse(auth.authenticate("first".toCharArray()))
        assertTrue(auth.authenticate("second".toCharArray()))
    }

    @Test
    fun `a destructive reset wipes the content as well as the password`() {
        seed()
        auth.setPassword("forgotten".toCharArray())
        auth.resetDestructively()

        assertFalse(auth.isPasswordSet())
        assertTrue(whitelistStore.load().channels.isEmpty())
        assertTrue(catalogStore.channelIds().isEmpty())
        assertTrue(ledger.all().isEmpty())
    }

    @Test
    fun `the reset is useless to a child because it takes the content with it`() {
        seed()
        auth.setPassword("p".toCharArray())
        auth.resetDestructively()
        assertTrue(
            whitelistStore.load().channels.isEmpty(),
            "if a reset left the videos behind it would be a child's shortcut, not a parent's recovery",
        )
    }

    @Test
    fun `a reset also clears the blocklist, so nothing survives to contradict a restore`() {
        whitelistStore.block(VideoId("bad1"))
        auth.setPassword("p".toCharArray())
        auth.resetDestructively()
        assertTrue(whitelistStore.load().blockedVideoIds.isEmpty())
    }

    @Test
    fun `a new password can be set after a reset`() {
        auth.setPassword("old".toCharArray())
        auth.resetDestructively()
        auth.setPassword("new".toCharArray())
        assertTrue(auth.authenticate("new".toCharArray()))
    }

    @Test
    fun `the daily limit survives a reset so the parent does not reconfigure everything`() {
        settingsStore.save(settingsStore.load().copy(dailyLimitMinutes = 45))
        auth.setPassword("p".toCharArray())
        auth.resetDestructively()
        assertEquals(45, settingsStore.load().dailyLimitMinutes)
    }
}
