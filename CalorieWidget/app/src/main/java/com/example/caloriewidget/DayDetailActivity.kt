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
                combine(repository.observeRecords(date), repository.observeHistory()) { entries, history -> entries to history.first { it.date == date } }
                    .collect { (entries, s) ->
                        binding.summary.text = "摄入 ${CalorieMath.milliKcalToDisplay(s.totalMilliKcal)} kcal / ${CalorieMath.milliKjToDisplay(s.totalMilliKcal)} kJ\n预算 ${CalorieMath.milliKcalToDisplay(s.budgetMilliKcal)} kcal · 剩余 ${CalorieMath.milliKcalToDisplay(s.remainingMilliKcal)} kcal"
                        binding.emptyText.isVisible = entries.isEmpty()
                        RecordRows.render(this@DayDetailActivity, binding.recordsContainer, entries) { id ->
                            (application as CalorieWidgetApp).appScope.launch { repository.deleteRecord(id); CalorieWidgetProvider.refreshAll(this@DayDetailActivity) }
                        }
                    }
            }
        }
    }
    companion object { const val EXTRA_DATE = "date" }
}
