package com.youranata.caloriewidget.data.dao

import androidx.room.*
import com.youranata.caloriewidget.data.DaySummary
import com.youranata.caloriewidget.data.entity.IntakeRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IntakeDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: IntakeRecordEntity): Long
    @Update suspend fun update(record: IntakeRecordEntity)
    @Delete suspend fun delete(record: IntakeRecordEntity)
    @Query("SELECT * FROM intake_records WHERE id = :id") suspend fun getById(id: Long): IntakeRecordEntity?
    @Query("SELECT * FROM intake_records WHERE submissionKey = :key") suspend fun getBySubmissionKey(key: String): IntakeRecordEntity?
    @Query("SELECT * FROM intake_records WHERE localDate = :date ORDER BY timestamp, id")
    fun observeForDate(date: String): Flow<List<IntakeRecordEntity>>
    @Query("SELECT * FROM intake_records WHERE localDate = :date ORDER BY timestamp, id")
    suspend fun getForDate(date: String): List<IntakeRecordEntity>
    @Query("SELECT COALESCE(SUM(amountMilliKcal), 0) FROM intake_records WHERE localDate = :date")
    suspend fun totalForDate(date: String): Long
    @Query("SELECT b.date, b.budgetMilliKcal, COALESCE(SUM(r.amountMilliKcal), 0) AS totalMilliKcal, b.budgetMilliKcal - COALESCE(SUM(r.amountMilliKcal), 0) AS remainingMilliKcal, COUNT(r.id) AS recordCount FROM daily_budgets b LEFT JOIN intake_records r ON b.date = r.localDate GROUP BY b.date ORDER BY b.date DESC")
    fun observeHistory(): Flow<List<DaySummary>>
}
