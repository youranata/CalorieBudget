package com.youranata.caloriewidget

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.youranata.caloriewidget.data.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class MigrationTest {
    @Test fun migrationPreservesLegacyRecordsAndBudgets() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration.db"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, 0, null).use { old ->
            old.execSQL("CREATE TABLE intake_records (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, amountMilliKcal INTEGER NOT NULL, originalAmountMilli INTEGER NOT NULL, originalUnit TEXT NOT NULL, note TEXT NOT NULL, timestamp INTEGER NOT NULL, localDate TEXT NOT NULL)")
            old.execSQL("CREATE TABLE daily_budgets (date TEXT NOT NULL PRIMARY KEY, budgetMilliKcal INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
            old.execSQL("INSERT INTO intake_records VALUES (1,350000,350000,'kcal','早餐',1000,'2026-11-22')")
            old.execSQL("INSERT INTO intake_records VALUES (2,100000,100000,'kcal','午餐',2000,'2026-11-22')")
            old.execSQL("INSERT INTO daily_budgets VALUES ('2026-11-22',1800000,1000)")
            old.version = 1
        }
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(AppDatabase.MIGRATION_1_2).build()
        assertEquals("早餐", db.intakeDao().getById(1)?.note)
        assertEquals(450000L, db.intakeDao().totalForDate("2026-11-22"))
        assertEquals(1800000L, db.budgetDao().get("2026-11-22")?.budgetMilliKcal)
        assertNotEquals(db.intakeDao().getById(1)?.submissionKey, db.intakeDao().getById(2)?.submissionKey)
        db.close(); context.deleteDatabase(name); Unit
    }
}
