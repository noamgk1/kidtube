package com.noam.kidtube.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class PolicyConfirmTest {

    private val policy = Policy()
    private val channelId = ChannelId("UC_abc")
    private val videoId = VideoId("vid1")
    private val whitelist = Whitelist(
        channels = mapOf(channelId.value to Channel(channelId, "Kids", null, "feed", 1L)),
    )

    private fun ticket(): PlaybackTicket =
        assertIs<IssueResult.Allowed>(policy.issue(videoId, channelId, whitelist, 600)).ticket

    private fun details(ageLimit: Int = 0, isLive: Boolean = false, id: VideoId = videoId) =
        VideoDetails(
            id = id,
            title = "t",
            durationSeconds = 100L,
            ageLimit = ageLimit,
            isLive = isLive,
            source = PlaybackSource.Streams("https://v", null, "video/mp4"),
        )

    @Test
    fun `clean details confirm the ticket`() {
        val t = ticket()
        val allowed = assertIs<ConfirmResult.Allowed>(policy.confirm(t, details()))
        assertSame(t, allowed.confirmed.ticket)
        assertEquals(videoId, allowed.confirmed.details.id)
    }

    @Test
    fun `an age restricted video is refused even though its channel is approved`() {
        assertEquals(
            DenyReason.AGE_RESTRICTED,
            assertIs<ConfirmResult.Denied>(policy.confirm(ticket(), details(ageLimit = 18))).reason,
        )
    }

    @Test
    fun `any positive age limit is refused, not just eighteen`() {
        assertEquals(
            DenyReason.AGE_RESTRICTED,
            assertIs<ConfirmResult.Denied>(policy.confirm(ticket(), details(ageLimit = 1))).reason,
        )
    }

    @Test
    fun `a live stream is refused because it cannot have been reviewed`() {
        assertEquals(
            DenyReason.LIVE_STREAM,
            assertIs<ConfirmResult.Denied>(policy.confirm(ticket(), details(isLive = true))).reason,
        )
    }

    @Test
    fun `details for a different video than the ticket are refused`() {
        assertEquals(
            DenyReason.NOT_WHITELISTED,
            assertIs<ConfirmResult.Denied>(
                policy.confirm(ticket(), details(id = VideoId("someone_elses_video"))),
            ).reason,
        )
    }
}
