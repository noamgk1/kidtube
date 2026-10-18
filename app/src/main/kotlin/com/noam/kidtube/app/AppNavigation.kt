package com.noam.kidtube.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.noam.kidtube.app.admin.AddFlow
import com.noam.kidtube.app.admin.AddPreview
import com.noam.kidtube.app.admin.AdminAuthenticator
import com.noam.kidtube.app.admin.AdminContentScreen
import com.noam.kidtube.app.admin.AdminGate
import com.noam.kidtube.app.admin.AdminHistoryScreen
import com.noam.kidtube.app.admin.AdminStatusScreen
import com.noam.kidtube.app.admin.AdminTimeScreen
import com.noam.kidtube.app.admin.CatalogPresenter
import com.noam.kidtube.app.admin.CatalogRow
import com.noam.kidtube.app.admin.ChannelCatalogScreen
import com.noam.kidtube.app.admin.HistoryPresenter
import com.noam.kidtube.app.admin.HistoryRow
import com.noam.kidtube.app.admin.StatusPresenter
import com.noam.kidtube.app.admin.StatusView
import com.noam.kidtube.app.admin.TimePresenter
import com.noam.kidtube.app.admin.TimeView
import com.noam.kidtube.app.kid.CalmErrorCard
import com.noam.kidtube.app.kid.KidGridLoader
import com.noam.kidtube.app.kid.KidGridModel
import com.noam.kidtube.app.kid.KidGridScreen
import com.noam.kidtube.app.kid.LockScreen
import com.noam.kidtube.app.kid.PlaybackCoordinator
import com.noam.kidtube.app.kid.PlaybackOutcome
import com.noam.kidtube.app.kid.PlayerEvent
import com.noam.kidtube.app.kid.PlayerScreen
import com.noam.kidtube.app.kid.TickResult
import com.noam.kidtube.app.kid.TimeLimitPresenter
import com.noam.kidtube.app.kid.TimeLimitState
import com.noam.kidtube.app.kid.calmMessageFor
import com.noam.kidtube.core.ChannelId
import com.noam.kidtube.core.DenyReason
import com.noam.kidtube.core.SyncState
import com.noam.kidtube.core.Whitelist
import com.noam.kidtube.core.today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppScreen { GRID, PLAYER, ERROR, LOCKED, ADMIN_GATE, ADMIN }

private const val TICK_INTERVAL_MS = 15_000L

@Composable
fun AppNavigation(graph: Graph) {
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Null while the first screen is still being decided, which needs a file read.
    var screen by remember { mutableStateOf<AppScreen?>(null) }
    var selectedChannel by remember { mutableStateOf<ChannelId?>(null) }
    var errorMessage by remember { mutableStateOf("") }
    var refreshKey by remember { mutableStateOf(0) }
    var warning by remember { mutableStateOf<TimeLimitState>(TimeLimitState.Plenty) }

    /** Where cancelling the password prompt returns to. Cancelling must not be an escape. */
    var gateReturnsTo by remember { mutableStateOf(AppScreen.GRID) }

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

    // A day that is already used up must not open onto the grid just because the app was
    // restarted. Decided off the main thread: it reads two files.
    LaunchedEffect(Unit) {
        val remaining = withContext(Dispatchers.IO) { remainingSecondsToday(graph) }
        warning = TimeLimitPresenter.stateFor(remaining)
        screen = if (remaining <= 0) AppScreen.LOCKED else AppScreen.GRID
    }

    // The player's own state is the authority on what is happening. Nothing observed it
    // before, so a finished video sat on screen and the retry-once rule was unreachable.
    LaunchedEffect(controller) {
        controller.state.collect { state ->
            when (val event = coordinator.onPlayerState(state)) {
                PlayerEvent.Nothing -> Unit
                PlayerEvent.ReturnToGrid -> {
                    refreshKey++
                    screen = AppScreen.GRID
                }
                is PlayerEvent.ShowError -> {
                    errorMessage = event.message
                    refreshKey++
                    screen = AppScreen.ERROR
                }
            }
        }
    }

    // Charge the allowance only while a video is on screen. The coordinator counts play
    // time rather than elapsed time, so a pause costs nothing.
    LaunchedEffect(screen) {
        if (screen != AppScreen.PLAYER) return@LaunchedEffect
        while (true) {
            delay(TICK_INTERVAL_MS)
            when (val result = coordinator.tick()) {
                is TickResult.BudgetExhausted -> {
                    coordinator.onEnded(completed = false)
                    warning = TimeLimitState.Locked
                    screen = AppScreen.LOCKED
                    return@LaunchedEffect
                }
                is TickResult.Continue -> {
                    warning = TimeLimitPresenter.stateFor(result.remainingSeconds)
                }
            }
        }
    }

    // Leaving the app must stop playback. Without this the audio carried on outside the
    // app and the allowance kept draining against a screen nobody was looking at.
    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) controller.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.release()
            graph.releaseEngine()
        }
    }

    when (screen) {
        // Deciding. A blank frame for one file read beats a grid that should be locked.
        null -> Box(modifier = Modifier.fillMaxSize())

        AppScreen.GRID -> {
            val model by produceState(
                initialValue = KidGridModel(emptyList(), selectedChannel, emptyList()),
                selectedChannel,
                refreshKey,
            ) {
                value = withContext(Dispatchers.IO) { gridLoader.load(selectedChannel) }
            }
            KidGridScreen(
                model = model,
                onSelectChannel = { selectedChannel = it },
                onPlay = { video ->
                    scope.launch {
                        when (val outcome = coordinator.play(video)) {
                            is PlaybackOutcome.Started -> screen = AppScreen.PLAYER
                            // Another tap is already being handled; say nothing.
                            is PlaybackOutcome.Busy -> Unit
                            is PlaybackOutcome.Refused ->
                                if (outcome.reason == DenyReason.BUDGET_EXHAUSTED) {
                                    warning = TimeLimitState.Locked
                                    screen = AppScreen.LOCKED
                                } else {
                                    errorMessage = calmMessageFor(outcome)
                                    screen = AppScreen.ERROR
                                }
                            else -> {
                                errorMessage = calmMessageFor(outcome)
                                screen = AppScreen.ERROR
                            }
                        }
                    }
                },
                onAdminLongPress = {
                    gateReturnsTo = AppScreen.GRID
                    screen = AppScreen.ADMIN_GATE
                },
            )
        }

        AppScreen.PLAYER -> PlayerScreen(
            engine = graph.playerEngine,
            controller = controller,
            warning = warning,
            onBack = {
                scope.launch {
                    coordinator.onEnded(completed = false)
                    controller.pause()
                    refreshKey++
                    screen = AppScreen.GRID
                }
            },
        )

        AppScreen.ERROR -> CalmErrorCard(errorMessage) { screen = AppScreen.GRID }

        AppScreen.LOCKED -> LockScreen(
            onAdminLongPress = {
                // Returning here on cancel is what makes this a lock rather than advice.
                gateReturnsTo = AppScreen.LOCKED
                screen = AppScreen.ADMIN_GATE
            },
        )

        AppScreen.ADMIN_GATE -> AdminGate(
            authenticator = authenticator,
            onUnlocked = { screen = AppScreen.ADMIN },
            onCancel = { screen = gateReturnsTo },
        )

        AppScreen.ADMIN -> AdminHost(graph, authenticator) {
            scope.launch {
                refreshKey++
                val remaining = withContext(Dispatchers.IO) { remainingSecondsToday(graph) }
                warning = TimeLimitPresenter.stateFor(remaining)
                // A parent who granted bonus minutes expects the child back on the grid.
                screen = if (remaining <= 0) AppScreen.LOCKED else AppScreen.GRID
            }
        }
    }
}

/** Reads the day's remaining allowance. Touches two files, so never call it on main. */
private fun remainingSecondsToday(graph: Graph): Int {
    val budget = graph.budgetAccountant.forToday(
        graph.budgetStore.load(graph.clock.today().toString()),
    )
    val limitSeconds = graph.settingsStore.load().dailyLimitMinutes * 60
    return graph.budgetAccountant.remainingSeconds(budget, limitSeconds)
}

@Composable
private fun AdminHost(graph: Graph, authenticator: AdminAuthenticator, onExit: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var tab by remember { mutableStateOf(0) }
    var pasted by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<AddPreview?>(null) }
    var singleVideoTitle by remember { mutableStateOf("") }
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
    // document pickers. Both run off the main thread: an export serialises the whitelist,
    // every catalog and the whole history.
    var importMessage by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val json = statusPresenter.exportBackup()
                        context.contentResolver.openOutputStream(uri)?.use { stream ->
                            stream.write(json.toByteArray())
                        }
                    }
                }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val outcome = withContext(Dispatchers.IO) {
                    runCatching {
                        val json = context.contentResolver.openInputStream(uri)
                            ?.bufferedReader()
                            ?.use { it.readText() }
                            ?: error("could not read the chosen file")
                        statusPresenter.importBackup(json).getOrThrow()
                    }
                }
                // A silently discarded failure is how a parent loses a restore and never
                // learns why.
                importMessage = outcome.fold(
                    onSuccess = { "הגיבוי יובא" },
                    onFailure = { "הייבוא נכשל: " + (it.message ?: "קובץ לא תקין") },
                )
                version++
            }
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
                val whitelist by produceState(Whitelist(), version) {
                    value = withContext(Dispatchers.IO) { graph.whitelistStore.load() }
                }
                val syncStates by produceState(emptyMap<String, SyncState>(), version) {
                    value = withContext(Dispatchers.IO) {
                        graph.syncStateStore.all().associateBy { it.channelId }
                    }
                }
                val open = openChannel
                if (open != null) {
                    val rows by produceState(emptyList<CatalogRow>(), version, open) {
                        value = withContext(Dispatchers.IO) { catalogPresenter.rows(open) }
                    }
                    ChannelCatalogScreen(
                        channelTitle = whitelist.channels[open.value]?.title.orEmpty(),
                        rows = rows,
                        onBlock = { id ->
                            scope.launch {
                                withContext(Dispatchers.IO) { catalogPresenter.block(id) }
                                version++
                            }
                        },
                        onUnblock = { id ->
                            scope.launch {
                                withContext(Dispatchers.IO) { catalogPresenter.unblock(id) }
                                version++
                            }
                        },
                        onBack = { openChannel = null },
                        modifier = content,
                    )
                } else {
                    AdminContentScreen(
                        pasted = pasted,
                        onPastedChange = { pasted = it },
                        preview = preview,
                        singleVideoTitle = singleVideoTitle,
                        onSingleVideoTitleChange = { singleVideoTitle = it },
                        onCheck = { scope.launch { preview = addFlow.preview(pasted) } },
                        onConfirm = {
                            scope.launch {
                                when (val current = preview) {
                                    is AddPreview.Channel -> {
                                        // Deliberately not awaited on this scope: the deep
                                        // sync must outlive this screen.
                                        val channel = current.channel
                                        graph.appScope.launch { addFlow.confirmChannel(channel) }
                                    }
                                    is AddPreview.SingleVideo ->
                                        withContext(Dispatchers.IO) {
                                            addFlow.confirmSingleVideo(
                                                current.videoId,
                                                singleVideoTitle.ifBlank { current.videoId.value },
                                            )
                                        }
                                    else -> Unit
                                }
                                preview = null
                                pasted = ""
                                singleVideoTitle = ""
                                version++
                            }
                        },
                        channels = whitelist.channels.values.sortedBy { it.title },
                        syncStates = syncStates,
                        onOpenChannel = { openChannel = it },
                        onRebuild = { id ->
                            graph.appScope.launch { graph.catalogSync.deepSync(id) }
                            version++
                        },
                        onRemove = { id ->
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    graph.whitelistStore.removeChannel(id)
                                    graph.catalogStore.delete(id)
                                }
                                version++
                            }
                        },
                        modifier = content,
                    )
                }
            }

            1 -> {
                val view by produceState(TimeView(0, 0, 0), version) {
                    value = withContext(Dispatchers.IO) { timePresenter.view() }
                }
                AdminTimeScreen(
                    view = view,
                    onLimitChange = { minutes ->
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                timePresenter.setDailyLimitMinutes(minutes)
                            }
                            version++
                        }
                    },
                    onGrantBonus = { minutes ->
                        scope.launch {
                            withContext(Dispatchers.IO) { timePresenter.grantBonusMinutes(minutes) }
                            version++
                        }
                    },
                    modifier = content,
                )
            }

            2 -> {
                val rows by produceState(emptyList<HistoryRow>(), version) {
                    value = withContext(Dispatchers.IO) { historyPresenter.rows() }
                }
                AdminHistoryScreen(
                    rows = rows,
                    onBlock = { id ->
                        scope.launch {
                            withContext(Dispatchers.IO) { historyPresenter.block(id) }
                            version++
                        }
                    },
                    onUnblock = { id ->
                        scope.launch {
                            withContext(Dispatchers.IO) { historyPresenter.unblock(id) }
                            version++
                        }
                    },
                    modifier = content,
                )
            }

            3 -> {
                val view by produceState(
                    StatusView(graph.engineHealth.value, emptyList(), emptyList()),
                    version,
                ) {
                    value = withContext(Dispatchers.IO) { statusPresenter.view() }
                }
                AdminStatusScreen(
                    view = view,
                    notice = importMessage,
                    onExport = { exportLauncher.launch("kidtube-backup.json") },
                    onImport = { importLauncher.launch(arrayOf("application/json")) },
                    onResetEverything = {
                        scope.launch {
                            withContext(Dispatchers.IO) { authenticator.resetDestructively() }
                            onExit()
                        }
                    },
                    modifier = content,
                )
            }
        }
    }
}
