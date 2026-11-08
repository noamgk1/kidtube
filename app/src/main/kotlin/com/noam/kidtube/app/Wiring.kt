package com.noam.kidtube.app

import android.content.Context
import com.noam.kidtube.app.admin.AdminAuthenticator
import com.noam.kidtube.app.sync.CatalogSync
import com.noam.kidtube.app.sync.TitleTranslationPass
import com.noam.kidtube.core.BudgetAccountant
import com.noam.kidtube.core.Clock
import com.noam.kidtube.core.Policy
import com.noam.kidtube.core.TitleTranslator
import com.noam.kidtube.core.store.BackupCodec
import com.noam.kidtube.core.store.BudgetStore
import com.noam.kidtube.core.store.CatalogStore
import com.noam.kidtube.core.store.FileStore
import com.noam.kidtube.core.store.ResumeStore
import com.noam.kidtube.core.store.SettingsStore
import com.noam.kidtube.core.store.SyncStateStore
import com.noam.kidtube.core.store.TranslationStore
import com.noam.kidtube.core.store.WatchLedger
import com.noam.kidtube.core.store.WhitelistStore
import com.noam.kidtube.engine.ContentSource
import com.noam.kidtube.engine.EngineHealth
import com.noam.kidtube.engine.PlaybackResolver
import com.noam.kidtube.engine.PlayerEngine
// The only place in :app that names the extractor engine. Swapping engines is this file.
import com.noam.kidtube.extractor.ExtractorEngine
import com.noam.kidtube.extractor.SystemClockImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
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
    val resumeStore: ResumeStore,
    val translationStore: TranslationStore,
    val titleTranslation: TitleTranslationPass,
    val backupCodec: BackupCodec,
    val budgetAccountant: BudgetAccountant,
    val contentSource: ContentSource,
    val playbackResolver: PlaybackResolver,
    val playerEngine: PlayerEngine,
    /** The same videos through YouTube's embedded player; the parent chooses at any time. */
    val embeddedPlaybackResolver: PlaybackResolver,
    val embeddedPlayerEngine: PlayerEngine,
    /** What the embedded player last settled on, e.g. "hd1080". Empty until it plays. */
    val lastEmbeddedQuality: StateFlow<String>,
    val engineHealth: StateFlow<EngineHealth>,
    val releaseEngine: () -> Unit,
    val catalogSync: CatalogSync,
    /**
     * Outlives any screen. A deep catalog sync runs for up to four minutes on a large
     * channel, so launching it from a composition's scope meant the parent cancelled it
     * simply by leaving the admin screen.
     */
    val appScope: CoroutineScope,
    val corruptions: List<String>,
)

/** The value [com.noam.kidtube.core.Settings.engineId] carries for the embedded player. */
const val EMBEDDED_ENGINE_ID: String = "embedded"

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
        val resumeStore = ResumeStore(files, clock)
        val translationStore = TranslationStore(files)

        // The door ships locked. Only a first run pays for the hash, and paying for it
        // here rather than in the background closes the window in which the admin screen
        // could be reached before the password exists.
        AdminAuthenticator(whitelistStore = whitelistStore, catalogStore = catalogStore,
            ledger = watchLedger, settingsStore = settingsStore).ensureDefaultPassword()

        val embeddedQuality = MutableStateFlow("")

        // Both playback paths are built; the app picks per the setting while it runs.
        // Choosing here instead meant the setting only took effect when the process next
        // died, which on Android is whenever it happens to - so it looked like it did
        // nothing at all.
        val engine = ExtractorEngine.create(
            appContext,
            clock,
            // Lets the embedded path answer from what is already approved instead of
            // making a network request it does not need.
            knownVideo = { videoId ->
                val whitelist = whitelistStore.load()
                whitelist.singleVideos[videoId.value]
                    ?: whitelist.channels.values
                        .firstNotNullOfOrNull { channel ->
                            catalogStore.load(channel.id).firstOrNull { it.id == videoId }
                        }
            },
            onEmbeddedQuality = { embeddedQuality.value = it },
        )

        return Graph(
            clock = clock,
            policy = Policy(),
            whitelistStore = whitelistStore,
            catalogStore = catalogStore,
            syncStateStore = syncStateStore,
            settingsStore = settingsStore,
            budgetStore = budgetStore,
            watchLedger = watchLedger,
            resumeStore = resumeStore,
            translationStore = translationStore,
            titleTranslation = TitleTranslationPass(
                whitelistStore = whitelistStore,
                catalogStore = catalogStore,
                translationStore = translationStore,
                settingsStore = settingsStore,
                // No translator ships: the only on-device one available was ML Kit,
                // whose licence cannot be combined with this project's. See
                // TitleTranslator.None.
                translator = TitleTranslator.None,
            ),
            backupCodec = BackupCodec(whitelistStore, catalogStore, settingsStore, watchLedger),
            budgetAccountant = BudgetAccountant(clock),
            contentSource = engine.contentSource,
            playbackResolver = engine.playbackResolver,
            playerEngine = engine.playerEngine,
            embeddedPlaybackResolver = engine.embeddedPlaybackResolver,
            embeddedPlayerEngine = engine.embeddedPlayerEngine,
            lastEmbeddedQuality = embeddedQuality,
            engineHealth = engine.health,
            releaseEngine = engine::release,
            catalogSync = CatalogSync(
                engine.contentSource,
                whitelistStore,
                catalogStore,
                syncStateStore,
                clock,
            ),
            appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            corruptions = corruptions,
        )
    }
}
