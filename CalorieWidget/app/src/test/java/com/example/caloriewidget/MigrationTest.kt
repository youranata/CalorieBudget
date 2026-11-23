package com.example.caloriewidget

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.caloriewidget.data.AppDatabase
import com.example.caloriewidget.data.CalorieRepository
import com.example.caloriewidget.domain.DateManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35], application = Application::class)
class MigrationTest {
    private fun verifyUpgrade(version: Int) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-${UUID.randomUUID()}.db"
        val today = DateManager.todayString()
        val oldDate = DateManager.today().minusDays(1).toString()
        val zeroDate = DateManager.today().minusDays(2).toString()
        val missingDate = DateManager.today().minusDays(3).toString()
        context.openOrCreateDatabase(name, 0, null).use { old ->
            val extra = if (version == 1) "" else ", submissionKey TEXT NOT NULL DEFAULT '', zoneId TEXT NOT NULL DEFAULT '', createdAt INTEGER NOT NULL DEFAULT 0"
            old.execSQL("CREATE TABLE intake_records (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, amountMilliKcal INTEGER NOT NULL, originalAmountMilli INTEGER NOT NULL, originalUnit TEXT NOT NULL, note TEXT NOT NULL, timestamp INTEGER NOT NULL, localDate TEXT NOT NULL$extra)")
            old.execSQL("CREATE TABLE daily_budgets (date TEXT NOT NULL PRIMARY KEY, budgetMilliKcal INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
            if (version == 2) {
                old.execSQL("CREATE UNIQUE INDEX index_intake_records_submissionKey ON intake_records(submissionKey)")
                old.execSQL("CREATE INDEX index_intake_records_localDate_timestamp ON intake_records(localDate, timestamp)")
                old.execSQL("CREATE TABLE app_settings (id INTEGER NOT NULL PRIMARY KEY, defaultBudgetMilliKcal INTEGER NOT NULL)")
                old.execSQL("INSERT INTO app_settings VALUES (1,1750000)")
            }
            for ((id, amount, date) in listOf(Triple(1, 350000, oldDate), Triple(2, 100000, oldDate), Triple(3, 100000, today), Triple(4, 0, zeroDate))) {
                val tail = if (version == 1) "" else ", 'key-$id', 'UTC', 1000"
                old.execSQL("INSERT INTO intake_records VALUES ($id,$amount,$amount,'kcal','备注$id',1000, ?$tail)", arrayOf(date))
            }
            old.execSQL("INSERT INTO daily_budgets VALUES (?,1800000,1000)", arrayOf(oldDate))
            old.execSQL("INSERT INTO daily_budgets VALUES (?,1900000,1000)", arrayOf(today))
            old.execSQL("INSERT INTO daily_budgets VALUES (?,2000000,1000)", arrayOf(missingDate))
            old.version = version
        }
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3).build()
        try {
            // Migration must archive before the first repository call.
            assertNull(db.intakeDao().getById(1))
            assertNull(db.intakeDao().getById(2))
            assertNull(db.intakeDao().getById(4))
            assertEquals("备注3", db.intakeDao().getById(3)?.note)
            assertEquals(450000L, db.budgetDao().get(oldDate)?.archivedTotalMilliKcal)
            assertEquals(1800000L, db.budgetDao().get(oldDate)?.budgetMilliKcal)
            assertTrue(requireNotNull(db.budgetDao().get(zeroDate)).archivedHasRecords)
            assertFalse(requireNotNull(db.budgetDao().get(missingDate)).archivedHasRecords)
            assertNull(db.budgetDao().get(today)?.archivedTotalMilliKcal)
            val repo = CalorieRepository(db)
            assertEquals(100000L, repo.getSummary(today).totalMilliKcal)
            assertEquals(1900000L, repo.getSummary(today).budgetMilliKcal)
            assertEquals(450000L, repo.getSummary(oldDate).totalMilliKcal)
            if (version == 2) {
                assertEquals("key-3", repo.getRecord(3)?.submissionKey)
                assertEquals(1750000L, db.budgetDao().settings()?.defaultBudgetMilliKcal)
                assertEquals(1750000L, db.budgetDao().get(zeroDate)?.budgetMilliKcal)
            } else assertEquals("legacy-3", repo.getRecord(3)?.submissionKey)
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun versionOneUpgradeKeepsTodayAndArchivesPast() { verifyUpgrade(1) }
    @Test fun versionTwoUpgradePreservesBudgetSettingsAndZeroVersusMissing() { verifyUpgrade(2) }
}
