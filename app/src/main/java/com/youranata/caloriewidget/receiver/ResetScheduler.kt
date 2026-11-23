package com.youranata.caloriewidget.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.youranata.caloriewidget.domain.DateManager
import java.time.ZoneId

object ResetScheduler {
    private const val REQUEST_CODE = 9017
    fun scheduleNextMidnight(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val next = DateManager.today().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = Intent(context, ResetReceiver::class.java).setAction(ResetReceiver.ACTION_MIDNIGHT)
        val pending = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, next, pending)
        }
    }
}
