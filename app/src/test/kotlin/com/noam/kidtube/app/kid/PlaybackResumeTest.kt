package com.noam.kidtube.app.kid

import com.noam.kidtube.core.BudgetAccountant
import com.noam.kidtube.core.Channel
import com.noam.kidtube.core.ChannelId
import com.noam.kidtube.core.Clock
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
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private class FixedClock : Clock {
    var realtime = 0L
    override fun nowEpochMillis(): Long =
        LocalDate.of(2026, 10, 18).atTime(12, 0).atZone(zone()).toInstant().toEpochMilli()
    override fun elapsedRealtimeMillis(): Long = realtime
    override fun zone(): ZoneId = ZoneId.of("Asia/Jerusalem")
}

/**
 * Picking a video up where it was left off. The rules of what is worth remembering live
 * in ResumeStore and are tested there; this is about the coordinator actually asking it.
 */
class PlaybackResumeTest {

    private val root: File = Files.createTempDirectory("resume-playback").toFile()
    private val files = FileStore(root)
    private val clock = FixedClock()
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

    private fun approve() {
        whitelistStore.addChannel(Channel(channelId, "Kids", null, "feed", 1L))
        settingsStore.save(Settings(dailyLimitMinutes = 60))
        resolver.scriptDetails(video.id)
    }

    @Test
    fun `a video with a remembered position starts from it`() = runTest {
        approve()
        resumeStore.save(video.id, positionMs = 120_000, durationMs = 300_000)

        assertIs<PlaybackOutcome.Started>(coordinator().play(video))
        assertEquals(
            120_000L,
            player.state.value.positionMs,
            "the child should land back where they stopped, not at the beginning",
        )
    }

    @Test
    fun `a video never watched starts at the beginning`() = runTest {
        approve()
        assertIs<PlaybackOutcome.Started>(coordinator().play(video))
        assertEquals(0L, player.state.value.positionMs)
    }

    @Test
    fun `a tick remembers where the child is`() = runTest {
        approve()
        val coordinator = coordinator()
        coordinator.play(video)
        coordinator.onPlayerState(
            PlayerState(PlaybackPhase.PLAYING, positionMs = 95_000, durationMs = 300_000),
        )

        coordinator.tick()

        assertEquals(95_000L, resumeStore.positionFor(video.id))
    }

    @Test
    fun `backing out of a video remembers the position`() = runTest {
        approve()
        val coordinator = coordinator()
        coordinator.play(video)
        coordinator.onPlayerState(
            PlayerState(PlaybackPhase.PLAYING, positionMs = 61_000, durationMs = 300_000),
        )

        coordinator.onEnded(completed = false)

        assertEquals(61_000L, resumeStore.positionFor(video.id))
        assertTrue(resumeStore.continueWatching().any { it.videoId == "v1" })
    }

    @Test
    fun `finishing a video forgets it`() = runTest {
        approve()
        resumeStore.save(video.id, positionMs = 120_000, durationMs = 300_000)

        val coordinator = coordinator()
        coordinator.play(video)
        coordinator.onPlayerState(
            PlayerState(PlaybackPhase.PLAYING, positionMs = 140_000, durationMs = 300_000),
        )
        coordinator.onEnded(completed = true)

        assertEquals(
            0L,
            resumeStore.positionFor(video.id),
            "a video watched to the end must not offer to resume from the middle",
        )
    }

    @Test
    fun `a glance is not progress`() = runTest {
        approve()
        val coordinator = coordinator()
        coordinator.play(video)
        coordinator.onPlayerState(
            PlayerState(PlaybackPhase.PLAYING, positionMs = 3_000, durationMs = 300_000),
        )
        coordinator.onEnded(completed = false)

        assertTrue(
            resumeStore.continueWatching().isEmpty(),
            "three seconds in means the child changed their mind, not that they were interrupted",
        )
    }
}
