package com.youranata.caloriewidget.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.youranata.caloriewidget.domain.CalorieMath

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val defaultBudgetMilliKcal: Long = CalorieMath.DEFAULT_BUDGET_MILLI_KCAL
)
