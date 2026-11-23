package com.youranata.caloriewidget.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.youranata.caloriewidget.domain.DateManager

object ResetScheduler {
    fun scheduleNextMidnight(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(context, 9017,
            Intent(context, ResetReceiver::class.java).setAction(ResetReceiver.ACTION_MIDNIGHT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val next = DateManager.nextMidnight()
        try {
            if (Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms())
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
            else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
        } catch (_: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
        }
    }
}
