package com.example.caloriewidget.data

/** Shared by the transactional runtime archive and the 2 -> 3 upgrade. */
object HistoryArchiveSql {
    const val ENSURE_BUDGETS = "INSERT OR IGNORE INTO daily_budgets (date, budgetMilliKcal, updatedAt) SELECT localDate, :defaultBudget, :updatedAt FROM intake_records WHERE localDate < :today GROUP BY localDate"
    const val FREEZE = "UPDATE daily_budgets SET archivedTotalMilliKcal = COALESCE((SELECT SUM(amountMilliKcal) FROM intake_records WHERE localDate = daily_budgets.date), 0), archivedHasRecords = EXISTS(SELECT 1 FROM intake_records WHERE localDate = daily_budgets.date), updatedAt = :updatedAt WHERE date < :today AND archivedTotalMilliKcal IS NULL"
    const val DELETE_DETAILS = "DELETE FROM intake_records WHERE localDate < :today"
}
