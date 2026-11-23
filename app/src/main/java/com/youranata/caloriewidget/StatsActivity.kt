package com.youranata.caloriewidget

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.youranata.caloriewidget.data.DaySummary
import com.youranata.caloriewidget.databinding.ActivityStatsBinding
import com.youranata.caloriewidget.domain.CalorieMath
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

class StatsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityStatsBinding
    private val repository get() = (application as CalorieWidgetApp).repository
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatsBinding.inflate(layoutInflater); setContentView(binding.root)
        binding.backButton.setOnClickListener { finish() }
        binding.range7.setOnClickListener { load(7) }; binding.range30.setOnClickListener { load(30) }; binding.range90.setOnClickListener { load(90) }; binding.rangeAll.setOnClickListener { load(null) }
        load(7)
    }
    private fun load(days: Long?) { lifecycleScope.launch {
        val items = if (days == null) repository.getHistory() else repository.getHistorySince(days)
        binding.chart.setData(items.reversed())
        if (items.isEmpty()) { binding.stats.text = getString(R.string.no_history); return@launch }
        val avg7 = repository.getHistorySince(7).takeIf { it.isNotEmpty() }?.map { it.totalMilliKcal }?.average()
        val avg30 = repository.getHistorySince(30).takeIf { it.isNotEmpty() }?.map { it.totalMilliKcal }?.average()
        val all = repository.getHistory()
        val average = items.map { it.totalMilliKcal }.average()
        val max = items.maxOf { it.totalMilliKcal }; val min = items.minOf { it.totalMilliKcal }
        val over = items.count { it.totalMilliKcal > it.budgetMilliKcal }
        binding.stats.text = """最近 7 天平均 ${avg7?.let { CalorieMath.milliKcalToDisplay(it.roundToLong()) } ?: "—"} kcal
最近 30 天平均 ${avg30?.let { CalorieMath.milliKcalToDisplay(it.roundToLong()) } ?: "—"} kcal
当前范围平均 ${CalorieMath.milliKcalToDisplay(average.roundToLong())} kcal
最高 ${CalorieMath.milliKcalToDisplay(max)} kcal · 最低 ${CalorieMath.milliKcalToDisplay(min)} kcal
超出预算 ${over} 天 · 历史有记录 ${all.size} 天"""
    } }
}
