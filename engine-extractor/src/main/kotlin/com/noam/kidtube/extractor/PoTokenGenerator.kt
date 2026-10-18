package com.noam.kidtube.extractor

/**
 * What one initialised BotGuard session yields.
 *
 * Three facts, not one token. YouTube's attestation is shaped this way: [visitorData]
 * identifies the synthetic web session, [streamingPoToken] is minted from that
 * visitorData and must be minted exactly once per session before anything else, and a
 * per-video player token is minted separately by [PoTokenGenerator.tokenFor].
 */
data class PoTokenSessionData(
    val visitorData: String,
    val streamingPoToken: String,
    val expiresAtEpochSeconds: Long,
)

/**
 * Produces proof-of-origin tokens. The only implementation that matters runs Google's
 * BotGuard inside a WebView; this interface exists so every failure path around it is
 * testable without a device.
 */
interface PoTokenGenerator {
    /**
     * Fetches visitorData from Innertube, runs BotGuard, exchanges its response for an
     * integrity token, and mints the streaming token.
     */
    suspend fun initialise(): Result<PoTokenSessionData>

    /** Mints a player token for one video id. Requires a successful [initialise] first. */
    suspend fun tokenFor(identifier: String): Result<String>

    fun release()
}
