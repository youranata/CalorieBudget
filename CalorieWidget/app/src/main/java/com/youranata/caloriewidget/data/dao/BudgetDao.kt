package com.youranata.caloriewidget.data.dao

import androidx.room.*
import com.youranata.caloriewidget.data.entity.AppSettingsEntity
import com.youranata.caloriewidget.data.entity.DailyBudgetEntity

@Dao
interface BudgetDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertIfMissing(budget: DailyBudgetEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(budget: DailyBudgetEntity)
    @Query("SELECT * FROM daily_budgets WHERE date = :date") suspend fun get(date: String): DailyBudgetEntity?
    @Query("SELECT * FROM app_settings WHERE id = 1") suspend fun settings(): AppSettingsEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun setSettings(settings: AppSettingsEntity)
}
