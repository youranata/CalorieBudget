package com.example.caloriewidget.widget

import android.app.PendingIntent
import android.appwidget.*
import android.content.*
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import com.example.caloriewidget.*
import com.example.caloriewidget.data.DaySummary
import com.example.caloriewidget.domain.*
import com.example.caloriewidget.receiver.ResetScheduler
import kotlinx.coroutines.launch

class CalorieWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = refresh(context)
    override fun onEnabled(context: Context) { ResetScheduler.scheduleNextMidnight(context) }
    private fun refresh(context: Context) {
        val result = goAsync()
        val app = context.applicationContext as CalorieWidgetApp
        app.appScope.launch {
            try { refreshAll(app) }
            catch (e: Exception) { Log.e("CalorieWidget", "Widget refresh failed", e) }
            finally { ResetScheduler.scheduleNextMidnight(app); result.finish() }
        }
    }
    companion object {
        suspend fun refreshAll(context: Context) {
            val app = context.applicationContext as CalorieWidgetApp
            val summary = app.repository.getSummary(DateManager.todayString())
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CalorieWidgetProvider::class.java))
            ids.forEach { id ->
                val compact = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250) < 220
                manager.updateAppWidget(id, buildViews(context, summary, compact))
            }
        }
        fun buildViews(context: Context, summary: DaySummary, compact: Boolean): RemoteViews {
            val views = RemoteViews(context.packageName, if (compact) R.layout.widget_calorie_compact else R.layout.widget_calorie)
            views.setTextViewText(R.id.widget_remaining, CalorieMath.milliKcalToDisplay(summary.remainingMilliKcal))
            views.setTextViewText(R.id.widget_remaining_kj, "${CalorieMath.milliKjToDisplay(summary.remainingMilliKcal)} kJ")
            views.setTextViewText(R.id.widget_consumed, "已摄入 ${CalorieMath.milliKcalToDisplay(summary.totalMilliKcal)} / ${CalorieMath.milliKcalToDisplay(summary.budgetMilliKcal)} kcal")
            views.setTextColor(R.id.widget_remaining, context.getColor(if (summary.remainingMilliKcal < 0) R.color.calorie_warning else R.color.calorie_primary))
            views.setOnClickPendingIntent(R.id.widget_balance, PendingIntent.getActivity(context, 100,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            views.setOnClickPendingIntent(R.id.widget_add_kcal, input(context, "kcal", 101))
            views.setOnClickPendingIntent(R.id.widget_add_kj, input(context, "kJ", 102))
            return views
        }
        private fun input(context: Context, unit: String, request: Int) = PendingIntent.getActivity(
            context, request, Intent(context, InputActivity::class.java).putExtra(InputActivity.EXTRA_UNIT, unit)
                .putExtra(InputActivity.EXTRA_FOCUS_NOTE, false), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
