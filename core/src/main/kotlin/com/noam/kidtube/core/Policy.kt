package com.noam.kidtube.core

enum class DenyReason {
    NOT_WHITELISTED,
    BLOCKED,
    AGE_RESTRICTED,
    LIVE_STREAM,
    BUDGET_EXHAUSTED,
}

/**
 * Proof that [Policy] approved this video against everything knowable locally.
 * The constructor is internal to :core, so no UI code can manufacture one.
 */
class PlaybackTicket internal constructor(
    val videoId: VideoId,
    val channelId: ChannelId?,
    val budgetSecondsAtIssue: Int,
)

sealed interface IssueResult {
    data class Allowed(val ticket: PlaybackTicket) : IssueResult
    data class Denied(val reason: DenyReason) : IssueResult
}

class Policy {

    /**
     * Phase one: everything knowable without touching the network. Age limit and
     * live-ness are not knowable here — a channel listing does not carry them — so
     * they are checked in confirm() once extraction has happened.
     */
    fun issue(
        videoId: VideoId,
        channelId: ChannelId?,
        whitelist: Whitelist,
        remainingBudgetSeconds: Int,
    ): IssueResult {
        // Blocklist first: it always wins, and it is the more specific refusal.
        if (whitelist.isBlocked(videoId)) return IssueResult.Denied(DenyReason.BLOCKED)

        val approved = whitelist.hasSingleVideo(videoId) ||
            (channelId != null && whitelist.hasChannel(channelId))
        if (!approved) return IssueResult.Denied(DenyReason.NOT_WHITELISTED)

        if (remainingBudgetSeconds < 1) return IssueResult.Denied(DenyReason.BUDGET_EXHAUSTED)

        return IssueResult.Allowed(PlaybackTicket(videoId, channelId, remainingBudgetSeconds))
    }
}
