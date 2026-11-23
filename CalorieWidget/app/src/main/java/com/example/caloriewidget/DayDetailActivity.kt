package com.example.caloriewidget

import android.content.Intent
import android.os.Bundle
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.caloriewidget.databinding.ActivityDayDetailBinding
import com.example.caloriewidget.domain.*
import com.example.caloriewidget.ui.*
import com.example.caloriewidget.widget.CalorieWidgetProvider
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class DayDetailActivity : BaseActivity() {
    private lateinit var binding: ActivityDayDetailBinding
    private lateinit var date: String
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDayDetailBinding.inflate(layoutInflater); content(binding.root)
        date = intent.getStringExtra(EXTRA_DATE) ?: DateManager.todayString()
        binding.title.text = DateManager.displayDate(date)
        binding.backButton.setOnClickListener { finish() }
        binding.addButton.setOnClickListener { startActivity(Intent(this, InputActivity::class.java).putExtra(InputActivity.EXTRA_DATE, date)) }
        lifecycleScope.launch {
            repository.ensureDate(date)
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(repository.observeRecords(date), repository.observeHistory()) { entries, history -> entries to history.firstOrNull { it.date == date } }
                    .collect { (entries, s) ->
                        val readOnly = s == null || s.isArchived || date != DateManager.todayString()
                        binding.addButton.isVisible = !readOnly
                        binding.summary.text = s?.let {
                            val intake = if (!readOnly || it.hasRecords) "${CalorieMath.milliKcalToDisplay(it.totalMilliKcal)} kcal / ${CalorieMath.milliKjToDisplay(it.totalMilliKcal)} kJ" else "尚无摄入记录"
                            "摄入 $intake\n预算 ${CalorieMath.milliKcalToDisplay(it.budgetMilliKcal)} kcal · 剩余 ${CalorieMath.milliKcalToDisplay(it.remainingMilliKcal)} kcal"
                        } ?: "该日期没有保存的数据"
                        binding.emptyText.isVisible = readOnly || entries.isEmpty()
                        binding.emptyText.text = if (readOnly) "历史仅保留每日目标和总摄入，只能查看。" else getString(R.string.activity_day_detail_text_3)
                        RecordRows.render(this@DayDetailActivity, binding.recordsContainer, if (readOnly) emptyList() else entries) { id ->
                            (application as CalorieWidgetApp).appScope.launch { repository.deleteRecord(id); CalorieWidgetProvider.refreshAll(this@DayDetailActivity) }
                        }
                    }
            }
        }
    }
    companion object { const val EXTRA_DATE = "date" }
}
