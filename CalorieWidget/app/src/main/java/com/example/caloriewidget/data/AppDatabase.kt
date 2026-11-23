package com.example.caloriewidget.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.caloriewidget.data.dao.*
import com.example.caloriewidget.data.entity.*
import com.example.caloriewidget.domain.CalorieMath
import com.example.caloriewidget.domain.DateManager

@Database(entities = [IntakeRecordEntity::class, DailyBudgetEntity::class, AppSettingsEntity::class], version = 3, exportSchema = true)
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
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE daily_budgets ADD COLUMN archivedTotalMilliKcal INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE daily_budgets ADD COLUMN archivedHasRecords INTEGER NOT NULL DEFAULT 0")
                val today = DateManager.todayString()
                val now = System.currentTimeMillis()
                val default = db.query("SELECT defaultBudgetMilliKcal FROM app_settings WHERE id = 1").use {
                    if (it.moveToFirst()) it.getLong(0) else CalorieMath.DEFAULT_BUDGET_MILLI_KCAL
                }
                db.execSQL(HistoryArchiveSql.ENSURE_BUDGETS, arrayOf(default, now, today))
                db.execSQL(HistoryArchiveSql.FREEZE, arrayOf(now, today))
                db.execSQL(HistoryArchiveSql.DELETE_DETAILS, arrayOf(today))
            }
        }
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "calorie_widget.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }
    }
}
