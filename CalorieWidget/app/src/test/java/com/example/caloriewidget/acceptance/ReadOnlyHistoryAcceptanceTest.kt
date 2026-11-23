package com.example.caloriewidget.acceptance

import android.app.Application
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.example.caloriewidget.data.*
import com.example.caloriewidget.data.entity.*
import com.example.caloriewidget.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Requirement-based acceptance: simulate dates, inspect stored rows, and compare independent totals. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35], application = Application::class)
class ReadOnlyHistoryAcceptanceTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: CalorieRepository
    private var day = LocalDate.of(2026, 1, 10)
    private val zone get() = ZoneId.systemDefault().id
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        repo = CalorieRepository(db) { day.toString() }
    }
    @After fun close() { db.close() }
    private suspend fun add(value: String, unit: String = "kcal", date: String = day.toString(), key: String = UUID.randomUUID().toString()) =
        repo.addRecord(CalorieMath.parseAmount(value, unit), "来源备注", DateManager.timestamp(LocalDate.parse(date), 12, 0), key, date, zone)

    @Test fun midnightPreservesExactlyTheBudgetAndTotalWhileRemovingAllDetails() = runBlocking {
        repo.setDefaultBudget(1_800_000)
        val a = add("350"); val b = add("1000", "kJ")
        val old = day.toString()
        val before = repo.getSummary(old)
        assertEquals(589_006L, before.totalMilliKcal)
        day = day.plusDays(1)
        assertEquals(0L, repo.getSummary(day.toString()).totalMilliKcal)
        val archived = repo.getSummary(old)
        assertEquals(1_800_000L, archived.budgetMilliKcal)
        assertEquals(589_006L, archived.totalMilliKcal)
        assertEquals(1_210_994L, archived.remainingMilliKcal)
        assertTrue(archived.isArchived); assertTrue(archived.hasRecords)
        assertEquals(0, archived.recordCount)
        assertTrue(db.intakeDao().getForDate(old).isEmpty())
        assertNull(repo.getRecord(a)); assertNull(repo.getRecord(b))
        val snapshot = db.budgetDao().get(old)
        repeat(10) { repo.ensureDate(); repo.getSummary(old); repo.observeHistory().first() }
        assertEquals(snapshot, db.budgetDao().get(old))
    }

    @Test fun historicalWritesAndStaleFormSubmissionsCannotChangeEitherDay() = runBlocking {
        val oldId = add("2200", key = "old-token"); val old = day.toString()
        day = day.plusDays(1)
        val current = add("100")
        val frozen = repo.getSummary(old)
        assertEquals(-200_000L, frozen.remainingMilliKcal)
        val amount = CalorieMath.parseAmount("999", "kcal")
        assertTrue(runCatching { add("999", date = old, key = "old-token") }.isFailure)
        assertTrue(runCatching { add("999", date = day.plusDays(1).toString()) }.isFailure)
        assertTrue(runCatching { repo.updateRecord(current, amount, "跨日移动", 0, old, zone) }.isFailure)
        assertTrue(runCatching { repo.updateRecord(oldId, amount, "已归档", 0, day.toString(), zone) }.isFailure)
        repo.deleteRecord(oldId)
        repo.setDefaultBudget(1_500_000)
        assertEquals(frozen, repo.getSummary(old))
        assertEquals(100_000L, repo.getSummary(day.toString()).totalMilliKcal)
        repo.updateRecord(current, amount, "仍可改今天", 0, day.toString(), zone)
        assertEquals(999_000L, repo.getSummary(day.toString()).totalMilliKcal)
        repo.deleteRecord(current)
        assertEquals(0L, repo.getSummary(day.toString()).totalMilliKcal)
    }

    @Test fun dateRollbackNeverReopensAnArchivedDateOrChangesItsGoal() = runBlocking {
        val original = day
        add("350")
        day = day.plusDays(1); repo.ensureDate()
        val frozen = repo.getSummary(original.toString())
        day = original
        assertTrue(runCatching { add("500") }.isFailure)
        assertTrue(runCatching { repo.setDefaultBudget(500_000) }.isFailure)
        assertEquals(frozen, repo.getSummary(original.toString()))
        assertEquals(2_000_000L, db.budgetDao().settings()?.defaultBudgetMilliKcal ?: 2_000_000L)
        assertTrue(db.intakeDao().getForDate(original.toString()).isEmpty())
    }

    @Test fun multipleMissedMidnightsDoNotInventZeroIntakeDaysOrChangeZeroMeaning() = runBlocking {
        val zeroDay = day.toString(); add("0")
        day = day.plusDays(1); repo.ensureDate()
        val missing = day.toString()
        day = day.plusDays(10); add("100")
        repo.ensureDate(day.minusDays(4).toString())
        assertNull(db.budgetDao().get(day.minusDays(4).toString()))
        assertTrue(runCatching { repo.getSummary(day.minusDays(4).toString()) }.isFailure)
        val history = repo.observeHistory().first()
        assertEquals(setOf(zeroDay, missing, day.toString()), history.map { it.date }.toSet())
        assertTrue(history.single { it.date == zeroDay }.hasRecords)
        assertFalse(history.single { it.date == missing }.hasRecords)
        assertEquals(50_000L, Statistics.average(history))
    }

    @Test fun chartsAndEveryStatisticsRangeKeepIdenticalNumbersAfterArchival() = runBlocking {
        // Seed old-style records, then independently compare all chart inputs and statistics.
        for (offset in 0L..100L) {
            val date = day.minusDays(offset).toString()
            db.budgetDao().upsert(DailyBudgetEntity(date, 1_500_000 + offset * 1_000, 0))
            if (offset % 7L != 0L) {
                db.intakeDao().insert(IntakeRecordEntity(amountMilliKcal = offset % 5L * 700_000,
                    originalAmountMilli = offset % 5L * 700_000, originalUnit = "kcal", note = "应被删除",
                    timestamp = offset, localDate = date, submissionKey = "seed-$offset", zoneId = zone))
            }
        }
        val before = db.intakeDao().observeHistory().first()
        val after = repo.observeHistory().first()
        assertEquals(before.map { listOf(it.date, it.budgetMilliKcal, it.totalMilliKcal, it.remainingMilliKcal, it.hasRecords) },
            after.map { listOf(it.date, it.budgetMilliKcal, it.totalMilliKcal, it.remainingMilliKcal, it.hasRecords) })
        for (range in listOf(7L, 30L, 90L, null)) {
            val previous = Statistics.range(before, range, day)
            val archived = Statistics.range(after, range, day)
            assertEquals(Statistics.average(previous), Statistics.average(archived))
            assertEquals(Statistics.recorded(previous).map { it.totalMilliKcal }.sorted(), Statistics.recorded(archived).map { it.totalMilliKcal }.sorted())
            assertEquals(previous.count { it.hasRecords && it.totalMilliKcal > it.budgetMilliKcal }, archived.count { it.hasRecords && it.totalMilliKcal > it.budgetMilliKcal })
        }
        assertTrue(after.filter { it.date < day.toString() }.all { it.isArchived && it.recordCount == 0 })
    }

    @Test fun failedDetailDeletionRollsBackTheWholeArchiveThenRetriesWithoutLoss() = runBlocking {
        val old = day.toString(); add("350")
        day = day.plusDays(1)
        db.withTransaction {
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_archive BEFORE DELETE ON intake_records BEGIN SELECT RAISE(ABORT, 'injected archive failure'); END")
        }
        assertTrue(runCatching { repo.ensureDate() }.isFailure)
        assertNull(db.budgetDao().get(old)?.archivedTotalMilliKcal)
        assertEquals(350_000L, db.intakeDao().totalForDate(old))
        db.withTransaction { db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_archive") }
        repo.ensureDate()
        assertEquals(350_000L, repo.getSummary(old).totalMilliKcal)
        assertTrue(db.intakeDao().getForDate(old).isEmpty())
    }

    @Test fun concurrentRefreshAndAddsDoNotRearchiveOrLoseIntake() = runBlocking {
        repeat(12) { add("10") }
        val old = day.toString(); day = day.plusDays(1)
        (0..23).map { index -> async(Dispatchers.Default) {
            if (index % 2 == 0) repo.ensureDate() else add("5")
        } }.awaitAll()
        assertEquals(120_000L, repo.getSummary(old).totalMilliKcal)
        assertEquals(60_000L, repo.getSummary(day.toString()).totalMilliKcal)
        assertEquals(12, repo.getSummary(day.toString()).recordCount)
        assertTrue(db.intakeDao().getForDate(old).isEmpty())
    }

    @Test fun archiveSurvivesDatabaseReopenWithTodayStillEditable() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val name = "archive-${UUID.randomUUID()}.db"
        var disk = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            var repository = CalorieRepository(disk) { day.toString() }
            val date = day.toString()
            repository.addRecord(CalorieMath.parseAmount("350", "kcal"), "旧备注", 0, "old", date, zone)
            day = day.plusDays(1); repository.ensureDate()
            disk.close()
            disk = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            repository = CalorieRepository(disk) { day.toString() }
            assertEquals(350_000L, repository.getSummary(date).totalMilliKcal)
            assertTrue(disk.intakeDao().getForDate(date).isEmpty())
            repository.addRecord(CalorieMath.parseAmount("1000", "kJ"), "今天", 0, "new", day.toString(), zone)
            assertEquals(239_006L, repository.getSummary(day.toString()).totalMilliKcal)
        } finally { disk.close(); context.deleteDatabase(name) }
    }
}
