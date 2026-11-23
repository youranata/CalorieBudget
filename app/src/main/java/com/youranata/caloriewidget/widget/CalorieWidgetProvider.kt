package com.youranata.caloriewidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.youranata.caloriewidget.CalorieWidgetApp
import com.youranata.caloriewidget.InputActivity
import com.youranata.caloriewidget.MainActivity
import com.youranata.caloriewidget.R
import com.youranata.caloriewidget.domain.CalorieMath
import com.youranata.caloriewidget.domain.DateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CalorieWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as CalorieWidgetApp
                app.repository.ensureDate()
                val summary = app.repository.getSummary(DateManager.todayString())
                ids.forEach { updateOne(context, manager, it, summary) }
            } finally { pending.finish() }
        }
    }

    override fun onEnabled(context: Context) { super.onEnabled(context); refreshAll(context) }
    override fun onDeleted(context: Context, appWidgetIds: IntArray) { super.onDeleted(context, appWidgetIds) }

    companion object {
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CalorieWidgetProvider::class.java))
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.IO).launch {
                val app = context.applicationContext as CalorieWidgetApp
                app.repository.ensureDate()
                val summary = app.repository.getSummary(DateManager.todayString())
                ids.forEach { updateOne(context, manager, it, summary) }
            }
        }

        private fun updateOne(context: Context, manager: AppWidgetManager, id: Int, summary: com.youranata.caloriewidget.data.DaySummary) {
            val views = RemoteViews(context.packageName, R.layout.widget_calorie)
            views.setTextViewText(R.id.widget_remaining, CalorieMath.milliKcalToDisplay(summary.remainingMilliKcal))
            views.setTextViewText(R.id.widget_remaining_kj, "约 ${CalorieMath.milliKjToDisplay(summary.remainingMilliKcal)} kJ")
            views.setTextViewText(R.id.widget_consumed, "已摄入 ${CalorieMath.milliKcalToDisplay(summary.totalMilliKcal)} / ${CalorieMath.milliKcalToDisplay(summary.budgetMilliKcal)} kcal")
            val color = if (summary.remainingMilliKcal < 0) R.color.calorie_warning else R.color.calorie_primary
            views.setTextColor(R.id.widget_remaining, context.getColor(color))
            views.setOnClickPendingIntent(R.id.widget_remaining, activityPending(context, MainActivity::class.java, 100))
            views.setOnClickPendingIntent(R.id.widget_add_kcal, inputPending(context, "kcal", 101))
            views.setOnClickPendingIntent(R.id.widget_add_kj, inputPending(context, "kJ", 102))
            views.setOnClickPendingIntent(R.id.widget_add_note, inputPending(context, "kcal", 103))
            manager.updateAppWidget(id, views)
        }

        private fun activityPending(context: Context, cls: Class<*>, request: Int): PendingIntent =
            PendingIntent.getActivity(context, request, Intent(context, cls), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        private fun inputPending(context: Context, unit: String, request: Int): PendingIntent =
            PendingIntent.getActivity(context, request, Intent(context, InputActivity::class.java).putExtra(InputActivity.EXTRA_UNIT, unit), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
