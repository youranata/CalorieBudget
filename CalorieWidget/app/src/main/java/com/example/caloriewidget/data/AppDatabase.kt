package com.example.caloriewidget.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.caloriewidget.data.dao.*
import com.example.caloriewidget.data.entity.*

@Database(entities = [IntakeRecordEntity::class, DailyBudgetEntity::class, AppSettingsEntity::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun intakeDao(): IntakeDao
    abstract fun budgetDao(): BudgetDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE intake_records ADD COLUMN submissionKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE intake_records SET submissionKey = 'legacy-' || id")
                db.execSQL("ALTER TABLE intake_records ADD COLUMN zoneId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE intake_records ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE intake_records SET createdAt = timestamp")
                db.execSQL("CREATE UNIQUE INDEX index_intake_records_submissionKey ON intake_records(submissionKey)")
                db.execSQL("CREATE INDEX index_intake_records_localDate_timestamp ON intake_records(localDate, timestamp)")
                db.execSQL("CREATE TABLE IF NOT EXISTS app_settings (id INTEGER NOT NULL, defaultBudgetMilliKcal INTEGER NOT NULL, PRIMARY KEY(id))")
            }
        }
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "calorie_widget.db")
                .addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
