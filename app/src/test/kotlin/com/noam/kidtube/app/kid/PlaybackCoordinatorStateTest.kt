package com.noam.kidtube.app.kid

import com.noam.kidtube.app.FakeClock
import com.noam.kidtube.core.BudgetAccountant
import com.noam.kidtube.core.Channel
import com.noam.kidtube.core.ChannelId
import com.noam.kidtube.core.Policy
import com.noam.kidtube.core.Settings
import com.noam.kidtube.core.VideoId
import com.noam.kidtube.core.VideoRef
import com.noam.kidtube.core.store.BudgetStore
import com.noam.kidtube.core.store.FileStore
import com.noam.kidtube.core.store.ResumeStore
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
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * How the coordinator reacts to the player's own state, and how watch time is counted.
 *
 * Nothing observed PlaybackPhase.ENDED or FAILED before these tests existed: a finished
 * video sat on screen draining the day's allowance, the retry-once rule was unreachable
 * dead code, and a paused video was charged as if it were playing.
 */
class PlaybackCoordinatorStateTest {

    private val root: File = Files.createTempDirectory("playback-state").toFile()
    private val files = FileStore(root)
    private val clock = FakeClock()
    private val whitelistStore = WhitelistStore(files)
    private val budgetStore = BudgetStore(files)
    private val settingsStore = SettingsStore(files)
    private val ledger = WatchLedger(files, clock)
    private val resumeStore = ResumeStore(files, clock)
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
        resumeStore = resumeStore,
        clock = clock,
    )

    @AfterTest
    fun tearDown() { root.deleteRecursively() }

    private fun approveChannel(limitMinutes: Int = 60) {
        whitelistStore.addChannel(Channel(channelId, "Kids", null, "feed", 1L))
        settingsStore.save(Settings(dailyLimitMinutes = limitMinutes))
    }

    private fun consumed() = budgetStore.load("2026-10-18").consumedSeconds

    private fun playing(positionMs: Long = 0) =
        PlayerState(PlaybackPhase.PLAYING, positionMs = positionMs, durationMs = 300_000)

    private fun ended() =
        PlayerState(PlaybackPhase.ENDED, positionMs = 300_000, durationMs = 300_000)

    private fun failed(recoverable: Boolean, positionMs: Long = 0) =
        PlayerState(
            PlaybackPhase.FAILED,
            positionMs = positionMs,
            failure = "boom",
            failureIsRecoverable = recoverable,
        )

    // ---- terminal states -------------------------------------------------------

    @Test
    fun `a finished video returns to the grid and is recorded as completed`() = runTest {
        approveChannel()
        resolver.scriptDetails(video.id)
        val coordinator = coordinator()
        coordinator.play(video)

        coordinator.onPlayerState(playing())
        clock.advanceRealtime(120)
        assertEquals(PlayerEvent.ReturnToGrid, coordinator.onPlayerState(ended()))

        val entry = ledger.all().single()
        assertTrue(entry.completed, "a video that reached its end is a completed viewing")
        assertEquals(120, entry.watchedSeconds)
    }

    @Test
    fun `a finished video stops consuming the allowance`() = runTest {
        approveChannel()
        resolver.scriptDetails(video.id)
        val coordinator = coordinator()
        coordinator.play(video)
        coordinator.onPlayerState(playing())
        clock.advanceRealtime(60)
        coordinator.onPlayerState(ended())

        val afterEnd = consumed()
        clock.advanceRealtime(600)
        coordinator.tick()
        assertEquals(afterEnd, consumed())
    }

    @Test
    fun `a recoverable mid-playback failure re-resolves silently`() = runTest {
        approveChannel()
        resolver.scriptDetails(video.id)
        val coordinator = coordinator()
        coordinator.play(video)
        coordinator.onPlayerState(playing(positionMs = 42_000))

        // The failure snapshot carries the position the player had reached.
        assertEquals(
            PlayerEvent.Nothing,
            coordinator.onPlayerState(failed(recoverable = true, positionMs = 42_000)),
        )
        assertEquals(42_000, player.state.value.positionMs, "it resumes where it stopped")
    }

    @Test
    fun `a second recoverable failure surfaces the calm card instead of looping`() = runTest {
        approveChannel()
        resolver.scriptDetails(video.id)
        val coordinator = coordinator()
        coordinator.play(video)
        coordinator.onPlayerState(playing())

        coordinator.onPlayerState(failed(recoverable = true))
        assertIs<PlayerEvent.ShowError>(coordinator.onPlayerState(failed(recoverable = true)))
    }

    @Test
    fun `an unrecoverable failure surfaces the calm card at once`() = runTest {
        approveChannel()
        resolver.scriptDetails(video.id)
        val coordinator = coordinator()
        coordinator.play(video)
        assertIs<PlayerEvent.ShowError>(coordinator.onPlayerState(failed(recoverable = false)))
    }

    @Test
    fun `a failure that gives up records the viewing rather than losing it`() = runTest {
        approveChannel()
        resolver.scriptDetails(video.id)
        val coordinator = coordinator()
        coordinator.play(video)
        coordinator.onPlayerState(playing())
        clock.advanceRealtime(40)
        coordinator.onPlayerState(failed(recoverable = false))

        assertEquals(40, ledger.all().single().watchedSeconds)
    }

    @Test
    fun `an ordinary playing state asks the UI to do nothing`() = runTest {
        approveChannel()
        resolver.scriptDetails(video.id)
        val coordinator = coordinator()
        coordinator.play(video)
        assertEquals(PlayerEvent.Nothing, coordinator.onPlayerState(playing()))
    }

    @Test
    fun `player state arriving with no session is ignored`() = runTest {
        assertEquals(PlayerEvent.Nothing, coordinator().onPlayerState(ended()))
        assertTrue(ledger.all().isEmpty())
    }

    // ---- paused time is not watch time -----------------------------------------

    @Test
    fun `a paused video does not consume the allowance`() = runTest {
        approveChannel()
        resolver.scriptDetails(video.id)
        val coordinator = coordinator()
        coordinator.play(video)

        coordinator.onPlayerState(playing())
        clock.advanceRealtime(30)
        coordinator.onPlayerState(PlayerState(PlaybackPhase.PAUSED))
        clock.advanceRealtime(600) // the child wandered off with the video paused
        coordinator.tick()

        assertEquals(30, consumed())
    }

    @Test
    fun `resuming after a pause carries on from the accumulated total`() = runTest {
        approveChannel()
        resolver.scriptDetails(video.id)
        val coordinator = coordinator()
        coordinator.play(video)

        coordinator.onPlayerState(playing())
        clock.advanceRealtime(30)
        coordinator.onPlayerState(PlayerState(PlaybackPhase.PAUSED))
        clock.advanceRealtime(600)
        coordinator.onPlayerState(playing())
        clock.advanceRealtime(20)
        coordinator.tick()

        assertEquals(50, consumed())
    }

    @Test
    fun `buffering is not watch time either`() = runTest {
        approveChannel()
        resolver.scriptDetails(video.id)
        val coordinator = coordinator()
        coordinator.play(video)

        coordinator.onPlayerState(PlayerState(PlaybackPhase.BUFFERING))
        clock.advanceRealtime(45)
        coordinator.tick()
        assertEquals(15, consumed(), "only the up-front debit")
    }

    // ---- the final reconciliation ----------------------------------------------

    @Test
    fun `a short viewing is charged in full when it ends, not left at the up-front debit`() = runTest {
        approveChannel()
        resolver.scriptDetails(video.id)
        val coordinator = coordinator()
        coordinator.play(video)

        coordinator.onPlayerState(playing())
        // Under the 15s tick cadence, so only the final reconciliation can charge it.
        clock.advanceRealtime(29)
        coordinator.onEnded(completed = false)

        assertEquals(29, consumed())
    }

    @Test
    fun `ten short viewings are charged for what was actually watched`() = runTest {
        approveChannel()
        val coordinator = coordinator()
        repeat(10) { index ->
            val each = video.copy(id = VideoId("v" + index))
            resolver.scriptDetails(each.id)
            coordinator.play(each)
            coordinator.onPlayerState(playing())
            clock.advanceRealtime(29)
            coordinator.onEnded(completed = false)
        }
        assertEquals(290, consumed(), "150 would be the up-front debits alone")
    }

    // ---- repeated taps ----------------------------------------------------------

    @Test
    fun `a second play while one is in flight is refused rather than double-charging`() = runTest {
        approveChannel()
        resolver.scriptDetails(video.id)
        val coordinator = coordinator()
        coordinator.play(video)

        assertEquals(PlaybackOutcome.Busy, coordinator.play(video))
        assertEquals(15, consumed(), "one debit, not two")
    }

    @Test
    fun `four impatient taps debit once and leave one tracked session`() = runTest {
        approveChannel()
        resolver.scriptDetails(video.id)
        val coordinator = coordinator()
        repeat(4) { coordinator.play(video) }

        assertEquals(15, consumed())
        coordinator.onPlayerState(playing())
        clock.advanceRealtime(50)
        coordinator.onEnded(completed = true)
        assertEquals(1, ledger.all().size, "three lost sessions would mean three missing rows")
    }

    @Test
    fun `a new video can be played once the previous one has ended`() = runTest {
        approveChannel()
        resolver.scriptDetails(video.id)
        val other = video.copy(id = VideoId("v2"))
        resolver.scriptDetails(other.id)
        val coordinator = coordinator()

        coordinator.play(video)
        coordinator.onEnded(completed = true)
        assertIs<PlaybackOutcome.Started>(coordinator.play(other))
    }

    @Test
    fun `a refused play does not leave the coordinator stuck busy`() = runTest {
        approveChannel()
        whitelistStore.block(video.id)
        val coordinator = coordinator()
        assertIs<PlaybackOutcome.Refused>(coordinator.play(video))

        whitelistStore.unblock(video.id)
        resolver.scriptDetails(video.id)
        assertIs<PlaybackOutcome.Started>(coordinator.play(video))
    }
}
