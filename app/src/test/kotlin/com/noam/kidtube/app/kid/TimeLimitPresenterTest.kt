package com.noam.kidtube.app.kid

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TimeLimitPresenterTest {

    @Test
    fun `plenty of time shows nothing at all`() {
        assertEquals(TimeLimitState.Plenty, TimeLimitPresenter.stateFor(3_600))
        assertEquals(TimeLimitState.Plenty, TimeLimitPresenter.stateFor(301))
    }

    @Test
    fun `the warning begins exactly at five minutes`() {
        assertIs<TimeLimitState.RunningOut>(TimeLimitPresenter.stateFor(300))
    }

    @Test
    fun `the warning carries the remaining seconds so the UI can count down`() {
        assertEquals(
            120,
            assertIs<TimeLimitState.RunningOut>(TimeLimitPresenter.stateFor(120)).remainingSeconds,
        )
    }

    @Test
    fun `one second left still warns rather than locking`() {
        assertIs<TimeLimitState.RunningOut>(TimeLimitPresenter.stateFor(1))
    }

    @Test
    fun `zero locks`() {
        assertEquals(TimeLimitState.Locked, TimeLimitPresenter.stateFor(0))
    }

    @Test
    fun `a negative balance locks rather than wrapping into plenty`() {
        assertEquals(TimeLimitState.Locked, TimeLimitPresenter.stateFor(-500))
    }

    @Test
    fun `the warning threshold is configurable for a shorter daily limit`() {
        assertEquals(TimeLimitState.Plenty, TimeLimitPresenter.stateFor(100, warnAtSeconds = 60))
        assertIs<TimeLimitState.RunningOut>(TimeLimitPresenter.stateFor(60, warnAtSeconds = 60))
    }
}
