package com.youranata.caloriewidget

import android.app.Application
import com.youranata.caloriewidget.data.*
import com.youranata.caloriewidget.receiver.ResetScheduler
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
