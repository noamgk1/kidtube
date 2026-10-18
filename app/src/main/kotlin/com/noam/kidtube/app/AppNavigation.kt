package com.noam.kidtube.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.noam.kidtube.app.admin.AddFlow
import com.noam.kidtube.app.admin.AddPreview
import com.noam.kidtube.app.admin.AdminAuthenticator
import com.noam.kidtube.app.admin.AdminContentScreen
import com.noam.kidtube.app.admin.AdminGate
import com.noam.kidtube.app.admin.AdminHistoryScreen
import com.noam.kidtube.app.admin.AdminStatusScreen
import com.noam.kidtube.app.admin.AdminTimeScreen
import com.noam.kidtube.app.admin.CatalogPresenter
import com.noam.kidtube.app.admin.ChannelCatalogScreen
import com.noam.kidtube.app.admin.HistoryPresenter
import com.noam.kidtube.app.admin.StatusPresenter
import com.noam.kidtube.app.admin.TimePresenter
import com.noam.kidtube.app.kid.CalmErrorCard
import com.noam.kidtube.app.kid.KidGridLoader
import com.noam.kidtube.app.kid.KidGridScreen
import com.noam.kidtube.app.kid.LockScreen
import com.noam.kidtube.app.kid.PlaybackCoordinator
import com.noam.kidtube.app.kid.PlaybackOutcome
import com.noam.kidtube.app.kid.PlayerScreen
import com.noam.kidtube.app.kid.TickResult
import com.noam.kidtube.app.kid.calmMessageFor
import com.noam.kidtube.core.ChannelId
import com.noam.kidtube.core.DenyReason
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class AppScreen { GRID, PLAYER, ERROR, LOCKED, ADMIN_GATE, ADMIN }

private const val TICK_INTERVAL_MS = 15_000L

@Composable
fun AppNavigation(graph: Graph) {
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf(AppScreen.GRID) }
    var selectedChannel by remember { mutableStateOf<ChannelId?>(null) }
    var errorMessage by remember { mutableStateOf("") }
    var refreshKey by remember { mutableStateOf(0) }

    val gridLoader = remember { KidGridLoader(graph.whitelistStore, graph.catalogStore) }
    val controller = remember { graph.playerEngine.createController(scope) }
    val coordinator = remember {
        PlaybackCoordinator(
            graph.policy,
            graph.playbackResolver,
            controller,
            graph.whitelistStore,
            graph.budgetStore,
            graph.budgetAccountant,
            graph.settingsStore,
            graph.watchLedger,
            graph.clock,
        )
    }
    val authenticator = remember {
        AdminAuthenticator(
            graph.settingsStore,
            graph.whitelistStore,
            graph.catalogStore,
            graph.watchLedger,
        )
    }

    // Budget accounting runs only while a video is on screen, never while browsing.
    LaunchedEffect(screen) {
        if (screen != AppScreen.PLAYER) return@LaunchedEffect
        while (true) {
            delay(TICK_INTERVAL_MS)
            if (coordinator.tick() is TickResult.BudgetExhausted) {
                coordinator.onEnded(completed = false)
                screen = AppScreen.LOCKED
                return@LaunchedEffect
            }
        }
    }

    when (screen) {
        AppScreen.GRID -> {
            val model = remember(selectedChannel, refreshKey) { gridLoader.load(selectedChannel) }
            KidGridScreen(
                model = model,
                onSelectChannel = { selectedChannel = it },
                onPlay = { video ->
                    scope.launch {
                        val outcome = coordinator.play(video)
                        screen = when {
                            outcome is PlaybackOutcome.Started -> AppScreen.PLAYER
                            outcome is PlaybackOutcome.Refused &&
                                outcome.reason == DenyReason.BUDGET_EXHAUSTED -> AppScreen.LOCKED
                            else -> {
                                errorMessage = calmMessageFor(outcome)
                                AppScreen.ERROR
                            }
                        }
                    }
                },
                onAdminLongPress = { screen = AppScreen.ADMIN_GATE },
            )
        }

        AppScreen.PLAYER -> PlayerScreen(
            engine = graph.playerEngine,
            controller = controller,
            onBack = {
                coordinator.onEnded(completed = false)
                controller.pause()
                refreshKey++
                screen = AppScreen.GRID
            },
        )

        AppScreen.ERROR -> CalmErrorCard(errorMessage) { screen = AppScreen.GRID }

        AppScreen.LOCKED -> LockScreen(onAdminLongPress = { screen = AppScreen.ADMIN_GATE })

        AppScreen.ADMIN_GATE -> AdminGate(
            authenticator = authenticator,
            onUnlocked = { screen = AppScreen.ADMIN },
            onCancel = { screen = AppScreen.GRID },
        )

        AppScreen.ADMIN -> AdminHost(graph, authenticator) {
            refreshKey++
            screen = AppScreen.GRID
        }
    }
}

@Composable
private fun AdminHost(graph: Graph, authenticator: AdminAuthenticator, onExit: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var tab by remember { mutableStateOf(0) }
    var pasted by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<AddPreview?>(null) }
    var version by remember { mutableStateOf(0) }
    var openChannel by remember { mutableStateOf<ChannelId?>(null) }

    val addFlow = remember { AddFlow(graph.contentSource, graph.whitelistStore, graph.catalogSync) }
    val catalogPresenter = remember { CatalogPresenter(graph.whitelistStore, graph.catalogStore) }
    val timePresenter = remember {
        TimePresenter(graph.settingsStore, graph.budgetStore, graph.budgetAccountant, graph.clock)
    }
    val historyPresenter = remember { HistoryPresenter(graph.watchLedger, graph.whitelistStore) }
    val statusPresenter = remember {
        StatusPresenter(
            graph.engineHealth,
            graph.syncStateStore,
            graph.backupCodec,
            graph.corruptions,
        )
    }

    // Backup is the whole recovery story for a forgotten password, so it is wired to real
    // document pickers rather than left for later.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(statusPresenter.exportBackup().toByteArray())
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val json = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
            if (json != null) statusPresenter.importBackup(json)
            version++
        }
    }

    Scaffold(
        topBar = {
            TabRow(selectedTabIndex = tab) {
                listOf("תכנים", "זמן", "היסטוריה", "מצב", "יציאה").forEachIndexed { index, label ->
                    Tab(
                        selected = tab == index,
                        onClick = { if (index == 4) onExit() else tab = index },
                        text = { Text(label) },
                    )
                }
            }
        },
    ) { padding ->
        val content = Modifier.padding(padding)
        when (tab) {
            0 -> {
                val whitelist = remember(version) { graph.whitelistStore.load() }
                val open = openChannel
                if (open != null) {
                    ChannelCatalogScreen(
                        channelTitle = whitelist.channels[open.value]?.title.orEmpty(),
                        rows = remember(version, open) { catalogPresenter.rows(open) },
                        onBlock = { catalogPresenter.block(it); version++ },
                        onUnblock = { catalogPresenter.unblock(it); version++ },
                        onBack = { openChannel = null },
                        modifier = content,
                    )
                } else {
                    AdminContentScreen(
                        pasted = pasted,
                        onPastedChange = { pasted = it },
                        preview = preview,
                        onCheck = { scope.launch { preview = addFlow.preview(pasted) } },
                        onConfirm = {
                            scope.launch {
                                when (val current = preview) {
                                    is AddPreview.Channel -> addFlow.confirmChannel(current.channel)
                                    is AddPreview.SingleVideo ->
                                        addFlow.confirmSingleVideo(
                                            current.videoId,
                                            current.canonicalUrl,
                                        )
                                    else -> Unit
                                }
                                preview = null
                                pasted = ""
                                version++
                            }
                        },
                        channels = whitelist.channels.values.sortedBy { it.title },
                        syncStates = remember(version) {
                            graph.syncStateStore.all().associateBy { it.channelId }
                        },
                        onOpenChannel = { openChannel = it },
                        onRebuild = { id -> scope.launch { graph.catalogSync.deepSync(id); version++ } },
                        onRemove = { id ->
                            graph.whitelistStore.removeChannel(id)
                            graph.catalogStore.delete(id)
                            version++
                        },
                        modifier = content,
                    )
                }
            }

            1 -> AdminTimeScreen(
                view = remember(version) { timePresenter.view() },
                onLimitChange = { timePresenter.setDailyLimitMinutes(it); version++ },
                onGrantBonus = { timePresenter.grantBonusMinutes(it); version++ },
                modifier = content,
            )

            2 -> AdminHistoryScreen(
                rows = remember(version) { historyPresenter.rows() },
                onBlock = { historyPresenter.block(it); version++ },
                onUnblock = { historyPresenter.unblock(it); version++ },
                modifier = content,
            )

            3 -> AdminStatusScreen(
                view = remember(version) { statusPresenter.view() },
                onExport = { exportLauncher.launch("kidtube-backup.json") },
                onImport = { importLauncher.launch(arrayOf("application/json")) },
                onResetEverything = { authenticator.resetDestructively(); onExit() },
                modifier = content,
            )
        }
    }
}
