package com.noam.kidtube.core.store

import com.noam.kidtube.core.Channel
import com.noam.kidtube.core.ChannelId
import com.noam.kidtube.core.FakeClock
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A backup is a file a human can edit, so import must treat it as hostile input. It used
 * to apply the whitelist first and only then touch the catalogs, where a bad channel key
 * threw straight past the Result and out of the caller — leaving the live whitelist
 * already overwritten and the app crashing on the main thread.
 */
class BackupCodecSafetyTest {

    private val root: File = Files.createTempDirectory("backup-safety").toFile()
    private val files = FileStore(root)
    private val whitelistStore = WhitelistStore(files)
    private val catalogStore = CatalogStore(files)
    private val settingsStore = SettingsStore(files)
    private val ledger = WatchLedger(files, FakeClock())
    private val codec = BackupCodec(whitelistStore, catalogStore, settingsStore, ledger)

    @AfterTest
    fun tearDown() { root.deleteRecursively() }

    private fun seedLiveState() {
        whitelistStore.addChannel(Channel(ChannelId("UC_keep"), "Keep", null, "f", 1L))
    }

    private fun backupWithCatalogKey(key: String): String =
        """{"version":1,"whitelist":{"channels":{},"singleVideos":{},"blockedVideoIds":[]},""" +
            """"catalogs":{"$key":[]},"dailyLimitMinutes":60,"engineId":"extractor",""" +
            """"history":[]}"""

    @Test
    fun `a blank catalog key is refused without touching the live whitelist`() {
        seedLiveState()
        val result = codec.import(backupWithCatalogKey(""))

        assertTrue(result.isFailure)
        assertTrue(
            whitelistStore.load().hasChannel(ChannelId("UC_keep")),
            "a refused import must not have already replaced the whitelist",
        )
    }

    @Test
    fun `a catalog key containing a path separator is refused`() {
        seedLiveState()
        val result = codec.import(backupWithCatalogKey("a/b"))

        assertTrue(result.isFailure)
        assertTrue(whitelistStore.load().hasChannel(ChannelId("UC_keep")))
    }

    @Test
    fun `a traversal attempt in a catalog key is refused`() {
        seedLiveState()
        assertTrue(codec.import(backupWithCatalogKey("..")).isFailure)
        assertTrue(whitelistStore.load().hasChannel(ChannelId("UC_keep")))
    }

    @Test
    fun `a refused import writes no catalog files at all`() {
        seedLiveState()
        codec.import(backupWithCatalogKey("a/b"))
        assertEquals(emptyList(), catalogStore.channelIds())
    }

    @Test
    fun `a valid backup still imports`() {
        seedLiveState()
        assertTrue(codec.import(backupWithCatalogKey("UC_fine")).isSuccess)
        assertEquals(listOf(ChannelId("UC_fine")), catalogStore.channelIds())
    }

    @Test
    fun `import never throws, whatever the input`() {
        // The caller treats the Result as the whole story, so nothing may escape it.
        listOf(
            "",
            "null",
            "[]",
            "{}",
            backupWithCatalogKey("a/b"),
            """{"version":1,"catalogs":"not-a-map"}""",
        ).forEach { input ->
            val result = runCatching { codec.import(input) }
            assertTrue(result.isSuccess, "import threw for input: " + input)
        }
    }
}
