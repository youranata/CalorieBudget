package com.youranata.caloriewidget.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.youranata.caloriewidget.data.dao.BudgetDao
import com.youranata.caloriewidget.data.dao.IntakeDao
import com.youranata.caloriewidget.data.entity.DailyBudgetEntity
import com.youranata.caloriewidget.data.entity.IntakeRecordEntity

@Database(
    entities = [IntakeRecordEntity::class, DailyBudgetEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun intakeDao(): IntakeDao
    abstract fun budgetDao(): BudgetDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "calorie_widget.db"
            ).build().also { INSTANCE = it }
        }
    }
}
