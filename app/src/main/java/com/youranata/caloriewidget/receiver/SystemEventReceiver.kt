package com.youranata.caloriewidget.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        ResetScheduler.scheduleNextMidnight(context.applicationContext)
        ResetReceiver().onReceive(context, intent)
    }
}
