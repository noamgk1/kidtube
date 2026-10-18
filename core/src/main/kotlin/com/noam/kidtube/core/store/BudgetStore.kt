package com.noam.kidtube.core.store

import com.noam.kidtube.core.Budget

private const val FILE = "budget.json"

class BudgetStore(private val files: FileStore) {
    fun load(todayIso: String): Budget = files.read(FILE, Budget.serializer()) { Budget(todayIso) }
    fun save(budget: Budget) = files.write(FILE, Budget.serializer(), budget)
}
