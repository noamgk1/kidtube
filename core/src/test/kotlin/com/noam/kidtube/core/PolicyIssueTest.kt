package com.noam.kidtube.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PolicyIssueTest {

    private val policy = Policy()
    private val channelId = ChannelId("UC_abc")
    private val videoId = VideoId("vid1")

    private val approvedChannel = Whitelist(
        channels = mapOf(
            channelId.value to Channel(channelId, "Kids", null, "feed", 1L),
        ),
    )

    @Test
    fun `a video from an approved channel gets a ticket`() {
        val result = policy.issue(videoId, channelId, approvedChannel, remainingBudgetSeconds = 600)
        val allowed = assertIs<IssueResult.Allowed>(result)
        assertEquals(videoId, allowed.ticket.videoId)
        assertEquals(channelId, allowed.ticket.channelId)
        assertEquals(600, allowed.ticket.budgetSecondsAtIssue)
    }

    @Test
    fun `a video from an unapproved channel is refused`() {
        val result = policy.issue(videoId, ChannelId("UC_other"), approvedChannel, 600)
        assertEquals(DenyReason.NOT_WHITELISTED, assertIs<IssueResult.Denied>(result).reason)
    }

    @Test
    fun `an individually approved video needs no channel`() {
        val whitelist = Whitelist(
            singleVideos = mapOf(
                videoId.value to VideoRef(videoId, channelId, "t", null, 10L, null),
            ),
        )
        assertIs<IssueResult.Allowed>(policy.issue(videoId, channelId = null, whitelist, 600))
    }

    @Test
    fun `the blocklist overrides an approved channel`() {
        val whitelist = approvedChannel.copy(blockedVideoIds = setOf(videoId.value))
        assertEquals(
            DenyReason.BLOCKED,
            assertIs<IssueResult.Denied>(policy.issue(videoId, channelId, whitelist, 600)).reason,
        )
    }

    @Test
    fun `the blocklist is checked before the whitelist so the reason is the specific one`() {
        val whitelist = Whitelist(blockedVideoIds = setOf(videoId.value))
        assertEquals(
            DenyReason.BLOCKED,
            assertIs<IssueResult.Denied>(policy.issue(videoId, ChannelId("UC_other"), whitelist, 600)).reason,
        )
    }

    @Test
    fun `an exhausted budget is refused`() {
        assertEquals(
            DenyReason.BUDGET_EXHAUSTED,
            assertIs<IssueResult.Denied>(
                policy.issue(videoId, channelId, approvedChannel, remainingBudgetSeconds = 0),
            ).reason,
        )
    }

    @Test
    fun `one second of budget is still enough`() {
        assertIs<IssueResult.Allowed>(
            policy.issue(videoId, channelId, approvedChannel, remainingBudgetSeconds = 1),
        )
    }

    @Test
    fun `a negative budget is treated as exhausted and never as allowed`() {
        assertEquals(
            DenyReason.BUDGET_EXHAUSTED,
            assertIs<IssueResult.Denied>(
                policy.issue(videoId, channelId, approvedChannel, remainingBudgetSeconds = -30),
            ).reason,
        )
    }
}
