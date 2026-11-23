package com.youranata.caloriewidget.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.youranata.caloriewidget.CalorieWidgetApp
import com.youranata.caloriewidget.widget.CalorieWidgetProvider
import kotlinx.coroutines.launch

class ResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        BackgroundRefresh.run(context, goAsync())
    }
    companion object { const val ACTION_MIDNIGHT = "com.youranata.caloriewidget.ACTION_MIDNIGHT" }
}

internal object BackgroundRefresh {
    fun run(context: Context, result: BroadcastReceiver.PendingResult) {
        val app = context.applicationContext as CalorieWidgetApp
        app.appScope.launch {
            try {
                app.repository.ensureDate()
                CalorieWidgetProvider.refreshAll(app)
            } catch (e: Exception) { Log.e("CalorieWidget", "Background refresh failed", e) }
            finally { ResetScheduler.scheduleNextMidnight(app); result.finish() }
        }
    }
}
