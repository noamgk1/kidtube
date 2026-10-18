package com.noam.kidtube.app.admin

import com.noam.kidtube.app.FakeClock
import com.noam.kidtube.core.Channel
import com.noam.kidtube.core.ChannelId
import com.noam.kidtube.core.SyncState
import com.noam.kidtube.core.store.BackupCodec
import com.noam.kidtube.core.store.CatalogStore
import com.noam.kidtube.core.store.FileStore
import com.noam.kidtube.core.store.SettingsStore
import com.noam.kidtube.core.store.SyncStateStore
import com.noam.kidtube.core.store.WatchLedger
import com.noam.kidtube.core.store.WhitelistStore
import com.noam.kidtube.engine.EngineHealth
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StatusPresenterTest {

    private val root: File = Files.createTempDirectory("status").toFile()
    private val files = FileStore(root)
    private val clock = FakeClock()
    private val syncStateStore = SyncStateStore(files)
    private val whitelistStore = WhitelistStore(files)
    private val catalogStore = CatalogStore(files)
    private val settingsStore = SettingsStore(files)
    private val ledger = WatchLedger(files, clock)
    private val health = MutableStateFlow<EngineHealth>(EngineHealth.Healthy)
    private val corruptions = mutableListOf<String>()

    private val presenter = StatusPresenter(
        health,
        syncStateStore,
        BackupCodec(whitelistStore, catalogStore, settingsStore, ledger),
        corruptions,
    )

    @AfterTest
    fun tearDown() { root.deleteRecursively() }

    @Test
    fun `a healthy engine with nothing pending reports clean`() {
        val view = presenter.view()
        assertEquals(EngineHealth.Healthy, view.engineHealth)
        assertTrue(view.incompleteSyncs.isEmpty())
        assertTrue(view.corruptions.isEmpty())
    }

    @Test
    fun `a degraded engine is surfaced with its reason`() {
        health.value = EngineHealth.Degraded("PO token init timed out after 20000ms")
        assertIs<EngineHealth.Degraded>(presenter.view().engineHealth)
    }

    @Test
    fun `an unavailable engine is surfaced`() {
        health.value = EngineHealth.Unavailable("BotGuard broken", 1_760_000_000L)
        assertIs<EngineHealth.Unavailable>(presenter.view().engineHealth)
    }

    @Test
    fun `only incomplete syncs are listed`() {
        syncStateStore.save(SyncState("UC_done", pagesDone = 4, videosStored = 100, complete = true))
        syncStateStore.save(
            SyncState("UC_partial", pagesDone = 2, videosStored = 60, complete = false),
        )

        assertEquals(listOf("UC_partial"), presenter.view().incompleteSyncs.map { it.channelId })
    }

    @Test
    fun `a sync error is surfaced even when the sync is complete`() {
        syncStateStore.save(
            SyncState("UC_err", pagesDone = 3, videosStored = 90, complete = true, lastError = "boom"),
        )
        assertEquals(listOf("UC_err"), presenter.view().incompleteSyncs.map { it.channelId })
    }

    @Test
    fun `corrupt files are surfaced to the parent rather than swallowed`() {
        corruptions += "catalog/UC_abc.json: unexpected token"
        assertEquals(1, presenter.view().corruptions.size)
    }

    @Test
    fun `export produces a backup that imports back`() {
        whitelistStore.addChannel(Channel(ChannelId("UC_abc"), "Kids", null, "f", 1L))
        val exported = presenter.exportBackup()
        assertTrue(exported.contains("UC_abc"))
        assertTrue(presenter.importBackup(exported).isSuccess)
    }

    @Test
    fun `importing rubbish fails without destroying what is there`() {
        whitelistStore.addChannel(Channel(ChannelId("UC_keep"), "Keep", null, "f", 1L))
        assertTrue(presenter.importBackup("not json").isFailure)
        assertTrue(whitelistStore.load().hasChannel(ChannelId("UC_keep")))
    }

    @Test
    fun `the corruption list is a snapshot, not a live alias the UI can mutate`() {
        corruptions += "a.json: bad"
        val view = presenter.view()
        corruptions += "b.json: also bad"
        assertEquals(1, view.corruptions.size)
    }
}
