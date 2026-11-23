package com.youranata.caloriewidget.data

import androidx.room.withTransaction
import com.youranata.caloriewidget.data.entity.*
import com.youranata.caloriewidget.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.ZoneId

class CalorieRepository(private val database: AppDatabase) {
    private val records = database.intakeDao()
    private val budgets = database.budgetDao()

    // Only called inside a transaction. Nothing ever clears records at midnight.
    private suspend fun ensureSnapshot(date: String) {
        DateManager.parseDate(date)
        if (budgets.get(date) == null) {
            val default = budgets.settings()?.defaultBudgetMilliKcal ?: CalorieMath.DEFAULT_BUDGET_MILLI_KCAL
            budgets.insertIfMissing(DailyBudgetEntity(date, default, System.currentTimeMillis()))
        }
    }
    private suspend fun checkDates(date: String = DateManager.todayString()) {
        ensureSnapshot(DateManager.todayString())
        ensureSnapshot(date)
    }
    suspend fun ensureDate(date: String = DateManager.todayString()) = withContext(Dispatchers.IO) {
        database.withTransaction { checkDates(date) }
    }
    suspend fun getSummary(date: String): DaySummary = withContext(Dispatchers.IO) {
        database.withTransaction {
            checkDates(date)
            val budget = requireNotNull(budgets.get(date)).budgetMilliKcal
            val entries = records.getForDate(date)
            val total = entries.sumOf { it.amountMilliKcal }
            DaySummary(date, budget, total, budget - total, entries.size)
        }
    }
    fun observeRecords(date: String) = records.observeForDate(date)
    fun observeHistory(): Flow<List<DaySummary>> = records.observeHistory()
    suspend fun getRecord(id: Long) = withContext(Dispatchers.IO) {
        database.withTransaction { checkDates(); records.getById(id) }
    }
    suspend fun addRecord(parsed: CalorieMath.ParsedAmount, note: String, timestamp: Long,
                          submissionKey: String, localDate: String = DateManager.dateFromTimestamp(timestamp),
                          zoneId: String = ZoneId.systemDefault().id): Long = withContext(Dispatchers.IO) {
        database.withTransaction {
            checkDates(localDate)
            require(submissionKey.isNotBlank())
            records.getBySubmissionKey(submissionKey)?.let { return@withTransaction it.id }
            validate(parsed, note)
            Math.addExact(records.totalForDate(localDate), parsed.amountMilliKcal)
            records.insert(IntakeRecordEntity(amountMilliKcal = parsed.amountMilliKcal,
                originalAmountMilli = parsed.originalAmountMilli, originalUnit = parsed.originalUnit,
                note = note.trim(), timestamp = timestamp, localDate = localDate,
                submissionKey = submissionKey, zoneId = zoneId))
        }
    }
    suspend fun updateRecord(id: Long, parsed: CalorieMath.ParsedAmount, note: String, timestamp: Long,
                             localDate: String, zoneId: String) = withContext(Dispatchers.IO) {
        database.withTransaction {
            checkDates(localDate)
            val old = records.getById(id) ?: error("记录已被删除")
            validate(parsed, note)
            val other = records.totalForDate(localDate) - if (old.localDate == localDate) old.amountMilliKcal else 0L
            Math.addExact(other, parsed.amountMilliKcal)
            records.update(old.copy(amountMilliKcal = parsed.amountMilliKcal, originalAmountMilli = parsed.originalAmountMilli,
                originalUnit = parsed.originalUnit, note = note.trim(), timestamp = timestamp, localDate = localDate, zoneId = zoneId))
        }
    }
    suspend fun deleteRecord(id: Long) = withContext(Dispatchers.IO) {
        database.withTransaction { checkDates(); records.getById(id)?.let { records.delete(it) } }
    }
    suspend fun setDefaultBudget(budgetMilliKcal: Long) = withContext(Dispatchers.IO) {
        require(budgetMilliKcal in 1..CalorieMath.MAX_AMOUNT_MILLI_KCAL) { "预算超出允许范围" }
        database.withTransaction {
            checkDates()
            budgets.setSettings(AppSettingsEntity(defaultBudgetMilliKcal = budgetMilliKcal))
            budgets.upsert(DailyBudgetEntity(DateManager.todayString(), budgetMilliKcal, System.currentTimeMillis()))
        }
    }
    private fun validate(parsed: CalorieMath.ParsedAmount, note: String) {
        require(parsed.amountMilliKcal in 0..CalorieMath.MAX_AMOUNT_MILLI_KCAL)
        require(note.length <= 500) { "备注最多 500 字" }
    }
}

data class DaySummary(val date: String, val budgetMilliKcal: Long, val totalMilliKcal: Long,
                      val remainingMilliKcal: Long, val recordCount: Int)
