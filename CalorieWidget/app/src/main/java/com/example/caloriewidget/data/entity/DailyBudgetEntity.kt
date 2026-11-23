package com.example.caloriewidget.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_budgets")
data class DailyBudgetEntity(
    @PrimaryKey val date: String,
    val budgetMilliKcal: Long,
    val updatedAt: Long
)
