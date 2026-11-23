package com.example.caloriewidget

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.caloriewidget.data.*
import com.example.caloriewidget.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class RepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: CalorieRepository
    private var currentDay = DateManager.todayString()
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        repository = CalorieRepository(db) { currentDay }
    }
    @After fun close() { db.close() }
    private fun time(date: String) = DateManager.timestamp(LocalDate.parse(date), 12, 0)
    private suspend fun add(amount: String, date: String = DateManager.todayString(), key: String = UUID.randomUUID().toString()) =
        repository.addRecord(CalorieMath.parseAmount(amount, "kcal"), "早餐", time(date), key)

    @Test fun addEditDeleteAndOverBudgetRemainConsistentAndHistoryCannotBeWritten() = runBlocking {
        val today = DateManager.todayString(); val yesterday = DateManager.today().minusDays(1).toString()
        val id = add("2200")
        assertEquals(-200_000L, repository.getSummary(today).remainingMilliKcal)
        assertTrue(runCatching { repository.updateRecord(id, CalorieMath.parseAmount("1000", "kJ"), "午餐", time(yesterday), yesterday, java.time.ZoneId.systemDefault().id) }.isFailure)
        repository.updateRecord(id, CalorieMath.parseAmount("1000", "kJ"), "午餐", time(today), today, java.time.ZoneId.systemDefault().id)
        assertEquals(239006L, repository.getSummary(today).totalMilliKcal)
        repository.deleteRecord(id)
        assertEquals(0L, repository.getSummary(today).totalMilliKcal)
        assertNull(db.budgetDao().get(yesterday))
    }
    @Test fun rapidConcurrentWritesAndSameSubmissionAreSafe() = runBlocking {
        val ids = (1..24).map { async(Dispatchers.Default) { add("10") } }.awaitAll()
        assertEquals(24, ids.distinct().size)
        val same = (1..12).map { async(Dispatchers.Default) { add("5", key = "one-submission") } }.awaitAll()
        assertEquals(1, same.distinct().size)
        assertEquals(245_000L, repository.getSummary(DateManager.todayString()).totalMilliKcal)
        assertEquals(25, repository.getSummary(DateManager.todayString()).recordCount)
    }
    @Test fun budgetChangesApplyTodayAndNewSnapshotsOnly() = runBlocking {
        val yesterday = DateManager.today().minusDays(1).toString()
        currentDay = yesterday
        repository.ensureDate(yesterday); add("500", yesterday)
        currentDay = DateManager.todayString()
        repository.setDefaultBudget(1_800_000L)
        assertEquals(1_800_000L, repository.getSummary(DateManager.todayString()).budgetMilliKcal)
        assertEquals(2_000_000L, repository.getSummary(yesterday).budgetMilliKcal)
        currentDay = DateManager.today().plusDays(1).toString()
        assertEquals(1_800_000L, repository.getSummary(currentDay).budgetMilliKcal)
    }
    @Test fun repeatedDateChecksKeepArchivedTotalsAndZeroIsDistinctFromMissing() = runBlocking {
        val old = DateManager.today().minusDays(12).toString()
        currentDay = old
        add("0", old)
        currentDay = DateManager.todayString()
        repeat(5) { repository.ensureDate() }
        assertEquals(0, repository.getSummary(old).recordCount)
        assertTrue(repository.getSummary(old).hasRecords)
        assertTrue(repository.getSummary(old).isArchived)
        assertEquals(0L, repository.getSummary(old).totalMilliKcal)
        assertEquals(0, repository.getSummary(DateManager.todayString()).recordCount)
        val history = repository.observeHistory().first()
        assertEquals(1, Statistics.recorded(history).size)
    }
    @Test fun failedMutationRollsBackItsBudgetSnapshot() = runBlocking {
        val old = DateManager.today().minusDays(90).toString()
        assertTrue(runCatching { repository.addRecord(CalorieMath.parseAmount("10", "kcal"), "x".repeat(501), time(old), "bad") }.isFailure)
        assertNull(db.budgetDao().get(old))
        assertNull(db.intakeDao().getBySubmissionKey("bad"))
    }
    @Test fun persistentDatabaseSurvivesReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val name = "persistence-${UUID.randomUUID()}.db"
        var disk = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        val repo = CalorieRepository(disk)
        repo.setDefaultBudget(1_900_000)
        val id = repo.addRecord(CalorieMath.parseAmount("350", "kcal"), "持久化", time(DateManager.todayString()), "persistent")
        disk.close()
        disk = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        val restored = CalorieRepository(disk)
        assertEquals(350_000L, restored.getSummary(DateManager.todayString()).totalMilliKcal)
        assertEquals(1_900_000L, restored.getSummary(DateManager.todayString()).budgetMilliKcal)
        assertEquals("持久化", restored.getRecord(id)?.note)
        disk.close(); context.deleteDatabase(name); Unit
    }
}
