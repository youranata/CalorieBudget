package com.example.caloriewidget.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import com.example.caloriewidget.CalorieWidgetApp
import com.example.caloriewidget.domain.CalorieMath
import com.example.caloriewidget.widget.CalorieWidgetProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class InputViewModel(application: Application, handle: SavedStateHandle) : AndroidViewModel(application) {
    private val app = application as CalorieWidgetApp
    val token: String = handle.get<String>("submission_key") ?: UUID.randomUUID().toString().also { handle["submission_key"] = it }
    private val mutable = MutableStateFlow<SaveState>(SaveState.Idle)
    val state = mutable.asStateFlow()
    fun save(id: Long, amount: CalorieMath.ParsedAmount, note: String, timestamp: Long, date: String, zone: String) {
        if (mutable.value == SaveState.Saving || mutable.value == SaveState.Saved) return
        mutable.value = SaveState.Saving
        app.appScope.launch {
            try {
                if (id > 0) app.repository.updateRecord(id, amount, note, timestamp, date, zone)
                else app.repository.addRecord(amount, note, timestamp, token, date, zone)
                // A widget refresh failure cannot turn a successful write into a failed save.
                runCatching { CalorieWidgetProvider.refreshAll(app) }
                mutable.value = SaveState.Saved
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.value = SaveState.Error(e.message ?: "保存失败，请重试") }
        }
    }
}
sealed class SaveState {
    data object Idle : SaveState()
    data object Saving : SaveState()
    data object Saved : SaveState()
    data class Error(val message: String) : SaveState()
}
