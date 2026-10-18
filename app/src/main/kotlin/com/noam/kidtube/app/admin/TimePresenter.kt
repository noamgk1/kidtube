package com.noam.kidtube.app.admin

import com.noam.kidtube.core.BudgetAccountant
import com.noam.kidtube.core.Clock
import com.noam.kidtube.core.store.BudgetStore
import com.noam.kidtube.core.store.SettingsStore
import com.noam.kidtube.core.today

data class TimeView(
    val dailyLimitMinutes: Int,
    val consumedSeconds: Int,
    val remainingSeconds: Int,
)

class TimePresenter(
    private val settingsStore: SettingsStore,
    private val budgetStore: BudgetStore,
    private val accountant: BudgetAccountant,
    private val clock: Clock,
) {
    fun view(): TimeView {
        val limitMinutes = settingsStore.load().dailyLimitMinutes
        val budget = accountant.forToday(budgetStore.load(clock.today().toString()))
        return TimeView(
            dailyLimitMinutes = limitMinutes,
            consumedSeconds = budget.consumedSeconds,
            remainingSeconds = accountant.remainingSeconds(budget, limitMinutes * 60),
        )
    }

    fun setDailyLimitMinutes(minutes: Int) {
        settingsStore.save(
            settingsStore.load().copy(dailyLimitMinutes = minutes.coerceAtLeast(0)),
        )
    }

    /** Extends today only: the bonus lives on the day's budget record and dies with it. */
    fun grantBonusMinutes(minutes: Int) {
        val budget = accountant.forToday(budgetStore.load(clock.today().toString()))
        budgetStore.save(accountant.grantBonus(budget, minutes * 60))
    }
}
