package com.noam.kidtube.app.kid

import com.noam.kidtube.core.Budget
import com.noam.kidtube.core.BudgetAccountant
import com.noam.kidtube.core.Channel
import com.noam.kidtube.core.ChannelId
import com.noam.kidtube.core.Clock
import com.noam.kidtube.core.DenyReason
import com.noam.kidtube.core.Policy
import com.noam.kidtube.core.Settings
import com.noam.kidtube.core.VideoId
import com.noam.kidtube.core.VideoRef
import com.noam.kidtube.core.store.BudgetStore
import com.noam.kidtube.core.store.FileStore
import com.noam.kidtube.core.store.SettingsStore
import com.noam.kidtube.core.store.WatchLedger
import com.noam.kidtube.core.store.WhitelistStore
import com.noam.kidtube.engine.PlaybackPhase
import com.noam.kidtube.engine.PlayerState
import com.noam.kidtube.engine.fake.FakePlaybackResolver
import com.noam.kidtube.engine.fake.FakeVideoPlayer
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class MovableClock : Clock {
    var realtime = 0L
    override fun nowEpochMillis(): Long =
        LocalDate.of(2026, 10, 18).atTime(12, 0).atZone(zone()).toInstant().toEpochMilli()
    override fun elapsedRealtimeMillis(): Long = realtime
    override fun zone(): ZoneId = ZoneId.of("Asia/Jerusalem")
    fun advance(seconds: Long) { realtime += seconds * 1_000L }
}

class PlaybackCoordinatorTest {

    private val root: File = Files.createTempDirectory("playback").toFile()
    private val files = FileStore(root)
    private val clock = MovableClock()
    private val whitelistStore = WhitelistStore(files)
    private val budgetStore = BudgetStore(files)
    private val settingsStore = SettingsStore(files)
    private val ledger = WatchLedger(files, clock)
    private val resolver = FakePlaybackResolver()
    private val player = FakeVideoPlayer()

    private val channelId = ChannelId("UC_abc")
    private val video = VideoRef(VideoId("v1"), channelId, "Song", null, 300L, 900L)

    private fun coordinator() = PlaybackCoordinator(
        policy = Policy(),
        resolver = resolver,
        player = player,
        whitelistStore = whitelistStore,
        budgetStore = budgetStore,
        accountant = BudgetAccountant(clock),
        settingsStore = settingsStore,
        ledger = ledger,
        clock = clock,
    )

    @AfterTest
    fun tearDown() { root.deleteRecursively() }

    /** Watch time is only charged while the player reports PLAYING. */
    private fun playing() = PlayerState(PlaybackPhase.PLAYING, durationMs = 300_000)

    private fun approveChannel(limitMinutes: Int = 60) {
        whitelistStore.addChannel(Channel(channelId, "Kids", null, "feed", 1L))
        settingsStore.save(Settings(dailyLimitMinutes = limitMinutes))
    }

    @Test
    fun `an approved video resolves, confirms and starts`() = runTest {
        approveChannel()
        resolver.scriptDetails(video.id)

        assertIs<PlaybackOutcome.Started>(coordinator().play(video))
        assertEquals(video.id, player.startedWith?.ticket?.videoId)
    }

    @Test
    fun `starting debits fifteen seconds up front`() = runTest {
        approveChannel()
        resolver.scriptDetails(video.id)
        coordinator().play(video)
        assertEquals(15, budgetStore.load("2026-10-18").consumedSeconds)
    }

    @Test
    fun `a blocked video is refused and never reaches the resolver or the player`() = runTest {
        approveChannel()
        whitelistStore.block(video.id)
        resolver.scriptDetails(video.id)

        assertEquals(
            DenyReason.BLOCKED,
            assertIs<PlaybackOutcome.Refused>(coordinator().play(video)).reason,
        )
        assertNull(player.startedWith)
    }

    @Test
    fun `an age restricted video is refused after resolution and never reaches the player`() = runTest {
        approveChannel()
        resolver.scriptDetails(video.id, ageLimit = 18)

        assertEquals(
            DenyReason.AGE_RESTRICTED,
            assertIs<PlaybackOutcome.Refused>(coordinator().play(video)).reason,
        )
        assertNull(player.startedWith)
    }

    @Test
    fun `a live stream inside an approved channel is refused`() = runTest {
        approveChannel()
        resolver.scriptDetails(video.id, isLive = true)
        assertEquals(
            DenyReason.LIVE_STREAM,
            assertIs<PlaybackOutcome.Refused>(coordinator().play(video)).reason,
        )
        assertNull(player.startedWith)
    }

    @Test
    fun `an exhausted budget refuses before resolving`() = runTest {
        approveChannel(limitMinutes = 1)
        budgetStore.save(Budget("2026-10-18", consumedSeconds = 60))
        resolver.scriptDetails(video.id)

        assertEquals(
            DenyReason.BUDGET_EXHAUSTED,
            assertIs<PlaybackOutcome.Refused>(coordinator().play(video)).reason,
        )
    }

    @Test
    fun `a catalogued video that no longer exists comes back as unavailable`() = runTest {
        approveChannel() // the resolver has nothing scripted for v1
        assertIs<PlaybackOutcome.Unavailable>(coordinator().play(video))
        assertNull(player.startedWith)
    }

    @Test
    fun `an expired stream url is re-resolved once and resumes at the same position`() = runTest {
        approveChannel()
        resolver.scriptDetails(video.id)
        val coordinator = coordinator()
        coordinator.play(video)

        player.seekTo(42_000)
        assertIs<PlaybackOutcome.Started>(coordinator.onPlayerFailed("HTTP 403", recoverable = true))
        assertEquals(42_000, player.state.value.positionMs)
    }

    @Test
    fun `a second recoverable failure gives up rather than looping`() = runTest {
        approveChannel()
        resolver.scriptDetails(video.id)
        val coordinator = coordinator()
        coordinator.play(video)

        assertIs<PlaybackOutcome.Started>(coordinator.onPlayerFailed("HTTP 403", true))
        assertIs<PlaybackOutcome.Failed>(coordinator.onPlayerFailed("HTTP 403", true))
    }

    @Test
    fun `an unrecoverable failure is not retried at all`() = runTest {
        approveChannel()
        resolver.scriptDetails(video.id)
        val coordinator = coordinator()
        coordinator.play(video)
        assertIs<PlaybackOutcome.Failed>(coordinator.onPlayerFailed("decoder died", false))
    }

    @Test
    fun `the retry budget resets for the next video`() = runTest {
        approveChannel()
        resolver.scriptDetails(video.id)
        val other = video.copy(id = VideoId("v2"))
        resolver.scriptDetails(other.id)

        val coordinator = coordinator()
        coordinator.play(video)
        coordinator.onPlayerFailed("HTTP 403", true)
        coordinator.onPlayerFailed("HTTP 403", true) // gives up
        coordinator.onEnded(completed = false) // closes the viewing, as onPlayerState does

        coordinator.play(other)
        assertIs<PlaybackOutcome.Started>(coordinator.onPlayerFailed("HTTP 403", true))
    }

    @Test
    fun `ticking charges the shortfall and reports what remains`() = runTest {
        approveChannel()
        resolver.scriptDetails(video.id)
        val coordinator = coordinator()
        coordinator.play(video)
        coordinator.onPlayerState(playing())

        clock.advance(60)
        val tick = assertIs<TickResult.Continue>(coordinator.tick())
        assertEquals(60, budgetStore.load("2026-10-18").consumedSeconds)
        assertEquals(3_540, tick.remainingSeconds)
    }

    @Test
    fun `a tick that exhausts the budget reports it and pauses the player`() = runTest {
        approveChannel(limitMinutes = 1)
        resolver.scriptDetails(video.id)
        val coordinator = coordinator()
        coordinator.play(video)
        coordinator.onPlayerState(playing())

        clock.advance(120)
        assertIs<TickResult.BudgetExhausted>(coordinator.tick())
        assertEquals(PlaybackPhase.PAUSED, player.state.value.phase)
    }

    @Test
    fun `ending writes a history entry carrying the seconds actually watched`() = runTest {
        approveChannel()
        resolver.scriptDetails(video.id)
        val coordinator = coordinator()
        coordinator.play(video)
        coordinator.onPlayerState(playing())
        clock.advance(90)
        coordinator.onEnded(completed = true)

        val entries = ledger.all()
        assertEquals(1, entries.size)
        assertEquals("v1", entries[0].videoId)
        assertEquals(90, entries[0].watchedSeconds)
        assertTrue(entries[0].completed)
    }

    @Test
    fun `stopping early writes history marked incomplete`() = runTest {
        approveChannel()
        resolver.scriptDetails(video.id)
        val coordinator = coordinator()
        coordinator.play(video)
        coordinator.onPlayerState(playing())
        clock.advance(20)
        coordinator.onEnded(completed = false)

        assertFalse(ledger.all()[0].completed)
    }

    @Test
    fun `ending without a session writes nothing rather than crashing`() = runTest {
        coordinator().onEnded(completed = true)
        assertTrue(ledger.all().isEmpty())
    }

    @Test
    fun `ending twice writes one entry, not two`() = runTest {
        approveChannel()
        resolver.scriptDetails(video.id)
        val coordinator = coordinator()
        coordinator.play(video)
        coordinator.onPlayerState(playing())
        clock.advance(30)
        coordinator.onEnded(completed = true)
        coordinator.onEnded(completed = true)
        assertEquals(1, ledger.all().size)
    }
}
