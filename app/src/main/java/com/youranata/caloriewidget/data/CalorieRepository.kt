package com.youranata.caloriewidget.data

import androidx.room.withTransaction
import com.youranata.caloriewidget.data.dao.DailyTotalRow
import com.youranata.caloriewidget.data.entity.DailyBudgetEntity
import com.youranata.caloriewidget.data.entity.IntakeRecordEntity
import com.youranata.caloriewidget.domain.CalorieMath
import com.youranata.caloriewidget.domain.DateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.LocalDate

class CalorieRepository(private val database: AppDatabase) {
    private val intakeDao = database.intakeDao()
    private val budgetDao = database.budgetDao()

    suspend fun ensureDate(date: String = DateManager.todayString()) = withContext(Dispatchers.IO) {
        database.withTransaction {
            budgetDao.insertIfMissing(DailyBudgetEntity(date, CalorieMath.DEFAULT_BUDGET_MILLI_KCAL, System.currentTimeMillis()))
        }
    }

    suspend fun getSummary(date: String): DaySummary = withContext(Dispatchers.IO) {
        ensureDate(date)
        val budget = budgetDao.get(date)?.budgetMilliKcal ?: CalorieMath.DEFAULT_BUDGET_MILLI_KCAL
        val total = intakeDao.totalForDate(date)
        DaySummary(date, budget, total, budget - total)
    }

    fun observeRecords(date: String): Flow<List<IntakeRecordEntity>> = intakeDao.observeForDate(date)
    fun observeDailyTotals(): Flow<List<DailyTotalRow>> = intakeDao.observeDailyTotals()
    suspend fun getDailyTotals(): List<DailyTotalRow> = withContext(Dispatchers.IO) { intakeDao.getDailyTotals() }
    suspend fun getRecords(date: String): List<IntakeRecordEntity> = withContext(Dispatchers.IO) { intakeDao.getForDate(date) }
    suspend fun getRecord(id: Long): IntakeRecordEntity? = withContext(Dispatchers.IO) { intakeDao.getById(id) }

    suspend fun addRecord(parsed: CalorieMath.ParsedAmount, note: String, timestamp: Long) = withContext(Dispatchers.IO) {
        val date = DateManager.dateFromTimestamp(timestamp)
        database.withTransaction {
            budgetDao.insertIfMissing(DailyBudgetEntity(date, CalorieMath.DEFAULT_BUDGET_MILLI_KCAL, System.currentTimeMillis()))
            intakeDao.insert(IntakeRecordEntity(
                amountMilliKcal = parsed.amountMilliKcal,
                originalAmountMilli = parsed.originalAmountMilli,
                originalUnit = parsed.originalUnit,
                note = note.trim(),
                timestamp = timestamp,
                localDate = date
            ))
        }
    }

    suspend fun updateRecord(id: Long, parsed: CalorieMath.ParsedAmount, note: String, timestamp: Long) = withContext(Dispatchers.IO) {
        val old = intakeDao.getById(id) ?: throw IllegalArgumentException("记录不存在")
        val date = DateManager.dateFromTimestamp(timestamp)
        database.withTransaction {
            budgetDao.insertIfMissing(DailyBudgetEntity(date, CalorieMath.DEFAULT_BUDGET_MILLI_KCAL, System.currentTimeMillis()))
            intakeDao.update(old.copy(
                amountMilliKcal = parsed.amountMilliKcal,
                originalAmountMilli = parsed.originalAmountMilli,
                originalUnit = parsed.originalUnit,
                note = note.trim(),
                timestamp = timestamp,
                localDate = date
            ))
        }
    }

    suspend fun deleteRecord(id: Long) = withContext(Dispatchers.IO) {
        database.withTransaction { intakeDao.getById(id)?.let { intakeDao.delete(it) } }
    }

    suspend fun setBudget(date: String, budgetMilliKcal: Long) = withContext(Dispatchers.IO) {
        require(budgetMilliKcal > 0) { "预算必须大于 0" }
        database.withTransaction {
            budgetDao.upsert(DailyBudgetEntity(date, budgetMilliKcal, System.currentTimeMillis()))
        }
    }

    suspend fun getBudget(date: String): Long = withContext(Dispatchers.IO) {
        budgetDao.get(date)?.budgetMilliKcal ?: CalorieMath.DEFAULT_BUDGET_MILLI_KCAL
    }

    suspend fun getHistory(): List<DaySummary> = withContext(Dispatchers.IO) {
        val rows = intakeDao.getDailyTotals()
        rows.map { row ->
            val budget = budgetDao.get(row.date)?.budgetMilliKcal ?: CalorieMath.DEFAULT_BUDGET_MILLI_KCAL
            DaySummary(row.date, budget, row.totalMilliKcal, budget - row.totalMilliKcal, row.recordCount)
        }
    }

    suspend fun getHistorySince(days: Long): List<DaySummary> = withContext(Dispatchers.IO) {
        val cutoff = LocalDate.now().minusDays(days - 1).toString()
        getHistory().filter { it.date >= cutoff }
    }
}

data class DaySummary(
    val date: String,
    val budgetMilliKcal: Long,
    val totalMilliKcal: Long,
    val remainingMilliKcal: Long,
    val recordCount: Int = 0
)
