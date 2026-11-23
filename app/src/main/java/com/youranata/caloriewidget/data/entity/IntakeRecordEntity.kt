package com.youranata.caloriewidget.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "intake_records")
data class IntakeRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Unified amount in milli-kcal (1 kcal = 1000 milli-kcal). */
    val amountMilliKcal: Long,
    /** Original value in thousandths of the selected input unit. */
    val originalAmountMilli: Long,
    val originalUnit: String,
    val note: String,
    val timestamp: Long,
    val localDate: String
)
