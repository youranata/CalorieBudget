package com.youranata.caloriewidget

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.youranata.caloriewidget.data.DaySummary
import com.youranata.caloriewidget.databinding.ActivityHistoryBinding
import com.youranata.caloriewidget.domain.CalorieMath
import com.youranata.caloriewidget.domain.DateManager
import kotlinx.coroutines.launch

class HistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHistoryBinding
    private val repository get() = (application as CalorieWidgetApp).repository
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater); setContentView(binding.root)
        binding.backButton.setOnClickListener { finish() }
        lifecycleScope.launch { render(repository.getHistory()) }
    }
    private fun render(items: List<DaySummary>) {
        binding.historyContainer.removeAllViews()
        if (items.isEmpty()) { binding.emptyText.visibility = View.VISIBLE; return }
        binding.emptyText.visibility = View.GONE
        items.forEach { day ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_day_summary, binding.historyContainer, false)
            row.findViewById<TextView>(R.id.day_date).text = DateManager.displayDate(day.date)
            row.findViewById<TextView>(R.id.day_total).text = "摄入 ${CalorieMath.milliKcalToDisplay(day.totalMilliKcal)} kcal"
            row.findViewById<TextView>(R.id.day_budget).text = "预算 ${CalorieMath.milliKcalToDisplay(day.budgetMilliKcal)} · ${day.recordCount} 条记录"
            row.findViewById<TextView>(R.id.day_remaining).text = "剩余 ${CalorieMath.milliKcalToDisplay(day.remainingMilliKcal)} kcal"
            row.setOnClickListener { startActivity(Intent(this, DayDetailActivity::class.java).putExtra(DayDetailActivity.EXTRA_DATE, day.date)) }
            binding.historyContainer.addView(row)
        }
    }
}
