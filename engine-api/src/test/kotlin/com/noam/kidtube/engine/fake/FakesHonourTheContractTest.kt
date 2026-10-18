package com.noam.kidtube.engine.fake

import com.noam.kidtube.core.Channel
import com.noam.kidtube.core.ChannelId
import com.noam.kidtube.core.ConfirmResult
import com.noam.kidtube.core.IssueResult
import com.noam.kidtube.core.PlaybackSource
import com.noam.kidtube.core.Policy
import com.noam.kidtube.core.VideoId
import com.noam.kidtube.core.VideoRef
import com.noam.kidtube.core.Whitelist
import com.noam.kidtube.engine.ChannelResolutionError
import com.noam.kidtube.engine.Cursor
import com.noam.kidtube.engine.PlaybackPhase
import com.noam.kidtube.engine.VideoUnavailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FakesHonourTheContractTest {

    private val channelId = ChannelId("UC_abc")
    private val policy = Policy()
    private val whitelist = Whitelist(
        channels = mapOf(channelId.value to Channel(channelId, "Kids", null, "feed", 1L)),
    )

    private fun video(id: String) = VideoRef(VideoId(id), channelId, "t " + id, null, 60L, 100L)

    private fun ticket(id: String) =
        assertIs<IssueResult.Allowed>(policy.issue(VideoId(id), channelId, whitelist, 600)).ticket

    @Test
    fun `resolveChannel returns a scripted channel`() = runTest {
        val source = FakeContentSource().apply {
            scriptChannel("https://youtube.com/@kids", Channel(channelId, "Kids", null, "feed", 7L))
        }
        assertEquals("Kids", source.resolveChannel("https://youtube.com/@kids").getOrThrow().title)
    }

    @Test
    fun `an unscripted url resolves to NotAChannelUrl rather than throwing`() = runTest {
        val failure = FakeContentSource().resolveChannel("https://example.test/nope")
        assertIs<ChannelResolutionError.NotAChannelUrl>(failure.exceptionOrNull())
    }

    @Test
    fun `listVideos walks every scripted page to exhaustion exactly once`() = runTest {
        val source = FakeContentSource().apply {
            scriptPages(
                channelId,
                listOf(
                    videoPage(listOf(video("a"), video("b")), nextToken = "p2"),
                    videoPage(listOf(video("c")), nextToken = null),
                ),
            )
        }

        val collected = mutableListOf<String>()
        var page = source.listVideos(channelId, null).getOrThrow()
        collected += page.videos.map { it.id.value }
        while (page.next != null) {
            page = source.listVideos(channelId, page.next).getOrThrow()
            collected += page.videos.map { it.id.value }
        }

        assertEquals(listOf("a", "b", "c"), collected)
        assertEquals(2, source.listVideosCalls)
    }

    @Test
    fun `a scripted mid-paging failure surfaces as a failed Result`() = runTest {
        val source = FakeContentSource().apply {
            scriptPages(channelId, listOf(videoPage(listOf(video("a")), nextToken = "p2")))
            failPagesAfter = 1
        }
        source.listVideos(channelId, null).getOrThrow()
        assertTrue(source.listVideos(channelId, Cursor("p2")).isFailure)
    }

    @Test
    fun `latestVideos returns the scripted feed`() = runTest {
        val source = FakeContentSource().apply { scriptFeed(channelId, listOf(video("new"))) }
        assertEquals(
            listOf("new"),
            source.latestVideos(channelId, "feed").getOrThrow().map { it.id.value },
        )
    }

    @Test
    fun `the resolver returns scripted details for a ticketed video`() = runTest {
        val resolver = FakePlaybackResolver().apply { scriptDetails(VideoId("a")) }
        val details = resolver.resolve(ticket("a")).getOrThrow()
        assertEquals(VideoId("a"), details.id)
        assertIs<PlaybackSource.Streams>(details.source)
    }

    @Test
    fun `an unscripted video resolves to VideoUnavailable`() = runTest {
        val failure = FakePlaybackResolver().resolve(ticket("gone"))
        assertIs<VideoUnavailable>(failure.exceptionOrNull())
    }

    @Test
    fun `the fake player moves through buffering, playing, paused and ended`() = runTest {
        val resolver = FakePlaybackResolver().apply { scriptDetails(VideoId("a")) }
        val t = ticket("a")
        val details = resolver.resolve(t).getOrThrow()
        val confirmed = assertIs<ConfirmResult.Allowed>(policy.confirm(t, details)).confirmed

        val player = FakePlayerEngine().createController(CoroutineScope(coroutineContext)) as FakeVideoPlayer
        assertEquals(PlaybackPhase.IDLE, player.state.value.phase)

        player.start(confirmed)
        assertEquals(PlaybackPhase.BUFFERING, player.state.value.phase)

        player.becomeReady()
        assertEquals(PlaybackPhase.PLAYING, player.state.value.phase)

        player.pause()
        assertEquals(PlaybackPhase.PAUSED, player.state.value.phase)

        player.completeVideo()
        assertEquals(PlaybackPhase.ENDED, player.state.value.phase)
    }

    @Test
    fun `the player records which confirmed ticket it was started with`() = runTest {
        val resolver = FakePlaybackResolver().apply { scriptDetails(VideoId("a")) }
        val t = ticket("a")
        val confirmed = assertIs<ConfirmResult.Allowed>(
            policy.confirm(t, resolver.resolve(t).getOrThrow()),
        ).confirmed

        val player = FakePlayerEngine().createController(CoroutineScope(coroutineContext)) as FakeVideoPlayer
        player.start(confirmed)
        assertEquals(VideoId("a"), player.startedWith?.ticket?.videoId)
    }

    @Test
    fun `seeking reports the new position`() = runTest {
        val resolver = FakePlaybackResolver().apply { scriptDetails(VideoId("a")) }
        val t = ticket("a")
        val confirmed = assertIs<ConfirmResult.Allowed>(
            policy.confirm(t, resolver.resolve(t).getOrThrow()),
        ).confirmed

        val player = FakePlayerEngine().createController(CoroutineScope(coroutineContext))
        player.start(confirmed)
        player.seekTo(42_000)
        assertEquals(42_000, player.state.value.positionMs)
    }

    @Test
    fun `a recoverable failure is reported as recoverable`() = runTest {
        val player = FakePlayerEngine().createController(CoroutineScope(coroutineContext)) as FakeVideoPlayer
        player.fail("HTTP 403", recoverable = true)
        assertEquals(PlaybackPhase.FAILED, player.state.value.phase)
        assertTrue(player.state.value.failureIsRecoverable)
    }
}
