package com.youranata.caloriewidget

import android.app.Application
import com.youranata.caloriewidget.data.AppDatabase
import com.youranata.caloriewidget.data.CalorieRepository

class CalorieWidgetApp : Application() {
    val database by lazy { AppDatabase.get(this) }
    val repository by lazy { CalorieRepository(database) }

    override fun onCreate() {
        super.onCreate()
        com.youranata.caloriewidget.receiver.ResetScheduler.scheduleNextMidnight(this)
    }
}
