package com.youranata.caloriewidget

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.youranata.caloriewidget.data.entity.IntakeRecordEntity
import com.youranata.caloriewidget.databinding.ActivityDayDetailBinding
import com.youranata.caloriewidget.domain.CalorieMath
import com.youranata.caloriewidget.domain.DateManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DayDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDayDetailBinding
    private val repository get() = (application as CalorieWidgetApp).repository
    private lateinit var date: String
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDayDetailBinding.inflate(layoutInflater); setContentView(binding.root)
        date = intent.getStringExtra(EXTRA_DATE) ?: DateManager.todayString()
        binding.title.text = DateManager.displayDate(date)
        binding.backButton.setOnClickListener { finish() }
        binding.addButton.setOnClickListener { startActivity(Intent(this, InputActivity::class.java).putExtra(InputActivity.EXTRA_DATE, date)) }
        lifecycleScope.launch { repository.observeRecords(date).collectLatest { render(it); renderSummary() } }
    }
    private fun renderSummary() { lifecycleScope.launch { val s = repository.getSummary(date); binding.summary.text = "摄入 ${CalorieMath.milliKcalToDisplay(s.totalMilliKcal)} / 预算 ${CalorieMath.milliKcalToDisplay(s.budgetMilliKcal)} kcal · 剩余 ${CalorieMath.milliKcalToDisplay(s.remainingMilliKcal)} kcal" } }
    private fun render(records: List<IntakeRecordEntity>) {
        binding.recordsContainer.removeAllViews()
        records.forEach { r ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_record, binding.recordsContainer, false)
            row.findViewById<TextView>(R.id.record_time).text = DateManager.displayTime(r.timestamp)
            row.findViewById<TextView>(R.id.record_note).text = r.note.ifBlank { getString(R.string.no_note) }
            row.findViewById<TextView>(R.id.record_amount).text = "${CalorieMath.milliKcalToDisplay(r.amountMilliKcal)} kcal"
            row.findViewById<View>(R.id.edit_record).setOnClickListener { startActivity(Intent(this, InputActivity::class.java).putExtra(InputActivity.EXTRA_RECORD_ID, r.id)) }
            row.findViewById<View>(R.id.delete_record).setOnClickListener { lifecycleScope.launch { repository.deleteRecord(r.id); renderSummary() } }
            binding.recordsContainer.addView(row)
        }
    }
    companion object { const val EXTRA_DATE = "date" }
}
