package com.youranata.caloriewidget.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.youranata.caloriewidget.CalorieWidgetApp
import com.youranata.caloriewidget.widget.CalorieWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as CalorieWidgetApp
                app.repository.ensureDate()
                CalorieWidgetProvider.refreshAll(context.applicationContext)
                ResetScheduler.scheduleNextMidnight(context.applicationContext)
            } finally { pending.finish() }
        }
    }
    companion object { const val ACTION_MIDNIGHT = "com.youranata.caloriewidget.ACTION_MIDNIGHT" }
}
