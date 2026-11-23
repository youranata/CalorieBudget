package com.youranata.caloriewidget

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.youranata.caloriewidget.data.entity.IntakeRecordEntity
import com.youranata.caloriewidget.databinding.ActivityInputBinding
import com.youranata.caloriewidget.domain.CalorieMath
import com.youranata.caloriewidget.domain.DateManager
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class InputActivity : AppCompatActivity() {
    private lateinit var binding: ActivityInputBinding
    private val repository get() = (application as CalorieWidgetApp).repository
    private var editingId: Long = -1L
    private var selectedDate: LocalDate = DateManager.today()
    private var selectedTime: LocalTime = LocalTime.now().withSecond(0).withNano(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInputBinding.inflate(layoutInflater)
        setContentView(binding.root)
        editingId = intent.getLongExtra(EXTRA_RECORD_ID, -1L)
        val preferredUnit = intent.getStringExtra(EXTRA_UNIT)
        intent.getStringExtra(EXTRA_DATE)?.let { value -> runCatching { selectedDate = DateManager.parseDate(value) } }
        if (preferredUnit == "kJ") binding.unitGroup.check(R.id.unit_kj)
        setupDateTime()
        if (editingId > 0) loadRecord(editingId)
        binding.saveButton.setOnClickListener { save() }
        binding.cancelButton.setOnClickListener { finish() }
    }

    private fun setupDateTime() {
        renderDateTime()
        binding.dateButton.setOnClickListener {
            DatePickerDialog(this, { _, year, month, day ->
                selectedDate = LocalDate.of(year, month + 1, day); renderDateTime()
            }, selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth).show()
        }
        binding.timeButton.setOnClickListener {
            TimePickerDialog(this, { _, hour, minute ->
                selectedTime = LocalTime.of(hour, minute); renderDateTime()
            }, selectedTime.hour, selectedTime.minute, true).show()
        }
    }

    private fun renderDateTime() {
        binding.dateButton.text = DateManager.displayDate(selectedDate.toString())
        binding.timeButton.text = String.format("%02d:%02d", selectedTime.hour, selectedTime.minute)
    }

    private fun loadRecord(id: Long) {
        lifecycleScope.launch {
            val record = repository.getRecord(id) ?: return@launch
            val local = DateManager.timestampToLocalDateTime(record.timestamp)
            selectedDate = local.toLocalDate(); selectedTime = local.toLocalTime().withSecond(0).withNano(0)
            binding.amountInput.setText(CalorieMath.milliKcalToDisplay(record.originalAmountMilli))
            binding.unitGroup.check(if (record.originalUnit == "kJ") R.id.unit_kj else R.id.unit_kcal)
            binding.noteInput.setText(record.note)
            renderDateTime()
            binding.title.text = getString(R.string.edit_record)
        }
    }

    private fun save() {
        val unit = if (binding.unitGroup.checkedRadioButtonId == R.id.unit_kj) "kJ" else "kcal"
        val amount = try { CalorieMath.parsePositive(binding.amountInput.text?.toString().orEmpty(), unit) }
        catch (e: IllegalArgumentException) { binding.amountInput.error = e.message; return }
        val timestamp = LocalDateTime.of(selectedDate, selectedTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        lifecycleScope.launch {
            try {
                if (editingId > 0) repository.updateRecord(editingId, amount, binding.noteInput.text?.toString().orEmpty(), timestamp)
                else repository.addRecord(amount, binding.noteInput.text?.toString().orEmpty(), timestamp)
                com.youranata.caloriewidget.widget.CalorieWidgetProvider.refreshAll(this@InputActivity)
                setResult(RESULT_OK); finish()
            } catch (e: Exception) { Toast.makeText(this@InputActivity, e.message ?: "保存失败", Toast.LENGTH_LONG).show() }
        }
    }

    companion object {
        const val EXTRA_UNIT = "input_unit"
        const val EXTRA_RECORD_ID = "record_id"
        const val EXTRA_DATE = "input_date"
    }
}
