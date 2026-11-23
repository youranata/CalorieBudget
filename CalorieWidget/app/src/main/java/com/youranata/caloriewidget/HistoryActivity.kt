package com.youranata.caloriewidget

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.youranata.caloriewidget.databinding.ActivityHistoryBinding
import com.youranata.caloriewidget.domain.*
import com.youranata.caloriewidget.ui.BaseActivity
import kotlinx.coroutines.launch
import java.time.LocalDate

class HistoryActivity : BaseActivity() {
    private lateinit var binding: ActivityHistoryBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater); content(binding.root)
        binding.backButton.setOnClickListener { finish() }
        binding.pickDateButton.setOnClickListener {
            val today = DateManager.today()
            DatePickerDialog(this, { _, y, m, d -> open(LocalDate.of(y, m + 1, d).toString()) }, today.year, today.monthValue - 1, today.dayOfMonth).show()
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.observeHistory().collect { items ->
                    binding.historyContainer.removeAllViews(); binding.emptyText.isVisible = items.isEmpty()
                    items.forEach { day ->
                        val row = LayoutInflater.from(this@HistoryActivity).inflate(R.layout.item_day_summary, binding.historyContainer, false)
                        row.findViewById<TextView>(R.id.day_date).text = DateManager.displayDate(day.date)
                        row.findViewById<TextView>(R.id.day_total).text = if (day.recordCount == 0) "尚无摄入记录" else "摄入 ${CalorieMath.milliKcalToDisplay(day.totalMilliKcal)} kcal"
                        row.findViewById<TextView>(R.id.day_budget).text = "预算 ${CalorieMath.milliKcalToDisplay(day.budgetMilliKcal)} kcal · ${day.recordCount} 条记录"
                        row.findViewById<TextView>(R.id.day_remaining).text = "剩余 ${CalorieMath.milliKcalToDisplay(day.remainingMilliKcal)} kcal"
                        row.setOnClickListener { open(day.date) }; binding.historyContainer.addView(row)
                    }
                }
            }
        }
    }
    private fun open(date: String) { startActivity(Intent(this, DayDetailActivity::class.java).putExtra(DayDetailActivity.EXTRA_DATE, date)) }
}
