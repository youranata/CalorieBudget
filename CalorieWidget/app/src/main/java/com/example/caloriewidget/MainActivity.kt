package com.example.caloriewidget

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import android.widget.EditText
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.caloriewidget.databinding.ActivityMainBinding
import com.example.caloriewidget.domain.*
import com.example.caloriewidget.ui.*
import com.example.caloriewidget.widget.CalorieWidgetProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainActivity : BaseActivity() {
    private lateinit var binding: ActivityMainBinding
    private val date = MutableStateFlow(DateManager.todayString())
    override fun onDateChecked(date: String) { this.date.value = date }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater); content(binding.root)
        binding.addRecordButton.setOnClickListener { startActivity(Intent(this, InputActivity::class.java)) }
        binding.historyButton.setOnClickListener { startActivity(Intent(this, HistoryActivity::class.java)) }
        binding.statsButton.setOnClickListener { startActivity(Intent(this, StatsActivity::class.java)) }
        binding.budgetButton.setOnClickListener { showBudgetDialog() }
        binding.alarmButton.isVisible = Build.VERSION.SDK_INT >= 31
        binding.alarmButton.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 31) runCatching {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            }.onFailure { Toast.makeText(this, "系统未提供此权限页面", Toast.LENGTH_SHORT).show() }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                date.flatMapLatest { day ->
                    repository.ensureDate(day)
                    combine(repository.observeRecords(day), repository.observeHistory()) { entries, history -> entries to history.first { it.date == day } }
                }.collect { (entries, summary) ->
                    binding.todayDate.text = DateManager.displayDate(summary.date)
                    binding.remaining.text = CalorieMath.milliKcalToDisplay(summary.remainingMilliKcal)
                    binding.remainingKj.text = "${CalorieMath.milliKjToDisplay(summary.remainingMilliKcal)} kJ"
                    binding.consumed.text = "已摄入 ${CalorieMath.milliKcalToDisplay(summary.totalMilliKcal)} kcal"
                    binding.budget.text = "每日预算 ${CalorieMath.milliKcalToDisplay(summary.budgetMilliKcal)} kcal / ${CalorieMath.milliKjToDisplay(summary.budgetMilliKcal)} kJ"
                    binding.remaining.setTextColor(getColor(if (summary.remainingMilliKcal < 0) R.color.calorie_warning else R.color.calorie_primary))
                    binding.emptyText.isVisible = entries.isEmpty()
                    RecordRows.render(this@MainActivity, binding.recordsContainer, entries) { id ->
                        (application as CalorieWidgetApp).appScope.launch { repository.deleteRecord(id); CalorieWidgetProvider.refreshAll(this@MainActivity) }
                    }
                }
            }
        }
    }
    private fun showBudgetDialog() {
        lifecycleScope.launch {
            val summary = repository.getSummary(DateManager.todayString())
            val input = EditText(this@MainActivity).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                setText(CalorieMath.inputValue(summary.budgetMilliKcal)); selectAll()
                setPadding(48, 16, 48, 16)
            }
            val dialog = MaterialAlertDialogBuilder(this@MainActivity).setTitle(R.string.daily_budget)
                .setMessage("立即应用于今天和未来新日期；历史日期保留原预算。")
                .setView(input).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save, null).create()
            dialog.setOnShowListener {
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val amount = runCatching { CalorieMath.parseBudget(input.text.toString()) }.getOrElse { input.error = it.message; return@setOnClickListener }
                    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled = false
                    (application as CalorieWidgetApp).appScope.launch {
                        repository.setDefaultBudget(amount); CalorieWidgetProvider.refreshAll(this@MainActivity)
                    }
                    dialog.dismiss()
                }
            }
            dialog.show()
        }
    }
}
