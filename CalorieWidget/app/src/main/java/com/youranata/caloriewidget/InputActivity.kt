package com.youranata.caloriewidget

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.youranata.caloriewidget.databinding.ActivityInputBinding
import com.youranata.caloriewidget.domain.*
import com.youranata.caloriewidget.ui.*
import kotlinx.coroutines.launch
import java.time.*

class InputActivity : BaseActivity() {
    private lateinit var binding: ActivityInputBinding
    private val model: InputViewModel by viewModels()
    private var editingId = -1L
    private var selectedDate = DateManager.today()
    private var selectedTime = LocalTime.now().withNano(0)
    private var zone = ZoneId.systemDefault()
    private var initialized = false
    private var originalTimestamp: Long? = null
    private var originalLocalTime: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInputBinding.inflate(layoutInflater); content(binding.root)
        editingId = intent.getLongExtra(EXTRA_RECORD_ID, -1L)
        binding.title.setText(if (editingId > 0) R.string.edit_record else R.string.add_record)
        binding.saveButton.isEnabled = false
        if (savedInstanceState?.getBoolean("loaded") == true) {
            selectedDate = LocalDate.parse(savedInstanceState.getString("date"))
            selectedTime = LocalTime.parse(savedInstanceState.getString("time"))
            zone = DateManager.zone(savedInstanceState.getString("zone").orEmpty())
            binding.amountInput.setText(savedInstanceState.getString("amount"))
            binding.noteInput.setText(savedInstanceState.getString("note"))
            binding.unitGroup.check(if (savedInstanceState.getBoolean("kj")) R.id.unit_kj else R.id.unit_kcal)
            originalTimestamp = savedInstanceState.getLong("originalTimestamp").takeIf { savedInstanceState.containsKey("originalTimestamp") }
            originalLocalTime = savedInstanceState.getString("originalLocalTime")
            initialized = true
        } else if (editingId > 0) {
            lifecycleScope.launch {
                val record = repository.getRecord(editingId)
                if (record == null) { binding.formError.text = "记录已被删除"; return@launch }
                zone = DateManager.zone(record.zoneId)
                selectedDate = LocalDate.parse(record.localDate)
                selectedTime = Instant.ofEpochMilli(record.timestamp).atZone(zone).toLocalTime()
                binding.amountInput.setText(CalorieMath.inputValue(record.originalAmountMilli))
                binding.noteInput.setText(record.note)
                binding.unitGroup.check(if (record.originalUnit == "kJ") R.id.unit_kj else R.id.unit_kcal)
                originalTimestamp = record.timestamp
                originalLocalTime = selectedDate.atTime(selectedTime).toString()
                initialized = true; renderDateTime(); binding.saveButton.isEnabled = true
            }
        } else {
            intent.getStringExtra(EXTRA_DATE)?.let { selectedDate = LocalDate.parse(it) }
            binding.unitGroup.check(if (intent.getStringExtra(EXTRA_UNIT) == "kJ") R.id.unit_kj else R.id.unit_kcal)
            initialized = true
            if (intent.getBooleanExtra(EXTRA_FOCUS_NOTE, false)) binding.noteInput.requestFocus() else binding.amountInput.requestFocus()
        }
        renderDateTime(); binding.saveButton.isEnabled = initialized
        binding.dateButton.setOnClickListener {
            DatePickerDialog(this, { _, year, month, day -> selectedDate = LocalDate.of(year, month + 1, day); renderDateTime() },
                selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth).show()
        }
        binding.timeButton.setOnClickListener {
            TimePickerDialog(this, { _, hour, minute -> selectedTime = LocalTime.of(hour, minute); renderDateTime() },
                selectedTime.hour, selectedTime.minute, true).show()
        }
        binding.cancelButton.setOnClickListener { finish() }
        binding.saveButton.setOnClickListener { save() }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { state ->
                    binding.saveButton.isEnabled = initialized && state != SaveState.Saving
                    binding.cancelButton.isEnabled = state != SaveState.Saving
                    binding.formError.text = if (state is SaveState.Error) state.message else ""
                    if (state == SaveState.Saved) { setResult(RESULT_OK); finish() }
                }
            }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("loaded", initialized); outState.putString("date", selectedDate.toString())
        outState.putString("time", selectedTime.toString()); outState.putString("zone", zone.id)
        outState.putString("amount", binding.amountInput.text.toString()); outState.putString("note", binding.noteInput.text.toString())
        outState.putBoolean("kj", binding.unitGroup.checkedRadioButtonId == R.id.unit_kj)
        originalTimestamp?.let { outState.putLong("originalTimestamp", it) }
        outState.putString("originalLocalTime", originalLocalTime)
        super.onSaveInstanceState(outState)
    }
    private fun renderDateTime() {
        binding.dateButton.text = DateManager.displayDate(selectedDate.toString())
        binding.timeButton.text = selectedTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
        binding.timezoneText.text = "记录时区：${zone.id}；更改系统时区不会移动已有记录日期。"
    }
    private fun save() {
        if (!initialized) return
        val unit = if (binding.unitGroup.checkedRadioButtonId == R.id.unit_kj) "kJ" else "kcal"
        val parsed = runCatching { CalorieMath.parseAmount(binding.amountInput.text.toString(), unit) }
            .getOrElse { binding.amountInput.error = it.message; return }
        if (selectedDate > DateManager.today()) { binding.formError.text = "摄入记录日期不能晚于今天"; return }
        val local = selectedDate.atTime(selectedTime)
        val zoned = local.atZone(zone)
        if (zoned.toLocalDateTime() != local) { binding.formError.text = "此时间在夏令时切换中不存在，请重新选择"; return }
        val instant = if (originalLocalTime == local.toString()) originalTimestamp ?: zoned.toInstant().toEpochMilli() else zoned.toInstant().toEpochMilli()
        model.save(editingId, parsed, binding.noteInput.text.toString(), instant, selectedDate.toString(), zone.id)
    }
    companion object {
        const val EXTRA_UNIT = "input_unit"
        const val EXTRA_RECORD_ID = "record_id"
        const val EXTRA_DATE = "input_date"
        const val EXTRA_FOCUS_NOTE = "focus_note"
    }
}
