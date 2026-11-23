package com.example.caloriewidget.data.dao

import androidx.room.*
import com.example.caloriewidget.data.entity.AppSettingsEntity
import com.example.caloriewidget.data.entity.DailyBudgetEntity
import com.example.caloriewidget.data.HistoryArchiveSql

@Dao
interface BudgetDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertIfMissing(budget: DailyBudgetEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(budget: DailyBudgetEntity)
    @Query("SELECT * FROM daily_budgets WHERE date = :date") suspend fun get(date: String): DailyBudgetEntity?
    @Query("SELECT * FROM app_settings WHERE id = 1") suspend fun settings(): AppSettingsEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun setSettings(settings: AppSettingsEntity)
    @Query(HistoryArchiveSql.ENSURE_BUDGETS)
    suspend fun ensureLegacyBudgets(today: String, defaultBudget: Long, updatedAt: Long)
    @Query(HistoryArchiveSql.FREEZE)
    suspend fun freezeHistory(today: String, updatedAt: Long)
}
