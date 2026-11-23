package com.youranata.caloriewidget.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.youranata.caloriewidget.data.entity.IntakeRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IntakeDao {
    @Insert
    suspend fun insert(record: IntakeRecordEntity): Long

    @Update
    suspend fun update(record: IntakeRecordEntity)

    @Delete
    suspend fun delete(record: IntakeRecordEntity)

    @Query("SELECT * FROM intake_records WHERE localDate = :date ORDER BY timestamp ASC, id ASC")
    fun observeForDate(date: String): Flow<List<IntakeRecordEntity>>

    @Query("SELECT * FROM intake_records WHERE localDate = :date ORDER BY timestamp ASC, id ASC")
    suspend fun getForDate(date: String): List<IntakeRecordEntity>

    @Query("SELECT * FROM intake_records WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): IntakeRecordEntity?

    @Query("SELECT COALESCE(SUM(amountMilliKcal), 0) FROM intake_records WHERE localDate = :date")
    suspend fun totalForDate(date: String): Long

    @Query("SELECT localDate AS date, COALESCE(SUM(amountMilliKcal), 0) AS totalMilliKcal, COUNT(*) AS recordCount FROM intake_records GROUP BY localDate ORDER BY localDate DESC")
    fun observeDailyTotals(): Flow<List<DailyTotalRow>>

    @Query("SELECT localDate AS date, COALESCE(SUM(amountMilliKcal), 0) AS totalMilliKcal, COUNT(*) AS recordCount FROM intake_records GROUP BY localDate ORDER BY localDate DESC")
    suspend fun getDailyTotals(): List<DailyTotalRow>
}

data class DailyTotalRow(
    val date: String,
    val totalMilliKcal: Long,
    val recordCount: Int
)
