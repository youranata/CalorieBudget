package com.example.caloriewidget.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "intake_records", indices = [Index(value = ["submissionKey"], unique = true), Index(value = ["localDate", "timestamp"])])
data class IntakeRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountMilliKcal: Long,
    val originalAmountMilli: Long,
    val originalUnit: String,
    val note: String,
    val timestamp: Long,
    val localDate: String,
    @ColumnInfo(defaultValue = "''") val submissionKey: String,
    @ColumnInfo(defaultValue = "''") val zoneId: String,
    @ColumnInfo(defaultValue = "0") val createdAt: Long = System.currentTimeMillis()
)
