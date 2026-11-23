package com.youranata.caloriewidget.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.youranata.caloriewidget.data.entity.DailyBudgetEntity

@Dao
interface BudgetDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(budget: DailyBudgetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: DailyBudgetEntity)

    @Update
    suspend fun update(budget: DailyBudgetEntity)

    @Query("SELECT * FROM daily_budgets WHERE date = :date LIMIT 1")
    suspend fun get(date: String): DailyBudgetEntity?

    @Query("SELECT * FROM daily_budgets ORDER BY date DESC")
    suspend fun getAll(): List<DailyBudgetEntity>
}
