package com.noam.kidtube.app

import android.content.Context
import com.noam.kidtube.app.sync.CatalogSync
import com.noam.kidtube.core.BudgetAccountant
import com.noam.kidtube.core.Clock
import com.noam.kidtube.core.Policy
import com.noam.kidtube.core.store.BackupCodec
import com.noam.kidtube.core.store.BudgetStore
import com.noam.kidtube.core.store.CatalogStore
import com.noam.kidtube.core.store.FileStore
import com.noam.kidtube.core.store.SettingsStore
import com.noam.kidtube.core.store.SyncStateStore
import com.noam.kidtube.core.store.WatchLedger
import com.noam.kidtube.core.store.WhitelistStore
import com.noam.kidtube.engine.ContentSource
import com.noam.kidtube.engine.EngineHealth
import com.noam.kidtube.engine.PlaybackResolver
import com.noam.kidtube.engine.PlayerEngine
// The only place in :app that names the extractor engine. Swapping engines is this file.
import com.noam.kidtube.extractor.ExtractorEngine
import com.noam.kidtube.extractor.SystemClockImpl
import kotlinx.coroutines.flow.StateFlow

class Graph(
    val clock: Clock,
    val policy: Policy,
    val whitelistStore: WhitelistStore,
    val catalogStore: CatalogStore,
    val syncStateStore: SyncStateStore,
    val settingsStore: SettingsStore,
    val budgetStore: BudgetStore,
    val watchLedger: WatchLedger,
    val backupCodec: BackupCodec,
    val budgetAccountant: BudgetAccountant,
    val contentSource: ContentSource,
    val playbackResolver: PlaybackResolver,
    val playerEngine: PlayerEngine,
    val engineHealth: StateFlow<EngineHealth>,
    val releaseEngine: () -> Unit,
    val catalogSync: CatalogSync,
    val corruptions: List<String>,
)

object Wiring {

    @Volatile
    private var graph: Graph? = null

    private val corruptions = mutableListOf<String>()

    @Synchronized
    fun graph(context: Context): Graph =
        graph ?: build(context.applicationContext).also { graph = it }

    private fun build(appContext: Context): Graph {
        val clock = SystemClockImpl
        val files = FileStore(appContext.filesDir) { path, cause ->
            corruptions += path + ": " + cause.message
        }

        val whitelistStore = WhitelistStore(files)
        val catalogStore = CatalogStore(files)
        val syncStateStore = SyncStateStore(files)
        val settingsStore = SettingsStore(files)
        val budgetStore = BudgetStore(files)
        val watchLedger = WatchLedger(files, clock)

        val engine = ExtractorEngine.create(appContext, clock)

        return Graph(
            clock = clock,
            policy = Policy(),
            whitelistStore = whitelistStore,
            catalogStore = catalogStore,
            syncStateStore = syncStateStore,
            settingsStore = settingsStore,
            budgetStore = budgetStore,
            watchLedger = watchLedger,
            backupCodec = BackupCodec(whitelistStore, catalogStore, settingsStore, watchLedger),
            budgetAccountant = BudgetAccountant(clock),
            contentSource = engine.contentSource,
            playbackResolver = engine.playbackResolver,
            playerEngine = engine.playerEngine,
            engineHealth = engine.health,
            releaseEngine = engine::release,
            catalogSync = CatalogSync(
                engine.contentSource,
                whitelistStore,
                catalogStore,
                syncStateStore,
                clock,
            ),
            corruptions = corruptions,
        )
    }
}
