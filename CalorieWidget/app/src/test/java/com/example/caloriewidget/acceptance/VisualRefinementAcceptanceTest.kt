package com.example.caloriewidget.acceptance

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ApplicationProvider
import com.example.caloriewidget.CalorieWidgetApp
import com.example.caloriewidget.InputActivity
import com.example.caloriewidget.MainActivity
import com.example.caloriewidget.R
import com.example.caloriewidget.domain.CalorieMath
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

/** Oversized text must keep the same complete content and record controls. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = CalorieWidgetApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualRefinementAcceptanceTest {
    private lateinit var app: CalorieWidgetApp
    private val controllers = mutableListOf<ActivityController<*>>()

    @Before fun seed() {
        app = ApplicationProvider.getApplicationContext()
        @Suppress("DEPRECATION")
        app.resources.updateConfiguration(Configuration(app.resources.configuration).apply {
            fontScale = 1.3f
        }, app.resources.displayMetrics)
        runBlocking(Dispatchers.IO) {
            app.database.clearAllTables()
            app.repository.addRecord(CalorieMath.parseAmount("1000000", "kcal"),
                "长备注仍然可以完整查看。".repeat(30), System.currentTimeMillis(), "visual-large-font")
        }
    }

    @After fun cleanup() {
        controllers.asReversed().forEach { it.pause().stop().destroy() }
        shadowOf(Looper.getMainLooper()).idle()
        app.appScope.cancel()
    }

    @Test @Config(qualifiers = "w320dp-h900dp-notnight-mdpi")
    fun enlargedLightPagesPreserveNegativeBalanceLongNotesAndInput() = verifyPages("light")

    @Test @Config(qualifiers = "w320dp-h900dp-night-mdpi")
    fun enlargedDarkPagesPreserveNegativeBalanceLongNotesAndInput() = verifyPages("dark")

    private fun verifyPages(mode: String) {
        val main = activity(MainActivity::class.java)
        await { main.findViewById<LinearLayout>(R.id.records_container).childCount == 1 }
        assertEquals(1.3f, main.resources.configuration.fontScale, 0.01f)
        assertEquals("-998000", main.findViewById<TextView>(R.id.remaining).text.toString())
        capture(main, "$mode-large-font-today")
        listOf(R.id.add_record_button, R.id.budget_button, R.id.history_button,
            R.id.stats_button, R.id.edit_record, R.id.delete_record).forEach {
            val target = main.findViewById<View>(it)
            assertEquals(View.VISIBLE, target.visibility)
            assertTrue(target.hasOnClickListeners())
            assertTrue(target.width >= 44 && target.height >= 44)
        }

        val input = activity(InputActivity::class.java)
        await { input.findViewById<View>(R.id.save_button).isEnabled }
        input.findViewById<EditText>(R.id.amount_input).setText("123456.789")
        input.findViewById<EditText>(R.id.note_input).setText("输入值、单位和备注保持原样。")
        assertEquals(1.3f, input.resources.configuration.fontScale, 0.01f)
        capture(input, "$mode-large-font-input")
        assertEquals("123456.789", input.findViewById<EditText>(R.id.amount_input).text.toString())
        listOf(R.id.unit_kcal, R.id.unit_kj, R.id.time_button, R.id.cancel_button, R.id.save_button).forEach {
            val target = input.findViewById<View>(it)
            assertEquals(View.VISIBLE, target.visibility)
            assertTrue(target.width >= 44 && target.height >= 44)
        }
    }

    private fun <T : AppCompatActivity> activity(type: Class<T>): T {
        val controller = Robolectric.buildActivity(type, Intent(app, type)).setup().visible()
        controllers.add(controller)
        return controller.get()
    }

    private fun capture(activity: AppCompatActivity, name: String) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        fun measure() {
            root.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 320, 900)
        }
        measure()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
        measure()
        checkText(root)
        val bitmap = Bitmap.createBitmap(320, 900, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val directory = File("build/reports/visual-v21").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun checkText(view: View) {
        if (view.visibility != View.VISIBLE) return
        if (view is TextView && view !is EditText && view.text.isNotEmpty()) {
            val layout = view.layout
            assertNotNull(layout)
            assertTrue("Text height must fit: ${view.text}", view.height >= layout.height + view.compoundPaddingTop + view.compoundPaddingBottom)
            for (line in 0 until layout.lineCount) {
                assertEquals(0, layout.getEllipsisCount(line))
                assertTrue("Text width must fit: ${view.text}", layout.getLineWidth(line) <= view.width - view.compoundPaddingLeft - view.compoundPaddingRight + 1f)
            }
            assertTrue(layout.getLineEnd(layout.lineCount - 1) >= view.text.length)
        }
        if (view is ViewGroup) for (i in 0 until view.childCount) checkText(view.getChildAt(i))
    }

    private fun await(check: () -> Boolean) {
        val deadline = System.nanoTime() + 15_000_000_000L
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (check()) return
            Thread.sleep(5)
        }
        fail("Expected content did not load")
    }
}
