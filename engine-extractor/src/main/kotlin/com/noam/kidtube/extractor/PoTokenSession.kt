package com.noam.kidtube.extractor

import com.noam.kidtube.core.Clock
import com.noam.kidtube.engine.EngineHealth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.services.youtube.PoTokenProvider
import org.schabi.newpipe.extractor.services.youtube.PoTokenResult

/**
 * Bridges NewPipeExtractor's synchronous [PoTokenProvider] onto an asynchronous
 * generator. NewPipeExtractor calls these methods from whichever thread performs
 * extraction, which in this app is always a Dispatchers.IO coroutine — never the main
 * thread, or the [runBlocking] below would deadlock against the WebView.
 *
 * Two things NewPipe's own implementation lacks and this one has:
 *  - hard timeouts, so a network stall cannot freeze playback indefinitely;
 *  - a circuit breaker, so a permanently broken BotGuard stops draining a tablet
 *    battery on retries.
 *
 * Returning null is deliberate and safe: extraction then proceeds unattested, which
 * means 360p on Made-for-Kids content rather than no playback at all.
 */
internal class PoTokenSession(
    private val generator: PoTokenGenerator,
    private val clock: Clock,
    private val coldInitTimeoutMs: Long = COLD_INIT_TIMEOUT_MS,
    private val requestTimeoutMs: Long = REQUEST_TIMEOUT_MS,
    private val failureThreshold: Int = FAILURE_THRESHOLD,
    private val cooldownSeconds: Long = COOLDOWN_SECONDS,
) : PoTokenProvider {

    private val lock = Mutex()
    private val playerTokens = mutableMapOf<String, String>()
    private var sessionData: PoTokenSessionData? = null
    private var consecutiveFailures = 0
    private var breakerOpenedAtEpochSeconds: Long? = null

    private val mutableHealth = MutableStateFlow<EngineHealth>(EngineHealth.Healthy)
    val health: StateFlow<EngineHealth> = mutableHealth.asStateFlow()

    /**
     * Only the web client is served. BotGuard mints an attestation for the synthetic web
     * session this class creates; handing that token to the android or ios clients would
     * be presenting the wrong credential, so those correctly get nothing.
     */
    override fun getWebClientPoToken(videoId: String): PoTokenResult? =
        runBlocking { resultFor(videoId) }

    override fun getWebEmbedClientPoToken(videoId: String): PoTokenResult? = null

    override fun getAndroidClientPoToken(videoId: String): PoTokenResult? = null

    override fun getIosClientPoToken(videoId: String): PoTokenResult? = null

    fun release() {
        generator.release()
        sessionData = null
        playerTokens.clear()
    }

    private suspend fun resultFor(videoId: String): PoTokenResult? = lock.withLock {
        if (breakerIsOpen()) return@withLock null

        val session = ensureSession() ?: return@withLock null

        val cached = playerTokens[videoId]
        if (cached != null) {
            return@withLock PoTokenResult(session.visitorData, cached, session.streamingPoToken)
        }

        val minted = withTimeoutOrNull(requestTimeoutMs) { generator.tokenFor(videoId) }
        if (minted == null || minted.isFailure) {
            recordFailure(
                if (minted == null) {
                    "PO token request timed out after " + requestTimeoutMs + "ms"
                } else {
                    "PO token request failed: " + minted.exceptionOrNull()?.message
                },
            )
            return@withLock null
        }

        val playerToken = minted.getOrThrow()
        playerTokens[videoId] = playerToken
        recordSuccess()
        PoTokenResult(session.visitorData, playerToken, session.streamingPoToken)
    }

    private suspend fun ensureSession(): PoTokenSessionData? {
        val current = sessionData
        if (current != null && current.expiresAtEpochSeconds > nowSeconds()) return current

        // Either no session yet, or the integrity token lapsed: build a fresh one.
        playerTokens.clear()
        val initialised = withTimeoutOrNull(coldInitTimeoutMs) { generator.initialise() }
        if (initialised == null || initialised.isFailure) {
            sessionData = null
            recordFailure(
                if (initialised == null) {
                    "PO token init timed out after " + coldInitTimeoutMs + "ms"
                } else {
                    "PO token init failed: " + initialised.exceptionOrNull()?.message
                },
            )
            return null
        }
        return initialised.getOrThrow().also { sessionData = it }
    }

    private fun breakerIsOpen(): Boolean {
        val openedAt = breakerOpenedAtEpochSeconds ?: return false
        if (nowSeconds() - openedAt < cooldownSeconds) return true
        // Cooldown elapsed: close the breaker and allow one more attempt.
        breakerOpenedAtEpochSeconds = null
        consecutiveFailures = 0
        sessionData = null
        return false
    }

    private fun recordFailure(reason: String) {
        consecutiveFailures++
        if (consecutiveFailures >= failureThreshold) {
            breakerOpenedAtEpochSeconds = nowSeconds()
            mutableHealth.value = EngineHealth.Unavailable(reason, nowSeconds())
        } else {
            mutableHealth.value = EngineHealth.Degraded(reason)
        }
    }

    private fun recordSuccess() {
        consecutiveFailures = 0
        breakerOpenedAtEpochSeconds = null
        mutableHealth.value = EngineHealth.Healthy
    }

    private fun nowSeconds(): Long = clock.nowEpochMillis() / 1_000

    companion object {
        const val COLD_INIT_TIMEOUT_MS: Long = 20_000
        const val REQUEST_TIMEOUT_MS: Long = 8_000
        const val FAILURE_THRESHOLD: Int = 3
        const val COOLDOWN_SECONDS: Long = 300
    }
}
