package com.noam.kidtube.core.store

import com.noam.kidtube.core.Channel
import com.noam.kidtube.core.ChannelId
import com.noam.kidtube.core.FakeClock
import com.noam.kidtube.core.PasswordHasher
import com.noam.kidtube.core.Settings
import com.noam.kidtube.core.VideoId
import com.noam.kidtube.core.VideoRef
import com.noam.kidtube.core.WatchEntry
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupCodecTest {

    private val sourceRoot: File = Files.createTempDirectory("backup-src").toFile()
    private val targetRoot: File = Files.createTempDirectory("backup-dst").toFile()
    private val clock = FakeClock()

    private fun codecFor(root: File): BackupCodec {
        val files = FileStore(root)
        return BackupCodec(
            WhitelistStore(files),
            CatalogStore(files),
            SettingsStore(files),
            WatchLedger(files, clock),
        )
    }

    @AfterTest
    fun tearDown() {
        sourceRoot.deleteRecursively()
        targetRoot.deleteRecursively()
    }

    private fun seedSource() {
        val files = FileStore(sourceRoot)
        WhitelistStore(files).apply {
            addChannel(Channel(ChannelId("UC_abc"), "Kids", null, "feed", 100L))
            block(VideoId("bad1"))
        }
        CatalogStore(files).save(
            ChannelId("UC_abc"),
            listOf(VideoRef(VideoId("v1"), ChannelId("UC_abc"), "t", null, 60L, 500L)),
        )
        SettingsStore(files).save(Settings(dailyLimitMinutes = 45, engineId = "extractor"))
        WatchLedger(files, clock).append(
            WatchEntry("v1", "UC_abc", "t", clock.nowEpochMillis() / 1_000, 30, true),
        )
    }

    @Test
    fun `a backup round trips whitelist, catalogs, settings and history`() {
        seedSource()
        val exported = codecFor(sourceRoot).export()
        assertTrue(codecFor(targetRoot).import(exported).isSuccess)

        val files = FileStore(targetRoot)
        val whitelist = WhitelistStore(files).load()
        assertTrue(whitelist.hasChannel(ChannelId("UC_abc")))
        assertTrue(whitelist.isBlocked(VideoId("bad1")))
        assertEquals(listOf("v1"), CatalogStore(files).load(ChannelId("UC_abc")).map { it.id.value })
        assertEquals(45, SettingsStore(files).load().dailyLimitMinutes)
        assertEquals(1, WatchLedger(files, clock).all().size)
    }

    @Test
    fun `import does not restore the password, so a forgotten password stays reset`() {
        seedSource()
        val sourceFiles = FileStore(sourceRoot)
        SettingsStore(sourceFiles).save(
            PasswordHasher.install(SettingsStore(sourceFiles).load(), "old".toCharArray(), 1_000),
        )
        val exported = codecFor(sourceRoot).export()

        val targetFiles = FileStore(targetRoot)
        SettingsStore(targetFiles).save(
            PasswordHasher.install(Settings(), "brand-new".toCharArray(), 1_000),
        )

        assertTrue(codecFor(targetRoot).import(exported).isSuccess)

        val after = SettingsStore(targetFiles).load()
        assertTrue(PasswordHasher.verify(after, "brand-new".toCharArray(), 1_000))
        assertFalse(
            PasswordHasher.verify(after, "old".toCharArray(), 1_000),
            "restoring a backup must not reinstate a password the parent has forgotten",
        )
    }

    @Test
    fun `importing malformed json fails and leaves existing data untouched`() {
        val files = FileStore(targetRoot)
        WhitelistStore(files).addChannel(Channel(ChannelId("UC_keep"), "Keep", null, "f", 1L))

        assertTrue(codecFor(targetRoot).import("{ not a backup").isFailure)
        assertTrue(WhitelistStore(files).load().hasChannel(ChannelId("UC_keep")))
    }

    @Test
    fun `importing a newer backup version is refused rather than guessed at`() {
        val future = """{"version":99,"whitelist":{},"catalogs":{},"dailyLimitMinutes":60,"engineId":"x","history":[]}"""
        assertTrue(codecFor(targetRoot).import(future).isFailure)
    }

    @Test
    fun `exporting an empty state produces a backup that imports cleanly`() {
        val exported = codecFor(sourceRoot).export()
        assertTrue(codecFor(targetRoot).import(exported).isSuccess)
    }
}
