package com.noam.kidtube.app.admin

import com.noam.kidtube.app.FakeClock
import com.noam.kidtube.core.Budget
import com.noam.kidtube.core.BudgetAccountant
import com.noam.kidtube.core.Settings
import com.noam.kidtube.core.store.BudgetStore
import com.noam.kidtube.core.store.FileStore
import com.noam.kidtube.core.store.SettingsStore
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TimePresenterTest {

    private val root: File = Files.createTempDirectory("time").toFile()
    private val files = FileStore(root)
    private val clock = FakeClock()
    private val settingsStore = SettingsStore(files)
    private val budgetStore = BudgetStore(files)
    private val presenter = TimePresenter(settingsStore, budgetStore, BudgetAccountant(clock), clock)

    @AfterTest
    fun tearDown() { root.deleteRecursively() }

    @Test
    fun `a fresh day shows the full allowance`() {
        settingsStore.save(Settings(dailyLimitMinutes = 60))
        val view = presenter.view()
        assertEquals(60, view.dailyLimitMinutes)
        assertEquals(0, view.consumedSeconds)
        assertEquals(3_600, view.remainingSeconds)
    }

    @Test
    fun `consumption is reflected in what remains`() {
        settingsStore.save(Settings(dailyLimitMinutes = 60))
        budgetStore.save(Budget("2026-10-18", consumedSeconds = 900))
        assertEquals(2_700, presenter.view().remainingSeconds)
    }

    @Test
    fun `yesterday consumption does not count against today`() {
        settingsStore.save(Settings(dailyLimitMinutes = 60))
        budgetStore.save(Budget("2026-10-17", consumedSeconds = 3_600))
        assertEquals(0, presenter.view().consumedSeconds)
        assertEquals(3_600, presenter.view().remainingSeconds)
    }

    @Test
    fun `setting a new limit persists and is reflected immediately`() {
        presenter.setDailyLimitMinutes(30)
        assertEquals(30, settingsStore.load().dailyLimitMinutes)
        assertEquals(1_800, presenter.view().remainingSeconds)
    }

    @Test
    fun `a bonus extends today only`() {
        settingsStore.save(Settings(dailyLimitMinutes = 60))
        budgetStore.save(Budget("2026-10-18", consumedSeconds = 3_600))
        presenter.grantBonusMinutes(10)
        assertEquals(600, presenter.view().remainingSeconds)

        clock.setDate(LocalDate.of(2026, 10, 19))
        assertEquals(3_600, presenter.view().remainingSeconds, "a bonus does not carry over")
    }

    @Test
    fun `remaining never goes negative`() {
        settingsStore.save(Settings(dailyLimitMinutes = 1))
        budgetStore.save(Budget("2026-10-18", consumedSeconds = 9_999))
        assertEquals(0, presenter.view().remainingSeconds)
    }

    @Test
    fun `a negative limit is clamped to zero rather than inverting the allowance`() {
        presenter.setDailyLimitMinutes(-30)
        assertEquals(0, settingsStore.load().dailyLimitMinutes)
        assertEquals(0, presenter.view().remainingSeconds)
    }
}
