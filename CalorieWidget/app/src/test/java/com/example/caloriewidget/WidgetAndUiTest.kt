package com.example.caloriewidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.appwidget.AppWidgetProviderInfo
import org.robolectric.Shadows.shadowOf
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.example.caloriewidget.data.DaySummary
import com.example.caloriewidget.domain.DateManager
import com.example.caloriewidget.widget.CalorieWidgetProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35], application = CalorieWidgetApp::class)
class WidgetAndUiTest {
    @Test fun bothRemoteViewsLayoutsInflateAndShowNegativeBalanceWithoutAddingRecords() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<CalorieWidgetApp>()
        val summary = DaySummary(DateManager.todayString(), 2_000_000, 2_200_000, -200_000, 1)
        for (compact in listOf(false, true)) {
            val view = CalorieWidgetProvider.buildViews(app, summary, compact).apply(app, null)
            assertEquals("-200", view.findViewById<TextView>(R.id.widget_remaining).text.toString())
            assertTrue(view.findViewById<TextView>(R.id.widget_add_kcal).hasOnClickListeners())
            assertTrue(view.findViewById<TextView>(R.id.widget_add_kj).hasOnClickListeners())
        }
        repeat(3) { CalorieWidgetProvider.refreshAll(app) }
        assertEquals(0, app.repository.getSummary(DateManager.todayString()).recordCount)
        val component = ComponentName(app, CalorieWidgetProvider::class.java)
        val receiver = app.packageManager.getReceiverInfo(component, PackageManager.GET_META_DATA)
        assertTrue(receiver.metaData.getInt("android.appwidget.provider") != 0)
        // Robolectric needs an explicit host binding; it does not enumerate manifest providers automatically.
        val manager = AppWidgetManager.getInstance(app)
        val host = shadowOf(manager)
        val info = AppWidgetProviderInfo().apply { provider = component; initialLayout = R.layout.widget_calorie }
        host.addInstalledProvider(info); host.addBoundWidget(7, info)
        CalorieWidgetProvider.refreshAll(app)
        assertNotNull(host.getViewFor(7))
        val compact = CalorieWidgetProvider.buildViews(app, summary, true).apply(app, null)
        compact.findViewById<TextView>(R.id.widget_add_kj).performClick()
        assertEquals("kJ", shadowOf(app).nextStartedActivity.getStringExtra(InputActivity.EXTRA_UNIT))
        compact.findViewById<TextView>(R.id.widget_add_kcal).performClick()
        val kcalIntent = shadowOf(app).nextStartedActivity
        assertEquals("kcal", kcalIntent.getStringExtra(InputActivity.EXTRA_UNIT))
        assertFalse(kcalIntent.getBooleanExtra(InputActivity.EXTRA_FOCUS_NOTE, false))
        compact.findViewById<android.view.View>(R.id.widget_balance).performClick()
        assertEquals(MainActivity::class.java.name, shadowOf(app).nextStartedActivity.component?.className)
    }
    @Test fun nativeActivitiesCanOpenAndUnsubmittedInputDoesNotCreateRecord() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<CalorieWidgetApp>()
        val before = app.repository.getSummary(DateManager.todayString()).recordCount
        Robolectric.buildActivity(MainActivity::class.java).setup().visible().pause().stop().destroy()
        Robolectric.buildActivity(InputActivity::class.java).setup().visible().pause().stop().destroy()
        Robolectric.buildActivity(HistoryActivity::class.java).setup().visible().pause().stop().destroy()
        Robolectric.buildActivity(DayDetailActivity::class.java).setup().visible().pause().stop().destroy()
        Robolectric.buildActivity(StatsActivity::class.java).setup().visible().pause().stop().destroy()
        assertEquals(before, app.repository.getSummary(DateManager.todayString()).recordCount)
    }
}
