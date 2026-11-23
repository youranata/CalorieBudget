package com.example.caloriewidget

import android.app.Application
import com.example.caloriewidget.data.*
import com.example.caloriewidget.receiver.ResetScheduler
import kotlinx.coroutines.*

class CalorieWidgetApp : Application() {
    val database by lazy { AppDatabase.get(this) }
    val repository by lazy { CalorieRepository(database) }
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onCreate() {
        super.onCreate()
        ResetScheduler.scheduleNextMidnight(this)
    }
}
