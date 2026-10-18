package com.noam.kidtube.extractor

import com.noam.kidtube.core.Clock
import com.noam.kidtube.engine.EngineHealth
import kotlinx.coroutines.delay
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class TestClock(var epochMillis: Long = 1_760_000_000_000L) : Clock {
    override fun nowEpochMillis(): Long = epochMillis
    override fun elapsedRealtimeMillis(): Long = epochMillis
    override fun zone(): ZoneId = ZoneId.of("Asia/Jerusalem")
    fun advanceSeconds(seconds: Long) { epochMillis += seconds * 1_000L }
}

private class FakeGenerator(private val clock: TestClock) : PoTokenGenerator {
    var initialiseCalls = 0
    var tokenCalls = 0
    var released = false

    var initialiseFailure: Throwable? = null
    var tokenFailure: Throwable? = null
    var initialiseDelayMs: Long = 0
    var tokenDelayMs: Long = 0
    var lifetimeSeconds: Long = 3_600

    override suspend fun initialise(): Result<PoTokenSessionData> {
        initialiseCalls++
        if (initialiseDelayMs > 0) delay(initialiseDelayMs)
        val failure = initialiseFailure
        if (failure != null) return Result.failure(failure)
        return Result.success(
            PoTokenSessionData(
                visitorData = "visitor-" + initialiseCalls,
                streamingPoToken = "streaming-" + initialiseCalls,
                expiresAtEpochSeconds = clock.nowEpochMillis() / 1_000 + lifetimeSeconds,
            ),
        )
    }

    override suspend fun tokenFor(identifier: String): Result<String> {
        tokenCalls++
        if (tokenDelayMs > 0) delay(tokenDelayMs)
        val failure = tokenFailure
        if (failure != null) return Result.failure(failure)
        return Result.success("player-for-" + identifier)
    }

    override fun release() { released = true }
}

class PoTokenSessionTest {

    private val clock = TestClock()
    private val generator = FakeGenerator(clock)

    private fun session() = PoTokenSession(generator, clock)

    @Test
    fun `the first request initialises once and returns all three fields`() {
        val result = assertNotNull(session().getWebClientPoToken("vid1"))
        assertEquals("visitor-1", result.visitorData)
        assertEquals("player-for-vid1", result.playerRequestPoToken)
        assertEquals("streaming-1", result.streamingDataPoToken)
        assertEquals(1, generator.initialiseCalls)
    }

    @Test
    fun `a second video reuses the session without re-initialising`() {
        val s = session()
        s.getWebClientPoToken("vid1")
        s.getWebClientPoToken("vid2")
        assertEquals(1, generator.initialiseCalls)
        assertEquals(2, generator.tokenCalls)
    }

    @Test
    fun `a player token for the same video is served from cache`() {
        val s = session()
        s.getWebClientPoToken("vid1")
        s.getWebClientPoToken("vid1")
        assertEquals(1, generator.tokenCalls)
    }

    @Test
    fun `an expired session is rebuilt`() {
        generator.lifetimeSeconds = 100
        val s = session()
        s.getWebClientPoToken("vid1")
        clock.advanceSeconds(200)
        val result = assertNotNull(s.getWebClientPoToken("vid1"))
        assertEquals(2, generator.initialiseCalls)
        assertEquals("visitor-2", result.visitorData, "a rebuilt session carries fresh visitorData")
    }

    @Test
    fun `a slow initialisation times out and degrades instead of hanging`() {
        generator.initialiseDelayMs = 60_000
        val s = PoTokenSession(generator, clock, coldInitTimeoutMs = 50)
        assertNull(s.getWebClientPoToken("vid1"))
        assertIs<EngineHealth.Degraded>(s.health.value)
    }

    @Test
    fun `a slow token request times out`() {
        generator.tokenDelayMs = 60_000
        val s = PoTokenSession(generator, clock, requestTimeoutMs = 50)
        assertNull(s.getWebClientPoToken("vid1"))
    }

    @Test
    fun `a generator failure yields null so extraction can still run unattested`() {
        generator.tokenFailure = IllegalStateException("botguard changed")
        assertNull(session().getWebClientPoToken("vid1"))
    }

    @Test
    fun `an initialisation failure yields null`() {
        generator.initialiseFailure = IllegalStateException("no network")
        assertNull(session().getWebClientPoToken("vid1"))
    }

    @Test
    fun `the breaker opens after the failure threshold and stops calling the generator`() {
        generator.tokenFailure = IllegalStateException("boom")
        val s = PoTokenSession(generator, clock, failureThreshold = 3)

        repeat(3) { s.getWebClientPoToken("vid" + it) }
        val callsWhenOpen = generator.tokenCalls

        repeat(5) { s.getWebClientPoToken("more" + it) }
        assertEquals(callsWhenOpen, generator.tokenCalls, "an open breaker must not call out")
        assertIs<EngineHealth.Unavailable>(s.health.value)
    }

    @Test
    fun `the breaker closes after the cooldown and tries again`() {
        generator.tokenFailure = IllegalStateException("boom")
        val s = PoTokenSession(generator, clock, failureThreshold = 2, cooldownSeconds = 300)
        repeat(2) { s.getWebClientPoToken("vid" + it) }
        val callsWhenOpen = generator.tokenCalls

        clock.advanceSeconds(301)
        generator.tokenFailure = null
        assertNotNull(s.getWebClientPoToken("later"))
        assertTrue(generator.tokenCalls > callsWhenOpen)
        assertEquals(EngineHealth.Healthy, s.health.value)
    }

    @Test
    fun `a success resets the failure count so intermittent errors do not open the breaker`() {
        val s = PoTokenSession(generator, clock, failureThreshold = 3)
        generator.tokenFailure = IllegalStateException("blip")
        s.getWebClientPoToken("a")
        s.getWebClientPoToken("b")

        generator.tokenFailure = null
        assertNotNull(s.getWebClientPoToken("c"))

        generator.tokenFailure = IllegalStateException("blip")
        s.getWebClientPoToken("d")
        s.getWebClientPoToken("e")
        // Degraded, not Unavailable: two failures after a success have not reached three,
        // so the breaker is still closed and the generator is still being called.
        assertIs<EngineHealth.Degraded>(s.health.value)
        assertEquals(5, generator.tokenCalls, "an open breaker would have stopped calling out")
    }

    @Test
    fun `every client is served, because v0_26_5 asks only the android one`() {
        // Measured by the verification gate on 2026-10-18: web-only meant BotGuard
        // never ran, because the library never asks the web client.
        val s = session()
        assertNotNull(s.getWebClientPoToken("v"))
        assertNotNull(s.getWebEmbedClientPoToken("v"))
        assertNotNull(s.getAndroidClientPoToken("v"))
        assertNotNull(s.getIosClientPoToken("v"))
    }

    @Test
    fun `serving several clients for one video still mints only one token`() {
        val s = session()
        s.getWebClientPoToken("v")
        s.getAndroidClientPoToken("v")
        s.getIosClientPoToken("v")
        assertEquals(1, generator.tokenCalls, "the per-video cache must cover every client")
    }

    @Test
    fun `release passes through to the generator`() {
        val s = session()
        s.getWebClientPoToken("v")
        s.release()
        assertTrue(generator.released)
    }

    @Test
    fun `after release the next request initialises a fresh session`() {
        val s = session()
        s.getWebClientPoToken("v")
        s.release()
        assertNotNull(s.getWebClientPoToken("v"))
        assertEquals(2, generator.initialiseCalls)
    }
}
