package com.example.caloriewidget.acceptance

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ApplicationProvider
import com.example.caloriewidget.*
import com.example.caloriewidget.domain.*
import com.example.caloriewidget.data.CalorieRepository
import com.example.caloriewidget.widget.CalorieWidgetProvider
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.time.LocalDate

/** Exercise real buttons, forms, view-model saves, Room and rendered totals; no mocked repository. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35], application = CalorieWidgetApp::class)
class RecordManagementUiAcceptanceTest {
    private lateinit var app: CalorieWidgetApp
    private val controllers = mutableListOf<ActivityController<*>>()
    private val today get() = DateManager.todayString()
    @Before fun setup() {
        app = ApplicationProvider.getApplicationContext()
        runBlocking(Dispatchers.IO) { app.database.clearAllTables() }
    }
    @After fun cleanup() {
        controllers.asReversed().forEach { it.pause().stop().destroy() }
        controllers.clear()
        shadowOf(Looper.getMainLooper()).idle()
        app.appScope.cancel()
    }
    private fun await(message: String, check: () -> Boolean) {
        val deadline = System.nanoTime() + 15_000_000_000L
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (check()) return
            Thread.sleep(5)
        }
        fail(message)
    }
    private fun summary(date: String = today) = runBlocking { app.repository.getSummary(date) }
    private fun input(unit: String = "kcal", id: Long? = null, date: String? = null): InputActivity {
        val intent = Intent(app, InputActivity::class.java).putExtra(InputActivity.EXTRA_UNIT, unit)
        id?.let { intent.putExtra(InputActivity.EXTRA_RECORD_ID, it) }
        date?.let { intent.putExtra(InputActivity.EXTRA_DATE, it) }
        val controller = Robolectric.buildActivity(InputActivity::class.java, intent).setup().visible()
        controllers.add(controller)
        val activity = controller.get()
        await("Input form must load before editing") { activity.findViewById<View>(R.id.save_button).isEnabled }
        return activity
    }
    private fun save(activity: InputActivity, value: String, note: String) {
        activity.findViewById<EditText>(R.id.amount_input).setText(value)
        activity.findViewById<EditText>(R.id.note_input).setText(note)
        repeat(12) { activity.findViewById<View>(R.id.save_button).performClick() }
        await("Committed save must close the form") { activity.isFinishing }
    }
    private fun dialog() = requireNotNull(ShadowDialog.getLatestDialog()) as AlertDialog
    private fun balance(activity: MainActivity) = activity.findViewById<TextView>(R.id.remaining).text.toString()

    @Test fun actualFormsAddEditAndConfirmedDeleteRefreshTodayAndWidgetExactlyOnce() {
        val manager = AppWidgetManager.getInstance(app)
        val host = shadowOf(manager)
        host.addBoundWidget(42, AppWidgetProviderInfo().apply {
            provider = ComponentName(app, CalorieWidgetProvider::class.java)
            initialLayout = R.layout.widget_calorie
        })
        save(input(), "350", "早餐")
        save(input("kJ"), "1000", "饮料")
        val main = Robolectric.buildActivity(MainActivity::class.java).setup().visible().also(controllers::add).get()
        await("Today should show mixed-unit balance") { balance(main) == "1410.99" }
        await("Widget must refresh after two saves") { host.getViewFor(42)?.findViewById<TextView>(R.id.widget_remaining)?.text.toString() == "1410.99" }
        assertEquals(2, summary().recordCount)
        val drink = runBlocking { app.database.intakeDao().getForDate(today).single { it.note == "饮料" } }
        val edit = input(id = drink.id)
        assertEquals("1000", edit.findViewById<EditText>(R.id.amount_input).text.toString())
        assertEquals(R.id.unit_kj, edit.findViewById<RadioGroup>(R.id.unit_group).checkedRadioButtonId)
        save(edit, "2092", "饮料修改")
        await("Edit must replace the old amount") { balance(main) == "1150" }
        await("Widget must reflect edited amount") { host.getViewFor(42)?.findViewById<TextView>(R.id.widget_remaining)?.text.toString() == "1150" }
        assertEquals(850_000L, summary().totalMilliKcal)
        assertEquals(2, summary().recordCount)
        val rows = main.findViewById<LinearLayout>(R.id.records_container)
        await("Both rows should render") { rows.childCount == 2 }
        rows.getChildAt(0).findViewById<View>(R.id.delete_record).performClick()
        dialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        assertEquals(2, summary().recordCount)
        rows.getChildAt(0).findViewById<View>(R.id.delete_record).performClick()
        dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        await("Delete must restore the removed calories") { balance(main) == "1500" && rows.childCount == 1 }
        await("Widget must reflect deletion") { host.getViewFor(42)?.findViewById<TextView>(R.id.widget_remaining)?.text.toString() == "1500" }
        assertEquals(500_000L, summary().totalMilliKcal)
        rows.getChildAt(0).findViewById<View>(R.id.delete_record).performClick()
        dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        await("Deleting final record restores full budget") { balance(main) == "2000" && rows.childCount == 0 }
        assertEquals(0, summary().recordCount)
    }

    @Test fun invalidOrCancelledInputNeverWritesAndZeroCreatesOneExplicitRecord() {
        val activity = input()
        val amount = activity.findViewById<EditText>(R.id.amount_input)
        for (invalid in listOf("", "-1", "1000000.001", "1.2345")) {
            amount.setText(invalid)
            activity.findViewById<View>(R.id.save_button).performClick()
            assertNotNull("Validation should reject $invalid", amount.error)
            assertFalse(activity.isFinishing)
            assertEquals(0, summary().recordCount)
        }
        amount.setText("250")
        activity.findViewById<View>(R.id.cancel_button).performClick()
        assertTrue(activity.isFinishing)
        assertEquals(0, summary().recordCount)
        save(input(), "0", "零摄入记录")
        assertEquals(1, summary().recordCount)
        assertEquals(0L, summary().totalMilliKcal)
        assertEquals(2_000_000L, summary().remainingMilliKcal)
    }

    @Test fun noteOnlyEditKeepsOriginalPrecisionIdentityAndRecordedTime() {
        save(input("kJ"), "1234.567", "原始备注")
        val original = runBlocking { app.database.intakeDao().getForDate(today).single() }
        val activity = input(id = original.id)
        assertEquals("", activity.findViewById<TextView>(R.id.form_error).text.toString())
        assertEquals("1234.567", activity.findViewById<EditText>(R.id.amount_input).text.toString())
        save(activity, "1234.567", "修改备注")
        val updated = runBlocking { requireNotNull(app.repository.getRecord(original.id)) }
        assertEquals(original.id, updated.id)
        assertEquals(original.createdAt, updated.createdAt)
        assertEquals(original.timestamp, updated.timestamp)
        assertEquals(original.originalAmountMilli, updated.originalAmountMilli)
        assertEquals(295_069L, updated.amountMilliKcal)
        assertEquals("修改备注", updated.note)
        assertEquals(1, summary().recordCount)
    }

    @Test fun historyDisplaysOnlyFrozenTotalsAndHasNoAddEditOrDeleteEntry() {
        val date = DateManager.today().minusDays(1).toString()
        save(input(), "100", "今天")
        val oldId = runBlocking {
            CalorieRepository(app.database) { date }.addRecord(CalorieMath.parseAmount("300", "kcal"),
                "不应保留的昨天备注", DateManager.timestamp(LocalDate.parse(date), 12, 0), "old", date)
        }
        runBlocking { app.repository.ensureDate() }
        assertEquals(100_000L, summary().totalMilliKcal)
        assertEquals(300_000L, summary(date).totalMilliKcal)
        val intent = Intent(app, DayDetailActivity::class.java).putExtra(DayDetailActivity.EXTRA_DATE, date)
        val details = Robolectric.buildActivity(DayDetailActivity::class.java, intent).setup().visible().also(controllers::add).get()
        val rows = details.findViewById<LinearLayout>(R.id.records_container)
        await("Archived summary should render") { details.findViewById<TextView>(R.id.summary).text.contains("300 kcal") }
        assertEquals(View.GONE, details.findViewById<View>(R.id.add_button).visibility)
        assertEquals(0, rows.childCount)
        assertTrue(details.findViewById<TextView>(R.id.empty_text).text.contains("只能查看"))
        for (extra in listOf(Intent(app, InputActivity::class.java).putExtra(InputActivity.EXTRA_DATE, date),
            Intent(app, InputActivity::class.java).putExtra(InputActivity.EXTRA_RECORD_ID, oldId))) {
            val editor = Robolectric.buildActivity(InputActivity::class.java, extra).setup().visible().also(controllers::add).get()
            await("Historical input must explain read-only state") { editor.findViewById<TextView>(R.id.form_error).text.isNotEmpty() }
            assertFalse(editor.findViewById<View>(R.id.save_button).isEnabled)
            editor.findViewById<EditText>(R.id.amount_input).setText("900")
            editor.findViewById<View>(R.id.save_button).performClick()
            assertEquals(300_000L, summary(date).totalMilliKcal)
        }
        assertTrue(runBlocking { app.database.intakeDao().getForDate(date).isEmpty() })
        assertNull(runBlocking { app.repository.getRecord(oldId) })
        assertEquals(100_000L, summary().totalMilliKcal)
    }
}
