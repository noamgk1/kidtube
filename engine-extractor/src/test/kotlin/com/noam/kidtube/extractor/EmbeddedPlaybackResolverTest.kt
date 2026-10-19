package com.noam.kidtube.extractor

import com.noam.kidtube.core.ChannelId
import com.noam.kidtube.core.PlaybackSource
import com.noam.kidtube.core.Policy
import com.noam.kidtube.core.VideoId
import com.noam.kidtube.core.VideoRef
import com.noam.kidtube.core.Whitelist
import com.noam.kidtube.core.Channel
import com.noam.kidtube.core.IssueResult
import kotlinx.coroutines.test.runTest
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.services.youtube.PoTokenProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Fails loudly: reaching the network at all is the thing under test. */
private object ForbiddenDownloader : Downloader() {
    override fun execute(request: Request): Response =
        throw AssertionError("the resolver made a network request it did not need")
}

private object NoPoTokens : PoTokenProvider {
    override fun getWebClientPoToken(videoId: String) = null
    override fun getWebEmbedClientPoToken(videoId: String) = null
    override fun getAndroidClientPoToken(videoId: String) = null
    override fun getIosClientPoToken(videoId: String) = null
}

/**
 * The wait before a video starts.
 *
 * Extraction was measured between 1.8 and 26.6 seconds, and on the embedded path it
 * produces nothing that gets used - the player needs a video id and nothing else. So a
 * video already in the approved catalog must resolve without touching the network at
 * all, and this fails the moment it does.
 */
class EmbeddedPlaybackResolverTest {

    private val kids = ChannelId("UC_kids")
    private val videoId = VideoId("v1")
    private val known = VideoRef(videoId, kids, "Baby Shark", "https://thumb", 125L, 900L)

    private fun ticketFor(id: VideoId, channel: ChannelId): com.noam.kidtube.core.PlaybackTicket {
        val whitelist = Whitelist(
            channels = mapOf(channel.value to Channel(channel, "Kids", null, "feed", 1L)),
        )
        val issued = Policy().issue(id, channel, whitelist, remainingBudgetSeconds = 600)
        return assertIs<IssueResult.Allowed>(issued).ticket
    }

    private fun resolver(lookup: (VideoId) -> VideoRef?) =
        EmbeddedPlaybackResolver(lookup, NoPoTokens, ForbiddenDownloader)

    @Test
    fun `a video already in the catalog resolves without any network call`() = runTest {
        val details = resolver { known }.resolve(ticketFor(videoId, kids)).getOrThrow()

        assertEquals(videoId, details.id)
        assertEquals("Baby Shark", details.title)
        assertEquals(125L, details.durationSeconds)
        assertEquals(PlaybackSource.Embedded(videoId), details.source)
    }

    @Test
    fun `what it returns is something Policy will confirm`() = runTest {
        // The point of the fast path is to reach the player, so the details it builds
        // have to survive the gate that stands between.
        val ticket = ticketFor(videoId, kids)
        val details = resolver { known }.resolve(ticket).getOrThrow()

        assertIs<com.noam.kidtube.core.ConfirmResult.Allowed>(Policy().confirm(ticket, details))
    }

    @Test
    fun `a video with no duration yet still resolves`() = runTest {
        // The RSS fast path stores no duration, so the newest videos arrive without one.
        val undated = known.copy(durationSeconds = null)

        val details = resolver { undated }.resolve(ticketFor(videoId, kids)).getOrThrow()

        assertEquals(0L, details.durationSeconds)
    }

    @Test
    fun `a video that is not in the catalog is not answered from nothing`() = runTest {
        // It falls through to extraction instead of inventing details - which here means
        // the forbidden downloader is reached, and that is the correct behaviour.
        val outcome = resolver { null }.resolve(ticketFor(videoId, kids))

        assertTrue(outcome.isFailure, "a video it knows nothing about must not resolve silently")
    }
}
