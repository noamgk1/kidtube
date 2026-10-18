package com.noam.kidtube.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BudgetAccountantTest {

    private val clock = FakeClock()
    private val accountant = BudgetAccountant(clock)
    private val today = "2026-10-18"
    private val dailyLimit = 3_600 // one hour

    @Test
    fun `remaining includes the bonus and floors at zero`() {
        assertEquals(3_600, accountant.remainingSeconds(Budget(today), dailyLimit))
        assertEquals(600, accountant.remainingSeconds(Budget(today, consumedSeconds = 3_000), dailyLimit))
        assertEquals(900, accountant.remainingSeconds(Budget(today, 3_000, bonusSeconds = 300), dailyLimit))
        assertEquals(0, accountant.remainingSeconds(Budget(today, consumedSeconds = 9_999), dailyLimit))
    }

    @Test
    fun `forToday resets a budget stored under a past date`() {
        val stale = Budget("2026-10-17", consumedSeconds = 3_600, bonusSeconds = 300)
        val fresh = accountant.forToday(stale)
        assertEquals(today, fresh.dateIso)
        assertEquals(0, fresh.consumedSeconds)
        assertEquals(0, fresh.bonusSeconds)
    }

    @Test
    fun `forToday leaves a budget alone when it is already today`() {
        val current = Budget(today, consumedSeconds = 120)
        assertEquals(current, accountant.forToday(current))
    }

    @Test
    fun `forToday keeps consumption when the device clock was moved backwards`() {
        val fromTomorrow = Budget("2026-10-19", consumedSeconds = 3_000)
        assertEquals(fromTomorrow, accountant.forToday(fromTomorrow))
    }

    @Test
    fun `opening a session debits fifteen seconds before anything is watched`() {
        val (session, budget) = accountant.openSession(VideoId("v1"), Budget(today))
        assertEquals(15, budget.consumedSeconds)
        assertEquals(15, session.debitedSeconds)
    }

    @Test
    fun `a force-kill loop costs budget instead of saving it`() {
        var budget = Budget(today)
        repeat(10) {
            val (_, after) = accountant.openSession(VideoId("v1"), budget)
            budget = after
        }
        assertEquals(150, budget.consumedSeconds)
        assertTrue(
            accountant.remainingSeconds(budget, dailyLimit) < dailyLimit,
            "the loop must erode the allowance, not preserve it",
        )
    }

    @Test
    fun `a tick debits only the shortfall beyond what was already taken`() {
        val (session, afterOpen) = accountant.openSession(VideoId("v1"), Budget(today))
        clock.advanceRealtime(60)
        val (nextSession, afterTick) = accountant.tick(session, afterOpen)
        assertEquals(60, afterTick.consumedSeconds, "15 up front plus 45 of shortfall")
        assertEquals(60, nextSession.debitedSeconds)
    }

    @Test
    fun `a tick before the up-front debit is used up changes nothing`() {
        val (session, afterOpen) = accountant.openSession(VideoId("v1"), Budget(today))
        clock.advanceRealtime(5)
        val (_, afterTick) = accountant.tick(session, afterOpen)
        assertEquals(15, afterTick.consumedSeconds)
    }

    @Test
    fun `midnight during playback does not grant a fresh allowance`() {
        val (session, afterOpen) = accountant.openSession(VideoId("v1"), Budget(today, 3_000))
        clock.advanceRealtime(120)
        clock.setDate(LocalDate.of(2026, 10, 19))

        val (_, afterTick) = accountant.tick(session, afterOpen)
        assertEquals(today, afterTick.dateIso, "the session keeps consuming the day it began")
        assertEquals(3_120, afterTick.consumedSeconds)
        assertEquals(480, accountant.remainingSeconds(afterTick, dailyLimit))
    }

    @Test
    fun `the new day arrives only on the next forToday, which issue calls`() {
        val (session, afterOpen) = accountant.openSession(VideoId("v1"), Budget(today, 3_000))
        clock.advanceRealtime(120)
        clock.setDate(LocalDate.of(2026, 10, 19))
        val (_, afterTick) = accountant.tick(session, afterOpen)

        val nextDay = accountant.forToday(afterTick)
        assertEquals("2026-10-19", nextDay.dateIso)
        assertEquals(0, nextDay.consumedSeconds)
    }

    @Test
    fun `ticking a session against a rolled-over record is a programming error`() {
        val (session, _) = accountant.openSession(VideoId("v1"), Budget(today))
        assertFailsWith<IllegalArgumentException> {
            accountant.tick(session, Budget("2026-10-19"))
        }
    }

    @Test
    fun `a bonus adds to the allowance without touching consumption`() {
        val granted = accountant.grantBonus(Budget(today, consumedSeconds = 3_600), 600)
        assertEquals(3_600, granted.consumedSeconds)
        assertEquals(600, accountant.remainingSeconds(granted, dailyLimit))
    }
}
