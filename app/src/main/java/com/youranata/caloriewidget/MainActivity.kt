package com.youranata.caloriewidget

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.youranata.caloriewidget.data.entity.IntakeRecordEntity
import com.youranata.caloriewidget.databinding.ActivityMainBinding
import com.youranata.caloriewidget.domain.CalorieMath
import com.youranata.caloriewidget.domain.DateManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val repository get() = (application as CalorieWidgetApp).repository
    private val today get() = DateManager.todayString()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.addRecordButton.setOnClickListener { startActivity(Intent(this, InputActivity::class.java)) }
        binding.historyButton.setOnClickListener { startActivity(Intent(this, HistoryActivity::class.java)) }
        binding.statsButton.setOnClickListener { startActivity(Intent(this, StatsActivity::class.java)) }
        binding.budgetButton.setOnClickListener { showBudgetDialog() }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.ensureDate(today)
                combine(repository.observeRecords(today), kotlinx.coroutines.flow.flow { emit(repository.getSummary(today)) }) { records, _ -> records }
                    .collect { renderRecords(it); renderSummary() }
            }
        }
        lifecycleScope.launch { renderSummary() }
    }

    private fun renderSummary() {
        lifecycleScope.launch {
            val summary = repository.getSummary(today)
            binding.remaining.text = CalorieMath.milliKcalToDisplay(summary.remainingMilliKcal)
            binding.remainingKj.text = "约 ${CalorieMath.milliKjToDisplay(summary.remainingMilliKcal)} kJ"
            binding.consumed.text = "已摄入 ${CalorieMath.milliKcalToDisplay(summary.totalMilliKcal)} kcal"
            binding.budget.text = "每日预算 ${CalorieMath.milliKcalToDisplay(summary.budgetMilliKcal)} kcal"
            binding.remaining.setTextColor(getColor(if (summary.remainingMilliKcal < 0) R.color.calorie_warning else R.color.calorie_primary))
        }
    }

    private fun renderRecords(records: List<IntakeRecordEntity>) {
        binding.recordsContainer.removeAllViews()
        if (records.isEmpty()) {
            binding.emptyText.visibility = View.VISIBLE
            return
        }
        binding.emptyText.visibility = View.GONE
        records.forEach { record ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_record, binding.recordsContainer, false)
            row.findViewById<android.widget.TextView>(R.id.record_time).text = DateManager.displayTime(record.timestamp)
            row.findViewById<android.widget.TextView>(R.id.record_note).text = record.note.ifBlank { getString(R.string.no_note) }
            row.findViewById<android.widget.TextView>(R.id.record_amount).text = "${CalorieMath.milliKcalToDisplay(record.amountMilliKcal)} kcal"
            row.findViewById<View>(R.id.edit_record).setOnClickListener { startActivity(Intent(this, InputActivity::class.java).putExtra(InputActivity.EXTRA_RECORD_ID, record.id)) }
            row.findViewById<View>(R.id.delete_record).setOnClickListener { confirmDelete(record) }
            binding.recordsContainer.addView(row)
        }
    }

    private fun confirmDelete(record: IntakeRecordEntity) {
        MaterialAlertDialogBuilder(this).setTitle(R.string.delete_record_title).setMessage(R.string.delete_record_message)
            .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch { repository.deleteRecord(record.id); com.youranata.caloriewidget.widget.CalorieWidgetProvider.refreshAll(this@MainActivity); renderSummary() }
            }.show()
    }

    private fun showBudgetDialog() {
        val input = android.widget.EditText(this).apply { hint = "kcal"; inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL }
        MaterialAlertDialogBuilder(this).setTitle(R.string.daily_budget).setView(input)
            .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save) { _, _ ->
                val kcal = input.text.toString().toBigDecimalOrNull()
                if (kcal == null || kcal <= BigDecimal.ZERO) { Toast.makeText(this, R.string.invalid_budget, Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                val milli = runCatching { kcal.movePointRight(3).setScale(0, RoundingMode.HALF_UP).longValueExact() }.getOrNull()
                if (milli == null || milli <= 0) { Toast.makeText(this, R.string.invalid_budget, Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                lifecycleScope.launch { repository.setBudget(today, milli); renderSummary(); com.youranata.caloriewidget.widget.CalorieWidgetProvider.refreshAll(this@MainActivity) }
            }.show()
    }

    override fun onResume() { super.onResume(); lifecycleScope.launch { repository.ensureDate(); renderSummary() } }
}
