package com.example.caloriewidget.acceptance

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ApplicationProvider
import com.example.caloriewidget.*
import com.example.caloriewidget.data.DaySummary
import com.example.caloriewidget.data.entity.DailyBudgetEntity
import com.example.caloriewidget.domain.CalorieMath
import com.example.caloriewidget.domain.DateManager
import com.example.caloriewidget.widget.CalorieWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

/** Real Android View rendering supplements the unchanged functional acceptance tests. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = CalorieWidgetApp::class, qualifiers = "w400dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualStyleAcceptanceTest {
    private lateinit var app: CalorieWidgetApp
    private val controllers = mutableListOf<ActivityController<*>>()
    private val today get() = DateManager.todayString()
    private val yesterday get() = DateManager.today().minusDays(1).toString()

    @Before fun seedRepresentativeData() {
        app = ApplicationProvider.getApplicationContext()
        runBlocking(Dispatchers.IO) {
            app.database.clearAllTables()
            app.repository.addRecord(CalorieMath.parseAmount("350", "kcal"), "早餐", System.currentTimeMillis(), "visual-breakfast")
            app.repository.addRecord(CalorieMath.parseAmount("1000", "kJ"), "午后饮品", System.currentTimeMillis(), "visual-drink")
            app.database.budgetDao().upsert(DailyBudgetEntity(yesterday, 2_000_000, 0, 1_800_000, true))
        }
    }

    @After fun cleanup() {
        controllers.asReversed().forEach { it.pause().stop().destroy() }
        controllers.clear()
        shadowOf(Looper.getMainLooper()).idle()
        app.appScope.cancel()
    }

    @Test @Config(qualifiers = "w400dp-h900dp-notnight-mdpi")
    fun lightPagesRetainReadableContentAtNarrowAndRegularWidths() = renderPages("light")

    @Test @Config(qualifiers = "w400dp-h900dp-night-mdpi")
    fun darkPagesRetainReadableContentAtNarrowAndRegularWidths() = renderPages("dark")

    private fun renderPages(mode: String) {
        for (width in listOf(320, 400)) {
            val main = activity(MainActivity::class.java)
            await("Today balance should render") { main.findViewById<TextView>(R.id.remaining).text.toString() == "1410.99" }
            captureAndCheck(main, "${mode}-today-$width", width)
            listOf(R.id.add_record_button, R.id.budget_button, R.id.history_button, R.id.stats_button, R.id.alarm_button)
                .forEach { assertUsableTarget(main.findViewById(it)) }
            closeLatest()

            val input = activity(InputActivity::class.java)
            await("Input must initialize") { input.findViewById<View>(R.id.save_button).isEnabled }
            input.findViewById<EditText>(R.id.amount_input).setText("1234.567")
            input.findViewById<EditText>(R.id.note_input).setText("午餐 · 自由填写的来源备注")
            captureAndCheck(input, "${mode}-input-$width", width)
            assertEquals(R.id.unit_kcal, input.findViewById<RadioGroup>(R.id.unit_group).checkedRadioButtonId)
            assertFalse(input.findViewById<View>(R.id.date_button).isEnabled)
            listOf(R.id.unit_kcal, R.id.unit_kj, R.id.time_button, R.id.cancel_button, R.id.save_button)
                .forEach { assertUsableTarget(input.findViewById(it)) }
            closeLatest()

            val history = activity(HistoryActivity::class.java)
            await("Both saved dates should render") { history.findViewById<LinearLayout>(R.id.history_container).childCount == 2 }
            captureAndCheck(history, "${mode}-history-$width", width)
            assertUsableTarget(history.findViewById(R.id.pick_date_button))
            closeLatest()

            val detail = activity(DayDetailActivity::class.java, Intent(app, DayDetailActivity::class.java)
                .putExtra(DayDetailActivity.EXTRA_DATE, yesterday))
            await("Read-only archive must load") { detail.findViewById<TextView>(R.id.summary).text.contains("1800 kcal") }
            captureAndCheck(detail, "${mode}-archive-$width", width)
            assertEquals(View.GONE, detail.findViewById<View>(R.id.add_button).visibility)
            assertEquals(0, detail.findViewById<LinearLayout>(R.id.records_container).childCount)
            assertTrue(detail.findViewById<TextView>(R.id.empty_text).text.contains("只能查看"))
            closeLatest()

            val stats = activity(StatsActivity::class.java)
            await("Statistics text should load") { stats.findViewById<TextView>(R.id.stats).text.contains("最近 7 天平均") }
            captureAndCheck(stats, "${mode}-stats-$width", width)
            listOf(R.id.range7, R.id.range30, R.id.range90, R.id.rangeAll).forEach { assertUsableTarget(stats.findViewById(it)) }
            closeLatest()
        }
    }

    @Test @Config(qualifiers = "w400dp-h900dp-notnight-mdpi")
    fun lightRemoteViewsKeepBalanceAndLargerUnitButtonsAtBothSupportedSizes() = renderWidgets("light")

    @Test @Config(qualifiers = "w400dp-h900dp-night-mdpi")
    fun darkRemoteViewsKeepBalanceAndLargerUnitButtonsAtBothSupportedSizes() = renderWidgets("dark")

    private fun renderWidgets(mode: String) {
        val examples = listOf(
            DaySummary(today, 2_000_000, 750_000, 1_250_000, 1),
            DaySummary(today, 2_000_000, 1_000_000_000, -998_000_000, 1)
        )
        for ((index, summary) in examples.withIndex()) for (compact in listOf(false, true)) {
            val root = CalorieWidgetProvider.buildViews(app, summary, compact).apply(app, null)
            val width = if (compact) 110 else 250
            val height = if (compact) 132 else 140
            measure(root, width, height)
            assertAllTextFits(root)
            listOf(R.id.widget_balance, R.id.widget_add_kcal, R.id.widget_add_kj).forEach {
                val target = root.findViewById<View>(it)
                assertNotNull(target)
                assertEquals(View.VISIBLE, target.visibility)
                assertTrue("Widget target must keep a click listener", target.hasOnClickListeners())
                assertTrue("Widget target must have positive area", target.width > 0 && target.height > 0)
                assertTrue("Widget target must remain inside the widget", target.bottom <= (target.parent as View).height)
            }
            listOf(R.id.widget_add_kcal, R.id.widget_add_kj).forEach {
                assertTrue("Unit buttons must have a 48dp hit height", root.findViewById<View>(it).height >= 48 * root.resources.displayMetrics.density)
            }
            assertEquals(CalorieMath.milliKcalToDisplay(summary.remainingMilliKcal), root.findViewById<TextView>(R.id.widget_remaining).text.toString())
            save(root, "${mode}-widget-${if (compact) "compact" else "regular"}-$index")
        }
    }

    @Test fun longNotesAndLargeAmountsKeepEveryCharacterAndBothRecordActions() {
        val main = activity(MainActivity::class.java)
        await("Record rows should load") { main.findViewById<LinearLayout>(R.id.records_container).childCount == 2 }
        val container = main.findViewById<LinearLayout>(R.id.records_container)
        val note = "备注保留完整内容，支持换行与长文本。".repeat(25)
        val row = LayoutInflater.from(main).inflate(R.layout.item_record, container, false)
        row.findViewById<TextView>(R.id.record_note).text = note
        row.findViewById<TextView>(R.id.record_amount).text = "1000000 kcal"
        row.findViewById<TextView>(R.id.record_original).text = "1000000 kcal · 4184000 kJ"
        container.removeAllViews()
        container.addView(row)
        val root = main.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        measure(root, 320, 900)
        assertAllTextFits(row)
        val noteView = row.findViewById<TextView>(R.id.record_note)
        assertEquals(note, noteView.text.toString())
        assertTrue("All lines of a long note must remain available by scrolling", noteView.height >= noteView.layout.height + noteView.compoundPaddingTop + noteView.compoundPaddingBottom)
        listOf(R.id.edit_record, R.id.delete_record).forEach { assertUsableTarget(row.findViewById(it)) }
        save(root, "light-long-note-320")
        closeLatest()

        val input = activity(InputActivity::class.java)
        await("Input must initialize") { input.findViewById<View>(R.id.save_button).isEnabled }
        val edit = input.findViewById<EditText>(R.id.note_input)
        edit.setText("甲".repeat(500))
        assertEquals(500, edit.text.length)
        edit.append("乙")
        assertEquals("The original 500-character validation must remain", 500, edit.text.length)
        captureAndCheck(input, "light-input-long-note-320", 320)
        closeLatest()
    }

    private fun <T : AppCompatActivity> activity(type: Class<T>, intent: Intent = Intent(app, type)): T {
        val controller = Robolectric.buildActivity(type, intent).setup().visible()
        controllers.add(controller)
        return controller.get()
    }

    private fun closeLatest() {
        controllers.removeAt(controllers.lastIndex).pause().stop().destroy()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun captureAndCheck(activity: AppCompatActivity, name: String, width: Int) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        measure(root, width, 900)
        // Material floating labels animate after setText; capture the settled frame.
        // A bounded advance avoids draining the application's periodic midnight checks.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
        measure(root, width, 900)
        assertAllTextFits(root)
        save(root, name)
    }

    private fun measure(view: View, widthDp: Int, heightDp: Int) {
        val density = view.resources.displayMetrics.density
        val width = (widthDp * density).toInt()
        val height = (heightDp * density).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
    }

    private fun assertAllTextFits(root: View) {
        if (root.visibility != View.VISIBLE) return
        if (root is TextView && root !is EditText && root.text.isNotEmpty()) {
            val layout = root.layout
            assertNotNull("Text must have a layout: ${root.text}", layout)
            assertTrue("Text must keep usable width: ${root.text}", root.width > root.compoundPaddingLeft + root.compoundPaddingRight)
            assertTrue("Text must keep usable height: ${root.text}", root.height >= root.compoundPaddingTop + root.compoundPaddingBottom + layout.height)
            for (line in 0 until layout.lineCount) {
                assertEquals("Text must not be ellipsized: ${root.text}", 0, layout.getEllipsisCount(line))
                assertTrue("Text must fit its line: ${root.text}", layout.getLineWidth(line) <= root.width - root.compoundPaddingLeft - root.compoundPaddingRight + 1f)
            }
            assertTrue("Every text character must remain visible: ${root.text}", layout.lineCount > 0 && layout.getLineEnd(layout.lineCount - 1) >= root.text.length)
        }
        if (root is ViewGroup) for (i in 0 until root.childCount) assertAllTextFits(root.getChildAt(i))
    }

    private fun assertUsableTarget(target: View) {
        assertEquals(View.VISIBLE, target.visibility)
        assertTrue("Existing action must keep a positive hit area", target.width > 0 && target.height > 0)
        assertTrue("Existing action must not be clipped horizontally", target.left >= 0 && target.right <= (target.parent as View).width)
        if (target is Button) assertTrue("App buttons must retain a 44dp target", target.height >= 44 * target.resources.displayMetrics.density)
    }

    private fun save(view: View, name: String) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val directory = File("build/reports/visual-v2").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
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
}
