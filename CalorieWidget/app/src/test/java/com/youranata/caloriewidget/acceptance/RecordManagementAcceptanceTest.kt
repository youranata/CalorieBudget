package com.youranata.caloriewidget.acceptance

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.youranata.caloriewidget.data.*
import com.youranata.caloriewidget.data.entity.DailyBudgetEntity
import com.youranata.caloriewidget.domain.*
import com.youranata.caloriewidget.acceptance.CalorieCalculationAcceptanceTest.Companion.decimalInput
import com.youranata.caloriewidget.acceptance.CalorieCalculationAcceptanceTest.Companion.roundedRatio
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.util.Random
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35], application = Application::class)
class RecordManagementAcceptanceTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: CalorieRepository
    private val today get() = DateManager.todayString()
    private val yesterday get() = DateManager.today().minusDays(1).toString()
    private val zone get() = ZoneId.systemDefault().id
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        repo = CalorieRepository(db)
    }
    @After fun close() { db.close() }
    private fun time(date: String, hour: Int = 12) = DateManager.timestamp(LocalDate.parse(date), hour, 0)
    private suspend fun add(value: String, unit: String = "kcal", date: String = today, key: String = UUID.randomUUID().toString()) =
        repo.addRecord(CalorieMath.parseAmount(value, unit), "来源", time(date), key, date, zone)
    private suspend fun edit(id: Long, value: String, unit: String = "kcal", date: String = today, note: String = "已编辑", hour: Int = 12) =
        repo.updateRecord(id, CalorieMath.parseAmount(value, unit), note, time(date, hour), date, zone)

    /** Compare Kotlin summary, SQL SUM, history SQL projection and rows after every operation. */
    private suspend fun check(date: String, expected: Long, count: Int, budget: Long = 2_000_000L) {
        val actual = repo.getSummary(date)
        assertEquals(DaySummary(date, budget, expected, budget - expected, count), actual)
        assertEquals(expected, db.intakeDao().totalForDate(date))
        assertEquals(expected, db.intakeDao().getForDate(date).sumOf { it.amountMilliKcal })
        assertEquals(actual, repo.observeHistory().first().single { it.date == date })
        assertEquals(count, repo.observeRecords(date).first().size)
    }

    @Test fun specifiedAddEditUnitChangeAndDeleteSequence() = runBlocking {
        check(today, 0, 0)
        val breakfast = add("350"); check(today, 350_000, 1)
        val drink = add("1000", "kJ"); check(today, 589_006, 2)
        assertEquals("1410.99", CalorieMath.milliKcalToDisplay(repo.getSummary(today).remainingMilliKcal))
        edit(breakfast, "500"); check(today, 739_006, 2)
        edit(drink, "2092", "kJ"); check(today, 1_000_000, 2)
        repo.deleteRecord(breakfast); check(today, 500_000, 1)
        repo.deleteRecord(drink); check(today, 0, 0)
    }

    @Test fun editingPreservesIdentityCreationTimeAndOriginalThreeDecimalInput() = runBlocking {
        val id = add("1234.567", "kJ")
        val original = requireNotNull(repo.getRecord(id))
        repeat(12) {
            val current = requireNotNull(repo.getRecord(id))
            edit(id, CalorieMath.inputValue(current.originalAmountMilli), current.originalUnit, note = "备注 $it", hour = 8)
            check(today, 295_069, 1)
        }
        val changed = requireNotNull(repo.getRecord(id))
        assertEquals(id, changed.id)
        assertEquals(original.createdAt, changed.createdAt)
        assertEquals(original.submissionKey, changed.submissionKey)
        assertEquals(1_234_567L, changed.originalAmountMilli)
        assertEquals("kJ", changed.originalUnit)
        assertEquals(time(today, 8), changed.timestamp)
        assertEquals("备注 11", changed.note)
        edit(id, "350", "kcal"); check(today, 350_000, 1)
        edit(id, "1464.4", "kJ"); check(today, 350_000, 1)
    }

    @Test fun movingHistoryBothDirectionsAndDeletingNeverChangesTheOtherDay() = runBlocking {
        db.budgetDao().upsert(DailyBudgetEntity(yesterday, 1_500_000, 0))
        val old = add("400", date = yesterday)
        val oldDrink = add("418.4", "kJ", yesterday)
        val current = add("350")
        check(yesterday, 500_000, 2, 1_500_000); check(today, 350_000, 1)
        edit(current, "250", date = yesterday)
        check(yesterday, 750_000, 3, 1_500_000); check(today, 0, 0)
        edit(current, "1000", "kJ", today)
        check(yesterday, 500_000, 2, 1_500_000); check(today, 239_006, 1)
        repo.deleteRecord(old)
        check(yesterday, 100_000, 1, 1_500_000); check(today, 239_006, 1)
        assertNotNull(repo.getRecord(oldDrink))
        repo.deleteRecord(current)
        check(yesterday, 100_000, 1, 1_500_000); check(today, 0, 0)
    }

    @Test fun zeroOverBudgetAndTimeOnlyEditKeepTotalsAndOrderingCorrect() = runBlocking {
        val zero = add("0")
        check(today, 0, 1)
        assertEquals(0L, Statistics.average(repo.observeHistory().first()))
        val big = add("2200")
        check(today, 2_200_000, 2)
        assertEquals(-200_000L, repo.getSummary(today).remainingMilliKcal)
        edit(big, "2200", note = "  晚餐  ", hour = 23)
        edit(zero, "0", note = "", hour = 6)
        assertEquals(listOf(zero, big), db.intakeDao().getForDate(today).map { it.id })
        assertEquals("晚餐", repo.getRecord(big)?.note)
        check(today, 2_200_000, 2)
        repo.deleteRecord(big); check(today, 0, 1)
        repo.deleteRecord(zero); check(today, 0, 0)
        assertNull(Statistics.average(repo.observeHistory().first()))
    }

    @Test fun repeatedSaveDeleteAndEditOfDeletedRecordNeverDoubleApply() = runBlocking {
        val id = add("350", key = "stable-submit")
        repeat(12) { assertEquals(id, add("350", key = "stable-submit")) }
        check(today, 350_000, 1)
        repeat(5) { edit(id, "500") }; check(today, 500_000, 1)
        repeat(5) { repo.deleteRecord(id) }; check(today, 0, 0)
        assertTrue(runCatching { edit(id, "100", date = yesterday) }.isFailure)
        assertNull(db.budgetDao().get(yesterday))
        check(today, 0, 0)
        assertNull(repo.getRecord(id))
    }

    @Test fun failedEditRollsBackRecordDateAndBudgets() = runBlocking {
        val id = add("350")
        val before = repo.getRecord(id)
        assertTrue(runCatching { edit(id, "900", date = yesterday, note = "x".repeat(501)) }.isFailure)
        assertEquals(before, repo.getRecord(id))
        assertNull(db.budgetDao().get(yesterday))
        check(today, 350_000, 1)
        val invalid = CalorieMath.ParsedAmount(-1, -1, "kcal")
        assertTrue(runCatching { repo.updateRecord(id, invalid, "", time(today), today, zone) }.isFailure)
        assertEquals(before, repo.getRecord(id)); check(today, 350_000, 1)
    }

    @Test fun concurrentDistinctRecordsDuplicateSubmissionsEditsAndDeletesAreConsistent() = runBlocking {
        val ids = (0 until 32).map { index -> async(Dispatchers.Default) {
            if (index % 2 == 0) add("10") else add("41.84", "kJ")
        } }.awaitAll()
        check(today, 320_000, 32)
        val repeated = (0 until 16).map { async(Dispatchers.Default) { add("1000", "kJ", key = "same") } }.awaitAll()
        assertEquals(1, repeated.distinct().size); check(today, 559_006, 33)
        ids.map { id -> async(Dispatchers.Default) { edit(id, "83.68", "kJ") } }.awaitAll()
        check(today, 879_006, 33)
        ids.take(16).flatMap { id -> listOf(async(Dispatchers.Default) { repo.deleteRecord(id) },
            async(Dispatchers.Default) { repo.deleteRecord(id) }) }.awaitAll()
        check(today, 559_006, 17)
        Unit
    }

    @Test fun mixedMutationsPersistAcrossDatabaseCloseAndReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val name = "intake-acceptance-${UUID.randomUUID()}.db"
        var disk = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            val repository = CalorieRepository(disk)
            val a = repository.addRecord(CalorieMath.parseAmount("350", "kcal"), "早餐", time(today), "a")
            val b = repository.addRecord(CalorieMath.parseAmount("1000", "kJ"), "饮料", time(today), "b")
            repository.updateRecord(a, CalorieMath.parseAmount("500", "kcal"), "午餐", time(yesterday), yesterday, zone)
            repository.deleteRecord(b)
            disk.close()
            disk = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            val restored = CalorieRepository(disk)
            assertEquals(500_000L, restored.getSummary(yesterday).totalMilliKcal)
            assertEquals(1_500_000L, restored.getSummary(yesterday).remainingMilliKcal)
            assertEquals(0, restored.getSummary(today).recordCount)
            assertNull(restored.getRecord(b))
            assertEquals("午餐", restored.getRecord(a)?.note)
        } finally { disk.close(); context.deleteDatabase(name) }
        Unit
    }

    @Test fun oneHundredTwentyMutationsMatchIndependentRecordLedgerAfterEveryStep() = runBlocking {
        data class Expected(val amount: Long, val date: String)
        val random = Random(350_239_006)
        val dates = (0..4).map { DateManager.today().minusDays(it.toLong()).toString() }
        val ledger = linkedMapOf<Long, Expected>()
        for (date in dates) repo.ensureDate(date)
        repeat(120) { step ->
            val date = dates[random.nextInt(dates.size)]
            val unit = if (random.nextBoolean()) "kcal" else "kJ"
            val raw = (random.nextInt(5_000_001).toLong() + 10)
            val value = decimalInput(raw)
            val expected = if (unit == "kJ") roundedRatio(raw, 125, 523) else raw
            when (if (ledger.isEmpty()) 0 else random.nextInt(3)) {
                0 -> ledger[add(value, unit, date, "step-$step")] = Expected(expected, date)
                1 -> {
                    val id = ledger.keys.elementAt(random.nextInt(ledger.size))
                    edit(id, value, unit, date); ledger[id] = Expected(expected, date)
                }
                else -> {
                    val id = ledger.keys.elementAt(random.nextInt(ledger.size))
                    repo.deleteRecord(id); ledger.remove(id)
                }
            }
            for (day in dates) {
                val rows = ledger.filterValues { it.date == day }
                check(day, rows.values.sumOf { it.amount }, rows.size)
                assertEquals("record IDs after step $step for $day", rows.keys,
                    db.intakeDao().getForDate(day).map { it.id }.toSet())
            }
        }
    }
}
