package com.noam.kidtube.app

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noam.kidtube.app.admin.AddFlow
import com.noam.kidtube.app.admin.AddPreview
import com.noam.kidtube.app.admin.AdminAuthenticator
import com.noam.kidtube.app.admin.AdminContentScreen
import com.noam.kidtube.app.admin.AdminGate
import com.noam.kidtube.app.admin.AdminHistoryScreen
import com.noam.kidtube.app.admin.AdminSearchScreen
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
import com.noam.kidtube.app.admin.searchKeyOf
import com.noam.kidtube.app.kid.CalmErrorCard
import com.noam.kidtube.app.kid.HomeScreenModel
import com.noam.kidtube.app.kid.HomeSections
import com.noam.kidtube.app.kid.KidDestination
import com.noam.kidtube.app.kid.KidGridLoader
import com.noam.kidtube.app.kid.KidShell
import com.noam.kidtube.app.kid.LibrarySnapshot
import com.noam.kidtube.app.kid.LockScreen
import com.noam.kidtube.app.kid.PlaybackCoordinator
import com.noam.kidtube.app.kid.PlaybackOutcome
import com.noam.kidtube.app.kid.PlayerEvent
import com.noam.kidtube.app.kid.PlayerScreen
import com.noam.kidtube.app.kid.TickResult
import com.noam.kidtube.app.kid.TimeLimitPresenter
import com.noam.kidtube.app.kid.TimeLimitState
import com.noam.kidtube.app.kid.calmMessageFor
import com.noam.kidtube.app.kid.resumeMap
import com.noam.kidtube.app.ui.AppTheme
import com.noam.kidtube.core.Channel
import com.noam.kidtube.core.ChannelId
import com.noam.kidtube.core.DenyReason
import com.noam.kidtube.core.SyncState
import com.noam.kidtube.core.VideoSort
import com.noam.kidtube.core.Whitelist
import com.noam.kidtube.core.today
import com.noam.kidtube.engine.Cursor
import com.noam.kidtube.engine.SearchKind
import com.noam.kidtube.engine.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppScreen { GRID, PLAYER, ERROR, LOCKED, ADMIN_GATE, ADMIN }

private const val TICK_INTERVAL_MS = 15_000L

/** How often the parent's content screen re-reads sync progress while it is open. */
private const val SYNC_POLL_INTERVAL_MS = 2_000L

/** The last tab is the way out, not a screen. */
private const val EXIT_TAB = 5

/**
 * One read of the library, shared by all three of the child's destinations.
 *
 * Home, channels and search all draw from the same filtered snapshot rather than each
 * loading their own, so a blocked video cannot linger on one screen after another has
 * dropped it.
 */
private data class KidLibrary(
    val snapshot: LibrarySnapshot = LibrarySnapshot(emptyList(), emptyList()),
    val home: HomeScreenModel = HomeScreenModel(emptyList(), emptyList()),
    val resumePositions: Map<String, Long> = emptyMap(),
)

@Composable
fun AppNavigation(graph: Graph) {
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Null while the first screen is still being decided, which needs a file read.
    var screen by remember { mutableStateOf<AppScreen?>(null) }
    var destination by remember { mutableStateOf(KidDestination.HOME) }
    var openChannel by remember { mutableStateOf<Channel?>(null) }
    var query by remember { mutableStateOf("") }

    // The child's own ordering, kept for the session. Not persisted on purpose: it is a
    // way to look through the library, not a setting about it, and a parent who found
    // "shortest" still selected next week would reasonably call that a bug.
    var sort by remember { mutableStateOf(VideoSort.NEWEST) }
    var errorMessage by remember { mutableStateOf("") }
    var refreshKey by remember { mutableStateOf(0) }
    var warning by remember { mutableStateOf<TimeLimitState>(TimeLimitState.Plenty) }

    /** Where cancelling the password prompt returns to. Cancelling must not be an escape. */
    var gateReturnsTo by remember { mutableStateOf(AppScreen.GRID) }

    val gridLoader = remember {
        KidGridLoader(graph.whitelistStore, graph.catalogStore, graph.translationStore)
    }

    // Which playback engine is live. Re-read whenever the parent leaves their screen, so
    // flipping the switch takes effect on the next video rather than on the next time
    // Android happens to kill the process.
    var engineEpoch by remember { mutableStateOf(0) }
    val useEmbedded by produceState(false, engineEpoch) {
        value = withContext(Dispatchers.IO) {
            graph.settingsStore.load().engineId == EMBEDDED_ENGINE_ID
        }
    }

    // Keyed on the choice: changing it discards the player and builds the other one.
    val controller = remember(useEmbedded) {
        if (useEmbedded) {
            graph.embeddedPlayerEngine.createController(scope)
        } else {
            graph.playerEngine.createController(scope)
        }
    }
    val coordinator = remember(useEmbedded, controller) {
        PlaybackCoordinator(
            graph.policy,
            if (useEmbedded) graph.embeddedPlaybackResolver else graph.playbackResolver,
            controller,
            graph.whitelistStore,
            graph.budgetStore,
            graph.budgetAccountant,
            graph.settingsStore,
            graph.watchLedger,
            graph.resumeStore,
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

        // Catches up on anything approved before translation was turned on, and on the
        // tail of a sync that finished after the app was last closed. On its own scope:
        // a library of several hundred titles takes a while, and it must not die because
        // the child opened a video.
        graph.appScope.launch { graph.titleTranslation.run() }
    }

    // One definition of "leave the parent screen", so the tab and the back gesture agree.
    val leaveAdmin: () -> Unit = {
        scope.launch {
            refreshKey++
            engineEpoch++
            val remaining = withContext(Dispatchers.IO) { remainingSecondsToday(graph) }
            warning = TimeLimitPresenter.stateFor(remaining)
            // A parent who granted bonus minutes expects the child back on the grid.
            screen = if (remaining <= 0) AppScreen.LOCKED else AppScreen.GRID
        }
        Unit
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
            // The player only: this also runs when the parent switches engines, and the
            // PO token session below is shared by both and must outlive the swap.
            controller.release()
        }
    }

    DisposableEffect(Unit) {
        onDispose { graph.releaseEngine() }
    }

    // Back must not be a way out of the app. A child pressing it used to land on the
    // Android launcher with the whole tablet in front of them, which undoes the point of
    // the app; inside, it is the ordinary "up" that every screen here already has.
    BackHandler {
        when (screen) {
            AppScreen.PLAYER -> {
                scope.launch {
                    coordinator.onEnded(completed = false)
                    controller.pause()
                    refreshKey++
                    screen = AppScreen.GRID
                }
                Unit
            }

            AppScreen.ERROR -> screen = AppScreen.GRID
            AppScreen.ADMIN_GATE -> screen = gateReturnsTo
            AppScreen.ADMIN -> leaveAdmin()

            // A channel page goes up to the shell; the shell itself is the floor.
            AppScreen.GRID -> if (openChannel != null) openChannel = null

            // The lock screen and the first frame swallow it outright.
            AppScreen.LOCKED, null -> Unit
        }
    }

    // The parent's screens get Material's own type scale; the child's get a larger one.
    AppTheme(forAdmin = screen == AppScreen.ADMIN || screen == AppScreen.ADMIN_GATE) {
        // The bars are hidden, but a transient swipe brings them back over the content;
        // this keeps anything tappable out from under them while they are showing.
        Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            when (screen) {
                // Deciding. A blank frame for one file read beats a grid that should be locked.
                null -> Box(modifier = Modifier.fillMaxSize())

                AppScreen.GRID -> {
                    // Null until the first read finishes. Starting from an empty library
                    // instead flashed "there are no videos yet" on every cold start,
                    // which is a lie told to the child for as long as the files take to
                    // read.
                    val library by produceState<KidLibrary?>(null, refreshKey, sort) {
                        value = withContext(Dispatchers.IO) {
                            val snapshot = gridLoader.snapshot()
                            val resume = graph.resumeStore.continueWatching()
                            KidLibrary(
                                snapshot = snapshot,
                                home = HomeSections.build(snapshot, resume, sort),
                                resumePositions = resumeMap(resume),
                            )
                        }
                    }
                    val loaded = library
                    if (loaded == null) {
                        Box(modifier = Modifier.fillMaxSize())
                        return@Surface
                    }

                    KidShell(
                        destination = destination,
                        onDestinationChange = { destination = it },
                        snapshot = loaded.snapshot,
                        home = loaded.home,
                        sort = sort,
                        onSortChange = { sort = it },
                        resumePositions = loaded.resumePositions,
                        openChannel = openChannel,
                        onOpenChannel = { openChannel = it },
                        query = query,
                        onQueryChange = { query = it },
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
                        onOpenAdmin = {
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
                    onOpenAdmin = {
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

                AppScreen.ADMIN -> AdminHost(graph, authenticator, onExit = leaveAdmin)
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

/**
 * Drives the parent's YouTube search.
 *
 * The approve step deliberately runs on the graph's own scope for channels: confirming
 * one kicks off a deep sync that runs for minutes, and it must not die because the
 * parent moved to another tab.
 */
@Composable
private fun AdminSearchHost(
    graph: Graph,
    addFlow: AddFlow,
    modifier: Modifier,
    onChanged: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(SearchKind.EVERYTHING) }
    var searching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var nextCursor by remember { mutableStateOf<Cursor?>(null) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var notice by remember { mutableStateOf<String?>(null) }
    var approvedVersion by remember { mutableStateOf(0) }

    // What is already on the whitelist, so a hit the parent approved earlier shows as
    // approved instead of inviting them to approve it twice.
    val approvedKeys by produceState(emptySet<String>(), approvedVersion) {
        value = withContext(Dispatchers.IO) {
            val whitelist = graph.whitelistStore.load()
            whitelist.channels.keys.map { "c:" + it }.toSet() +
                whitelist.singleVideos.keys.map { "v:" + it }.toSet()
        }
    }

    // The in-flight search, so a new one replaces it rather than racing it. Changing the
    // filter while results were still coming back let the older request land last and
    // overwrite them: the chips said "channels" and the list was videos.
    var inFlight by remember { mutableStateOf<Job?>(null) }

    fun runSearch(cursor: Cursor?) {
        inFlight?.cancel()
        inFlight = scope.launch {
            searching = true
            notice = null
            val outcome = addFlow.search(submitted, cursor, kind)
            searching = false
            outcome.fold(
                onSuccess = { page ->
                    results = if (cursor == null) page.results else results + page.results
                    nextCursor = page.next
                    if (results.isEmpty()) notice = "לא נמצאו תוצאות"
                },
                onFailure = { cause ->
                    notice = "החיפוש נכשל: " + (cause.message ?: "לא ידוע")
                },
            )
        }
    }

    // One definition of "start a fresh search", so the button, the keyboard and a change
    // of filter all behave the same.
    fun freshSearch(text: String) {
        submitted = text
        selected = emptySet()
        results = emptyList()
        nextCursor = null
        runSearch(null)
    }

    AdminSearchScreen(
        query = query,
        onQueryChange = { query = it },
        onSearch = { freshSearch(query) },
        kind = kind,
        onKindChange = { chosen ->
            kind = chosen
            // Re-run straight away: a filter the parent has to press "search" again to
            // apply looks broken.
            if (submitted.isNotBlank()) freshSearch(submitted)
        },
        searching = searching,
        results = results,
        selected = selected,
        onToggle = { result ->
            val key = searchKeyOf(result)
            selected = if (key in selected) selected - key else selected + key
        },
        approvedKeys = approvedKeys,
        onApproveSelected = {
            val picked = results.filter { searchKeyOf(it) in selected }
            selected = emptySet()
            scope.launch {
                var failures = 0
                picked.forEach { result ->
                    when (result) {
                        is SearchResult.ChannelHit ->
                            addFlow.approveFromSearch(result).fold(
                                onSuccess = { added ->
                                    // The approval is done; the catalog sync that follows
                                    // runs for minutes, so it goes somewhere that outlives
                                    // this screen.
                                    if (added != null) {
                                        graph.appScope.launch {
                                            graph.catalogSync.deepSync(added.id)
                                            graph.titleTranslation.run()
                                        }
                                    }
                                },
                                onFailure = { failures++ },
                            )

                        is SearchResult.VideoHit ->
                            withContext(Dispatchers.IO) { addFlow.approveFromSearch(result) }
                    }
                }
                notice = if (failures > 0) "חלק מהפריטים לא נוספו" else null
                approvedVersion++
                onChanged()
            }
        },
        onLoadMore = nextCursor?.let { cursor -> { runSearch(cursor) } },
        notice = notice,
        modifier = modifier,
    )
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

    // Both read off the settings and translation files, so off the main thread, and
    // refreshed whenever anything on the admin screen changes.
    val translateTitles by produceState(true, version) {
        value = withContext(Dispatchers.IO) { graph.settingsStore.load().translateTitles }
    }
    val translatedCount by produceState(0, version) {
        value = withContext(Dispatchers.IO) { graph.translationStore.all().size }
    }
    val lastEmbeddedQuality by graph.lastEmbeddedQuality.collectAsStateWithLifecycle()
    val embeddedPlayer by produceState(false, version) {
        value = withContext(Dispatchers.IO) {
            graph.settingsStore.load().engineId == EMBEDDED_ENGINE_ID
        }
    }

    // Drives the nudge to stop using the factory password. A file read and a hash, so it
    // is computed off the main thread and refreshed whenever the admin screen changes.
    val usingDefaultPassword by produceState(false, version) {
        value = withContext(Dispatchers.IO) {
            authenticator.authenticate(AdminAuthenticator.DEFAULT_PASSWORD.toCharArray())
        }
    }

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
                listOf("תכנים", "חיפוש", "זמן", "היסטוריה", "הגדרות", "יציאה")
                    .forEachIndexed { index, label ->
                    Tab(
                        selected = tab == index,
                        onClick = { if (index == EXIT_TAB) onExit() else tab = index },
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
                // Polled rather than read once: a deep sync of a large channel runs for
                // minutes in a scope that outlives this screen, and a parent watching a
                // channel they just added would otherwise see nothing happen at all. The
                // loop lives only as long as this tab is on screen.
                val syncStates by produceState(emptyMap<String, SyncState>(), version) {
                    while (true) {
                        value = withContext(Dispatchers.IO) {
                            graph.syncStateStore.all().associateBy { it.channelId }
                        }
                        delay(SYNC_POLL_INTERVAL_MS)
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
                                        graph.appScope.launch {
                                            addFlow.confirmChannel(channel)
                                            graph.titleTranslation.run()
                                        }
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
                            graph.appScope.launch {
                                graph.catalogSync.deepSync(id)
                                graph.titleTranslation.run()
                            }
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
                        singleVideos = whitelist.singleVideos.values.sortedBy { it.title },
                        onRemoveSingleVideo = { id ->
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    graph.whitelistStore.removeSingleVideo(id)
                                }
                                version++
                            }
                        },
                        modifier = content,
                    )
                }
            }

            1 -> AdminSearchHost(graph, addFlow, content) { version++ }

            2 -> {
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

            3 -> {
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

            4 -> {
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
                    onChangePassword = { chosen ->
                        scope.launch {
                            // Hashing is 310k PBKDF2 rounds: never on the main thread.
                            withContext(Dispatchers.IO) {
                                authenticator.setPassword(chosen.toCharArray())
                            }
                            version++
                        }
                    },
                    usingDefaultPassword = usingDefaultPassword,
                    translateTitles = translateTitles,
                    onTranslateTitlesChange = { wanted ->
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                graph.settingsStore.save(
                                    graph.settingsStore.load().copy(translateTitles = wanted),
                                )
                                // Turning it off puts the original titles back at once
                                // rather than leaving stale Hebrew on the child's screen.
                                if (!wanted) graph.translationStore.clear()
                            }
                            // Turning it on backfills what is already approved.
                            if (wanted) graph.appScope.launch { graph.titleTranslation.run() }
                            version++
                        }
                    },
                    translatedCount = translatedCount,
                    embeddedPlayer = embeddedPlayer,
                    lastEmbeddedQuality = lastEmbeddedQuality,
                    onEmbeddedPlayerChange = { wanted ->
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                graph.settingsStore.save(
                                    graph.settingsStore.load().copy(
                                        engineId = if (wanted) EMBEDDED_ENGINE_ID else "extractor",
                                    ),
                                )
                            }
                            version++
                        }
                    },
                    modifier = content,
                )
            }
        }
    }
}
