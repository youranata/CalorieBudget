package com.youranata.caloriewidget

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.youranata.caloriewidget.databinding.ActivityStatsBinding
import com.youranata.caloriewidget.domain.*
import com.youranata.caloriewidget.ui.BaseActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class StatsActivity : BaseActivity() {
    private lateinit var binding: ActivityStatsBinding
    private val range = MutableStateFlow<Long?>(7)
    private val today = MutableStateFlow(DateManager.today())
    override fun onDateChecked(date: String) { today.value = DateManager.parseDate(date) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatsBinding.inflate(layoutInflater); content(binding.root)
        binding.backButton.setOnClickListener { finish() }
        binding.range7.setOnClickListener { range.value = 7 }; binding.range30.setOnClickListener { range.value = 30 }
        binding.range90.setOnClickListener { range.value = 90 }; binding.rangeAll.setOnClickListener { range.value = null }
        if (savedInstanceState?.containsKey("range") == true) range.value = savedInstanceState.getLong("range").takeIf { it > 0 }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(repository.observeHistory(), range, today) { days, count, now -> Triple(days, count, now) }.collect { (days, count, now) ->
                    val all = Statistics.range(days, null, now)
                    val items = Statistics.range(days, count, now)
                    binding.chart.setData(items, count?.let { now.minusDays(it - 1) }, now)
                    fun avg(values: List<com.youranata.caloriewidget.data.DaySummary>) = Statistics.average(values)?.let { "${CalorieMath.milliKcalToDisplay(it)} kcal" } ?: "—"
                    val recorded = Statistics.recorded(items)
                    val high = recorded.maxOfOrNull { it.totalMilliKcal }?.let(CalorieMath::milliKcalToDisplay) ?: "—"
                    val low = recorded.minOfOrNull { it.totalMilliKcal }?.let(CalorieMath::milliKcalToDisplay) ?: "—"
                    binding.stats.text = "最近 7 天平均  ${avg(Statistics.range(all, 7, now))}\n最近 30 天平均  ${avg(Statistics.range(all, 30, now))}\n历史平均  ${avg(all)}\n当前范围平均  ${avg(items)}\n当前范围最高  $high kcal\n当前范围最低  $low kcal\n当前范围超出预算  ${recorded.count { it.totalMilliKcal > it.budgetMilliKcal }} 天\n历史超出预算  ${Statistics.recorded(all).count { it.totalMilliKcal > it.budgetMilliKcal }} 天\n平均值仅包含有记录的日期（含零摄入记录）。"
                }
            }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putLong("range", range.value ?: 0); super.onSaveInstanceState(outState) }
}
