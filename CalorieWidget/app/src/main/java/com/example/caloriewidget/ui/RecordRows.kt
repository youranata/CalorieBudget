package com.example.caloriewidget.ui

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.caloriewidget.*
import com.example.caloriewidget.data.entity.IntakeRecordEntity
import com.example.caloriewidget.domain.*

object RecordRows {
    fun render(activity: AppCompatActivity, container: LinearLayout, records: List<IntakeRecordEntity>, onDelete: (Long) -> Unit) {
        container.removeAllViews()
        records.forEach { record ->
            val row = LayoutInflater.from(activity).inflate(R.layout.item_record, container, false)
            row.findViewById<TextView>(R.id.record_time).text = DateManager.displayTime(record.timestamp, record.zoneId)
            row.findViewById<TextView>(R.id.record_note).text = record.note.ifBlank { activity.getString(R.string.no_note) }
            row.findViewById<TextView>(R.id.record_amount).text = "${CalorieMath.milliKcalToDisplay(record.amountMilliKcal)} kcal"
            row.findViewById<TextView>(R.id.record_original).text = "${CalorieMath.inputValue(record.originalAmountMilli)} ${record.originalUnit} · ${CalorieMath.milliKjToDisplay(record.amountMilliKcal)} kJ"
            row.findViewById<View>(R.id.edit_record).setOnClickListener {
                activity.startActivity(Intent(activity, InputActivity::class.java).putExtra(InputActivity.EXTRA_RECORD_ID, record.id))
            }
            row.findViewById<View>(R.id.delete_record).setOnClickListener {
                MaterialAlertDialogBuilder(activity).setTitle(R.string.delete_record_title).setMessage(R.string.delete_record_message)
                    .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.delete) { _, _ -> onDelete(record.id) }.show()
            }
            container.addView(row)
        }
    }
}
