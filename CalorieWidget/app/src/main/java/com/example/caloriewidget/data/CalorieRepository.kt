package com.example.caloriewidget.data

import androidx.room.withTransaction
import com.example.caloriewidget.data.entity.*
import com.example.caloriewidget.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.withContext
import java.time.ZoneId

class CalorieRepository(
    private val database: AppDatabase,
    private val currentDate: () -> String = DateManager::todayString
) {
    private val records = database.intakeDao()
    private val budgets = database.budgetDao()

    // All snapshot creation and archival run inside a Room transaction.
    private suspend fun ensureSnapshot(date: String) {
        DateManager.parseDate(date)
        if (budgets.get(date) == null) {
            val default = budgets.settings()?.defaultBudgetMilliKcal ?: CalorieMath.DEFAULT_BUDGET_MILLI_KCAL
            budgets.insertIfMissing(DailyBudgetEntity(date, default, System.currentTimeMillis()))
        }
    }
    private suspend fun checkDates(): String {
        val today = currentDate()
        DateManager.parseDate(today)
        val default = budgets.settings()?.defaultBudgetMilliKcal ?: CalorieMath.DEFAULT_BUDGET_MILLI_KCAL
        val now = System.currentTimeMillis()
        budgets.ensureLegacyBudgets(today, default, now)
        budgets.freezeHistory(today, now)
        records.deleteHistoricalDetails(today)
        ensureSnapshot(today)
        return today
    }
    suspend fun ensureDate(date: String = currentDate()) = withContext(Dispatchers.IO) {
        DateManager.parseDate(date)
        // Looking up an unknown old date must not invent a budget or a zero-intake day.
        database.withTransaction { checkDates(); Unit }
    }
    suspend fun getSummary(date: String): DaySummary = withContext(Dispatchers.IO) {
        database.withTransaction {
            checkDates()
            val snapshot = requireNotNull(budgets.get(date)) { "该日期没有保存的数据" }
            val budget = snapshot.budgetMilliKcal
            val entries = records.getForDate(date)
            val total = snapshot.archivedTotalMilliKcal ?: entries.sumOf { it.amountMilliKcal }
            DaySummary(date, budget, total, budget - total, entries.size,
                if (snapshot.archivedTotalMilliKcal != null) snapshot.archivedHasRecords else entries.isNotEmpty(),
                snapshot.archivedTotalMilliKcal != null)
        }
    }
    fun observeRecords(date: String): Flow<List<IntakeRecordEntity>> = flow {
        ensureDate()
        emitAll(records.observeForDate(date))
    }
    fun observeHistory(): Flow<List<DaySummary>> = flow {
        ensureDate()
        emitAll(records.observeHistory())
    }
    suspend fun getRecord(id: Long) = withContext(Dispatchers.IO) {
        database.withTransaction { checkDates(); records.getById(id) }
    }
    suspend fun addRecord(parsed: CalorieMath.ParsedAmount, note: String, timestamp: Long,
                          submissionKey: String, localDate: String = DateManager.dateFromTimestamp(timestamp),
                          zoneId: String = ZoneId.systemDefault().id): Long = withContext(Dispatchers.IO) {
        ensureDate()
        database.withTransaction {
            val today = checkDates()
            requireWritable(localDate, today)
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
        ensureDate()
        database.withTransaction {
            val today = checkDates()
            requireWritable(localDate, today)
            val old = records.getById(id) ?: error("记录已归档或删除")
            requireWritable(old.localDate, today)
            validate(parsed, note)
            val other = records.totalForDate(localDate) - if (old.localDate == localDate) old.amountMilliKcal else 0L
            Math.addExact(other, parsed.amountMilliKcal)
            records.update(old.copy(amountMilliKcal = parsed.amountMilliKcal, originalAmountMilli = parsed.originalAmountMilli,
                originalUnit = parsed.originalUnit, note = note.trim(), timestamp = timestamp, localDate = localDate, zoneId = zoneId))
        }
    }
    suspend fun deleteRecord(id: Long) = withContext(Dispatchers.IO) {
        ensureDate()
        database.withTransaction {
            val today = checkDates()
            records.getById(id)?.let { requireWritable(it.localDate, today); records.delete(it) }
        }
    }
    suspend fun setDefaultBudget(budgetMilliKcal: Long) = withContext(Dispatchers.IO) {
        require(budgetMilliKcal in 1..CalorieMath.MAX_AMOUNT_MILLI_KCAL) { "预算超出允许范围" }
        ensureDate()
        database.withTransaction {
            val today = checkDates()
            requireWritable(today, today)
            budgets.setSettings(AppSettingsEntity(defaultBudgetMilliKcal = budgetMilliKcal))
            budgets.upsert(DailyBudgetEntity(today, budgetMilliKcal, System.currentTimeMillis()))
        }
    }
    private suspend fun requireWritable(date: String, today: String) {
        require(date == today && budgets.get(date)?.archivedTotalMilliKcal == null) {
            "仅可修改当天记录；历史汇总已归档，只能查看"
        }
    }
    private fun validate(parsed: CalorieMath.ParsedAmount, note: String) {
        require(parsed.amountMilliKcal in 0..CalorieMath.MAX_AMOUNT_MILLI_KCAL)
        require(note.length <= 500) { "备注最多 500 字" }
    }
}

data class DaySummary(val date: String, val budgetMilliKcal: Long, val totalMilliKcal: Long,
                      val remainingMilliKcal: Long, val recordCount: Int,
                      val hasRecords: Boolean = recordCount > 0, val isArchived: Boolean = false)
