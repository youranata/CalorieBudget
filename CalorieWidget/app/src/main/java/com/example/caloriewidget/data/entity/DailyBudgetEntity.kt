package com.example.caloriewidget.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "daily_budgets")
data class DailyBudgetEntity(
    @PrimaryKey val date: String,
    val budgetMilliKcal: Long,
    val updatedAt: Long,
    // A non-null total permanently seals this date, including after clock rollback.
    @ColumnInfo(defaultValue = "NULL") val archivedTotalMilliKcal: Long? = null,
    @ColumnInfo(defaultValue = "0") val archivedHasRecords: Boolean = false
)
