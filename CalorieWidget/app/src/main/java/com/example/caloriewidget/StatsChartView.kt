package com.example.caloriewidget

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.example.caloriewidget.data.DaySummary
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Date-proportional line chart. Missing dates break the line instead of becoming zeros. */
class StatsChartView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val budgetDash by lazy { DashPathEffect(floatArrayOf(5 * density, 4 * density), 0f) }
    private var days = emptyList<DaySummary>()
    private var start = LocalDate.now()
    private var end = start
    private val density get() = resources.displayMetrics.density
    fun setData(values: List<DaySummary>, from: LocalDate?, to: LocalDate) {
        days = values.sortedBy { it.date }
        start = from ?: days.firstOrNull()?.let { LocalDate.parse(it.date) } ?: to
        end = to; invalidate()
        contentDescription = "每日摄入趋势与预算对比，${days.count { it.recordCount > 0 }} 天有摄入记录。折线断开表示缺失日期。"
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val text = context.getColor(R.color.text_secondary)
        val intake = context.getColor(R.color.calorie_primary)
        val budget = context.getColor(R.color.chart_budget)
        val left = 56 * density; val right = width - 18 * density
        val top = 42 * density; val bottom = height - 34 * density
        if (right <= left || bottom <= top) return
        val max = maxOf(days.maxOfOrNull { maxOf(it.totalMilliKcal, it.budgetMilliKcal) } ?: 0, 1_000_000).toFloat() * 1.12f
        val span = ChronoUnit.DAYS.between(start, end).coerceAtLeast(1)
        fun x(date: String) = left + (right - left) * ChronoUnit.DAYS.between(start, LocalDate.parse(date)).toFloat() / span
        fun y(value: Long) = bottom - (bottom - top) * value.toFloat() / max
        paint.textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 10f, resources.displayMetrics)
        paint.style = Paint.Style.FILL; paint.color = text
        canvas.drawText("kcal", 4 * density, top - 12 * density, paint)
        canvas.drawText("摄入 ━    预算 ┄    断线 = 无记录", left, 18 * density, paint)
        for (step in 0..4) {
            val pos = bottom - (bottom - top) * step / 4
            paint.color = text; paint.alpha = 45; paint.strokeWidth = density
            canvas.drawLine(left, pos, right, pos, paint)
            paint.alpha = 255; canvas.drawText((max / 1000 * step / 4).toInt().toString(), 0f, pos + 3 * density, paint)
        }
        paint.color = budget; paint.strokeWidth = 2 * density
        paint.pathEffect = budgetDash
        var previous: DaySummary? = null
        days.forEach { day ->
            val prev = previous
            if (prev != null && ChronoUnit.DAYS.between(LocalDate.parse(prev.date), LocalDate.parse(day.date)) == 1L)
                canvas.drawLine(x(prev.date), y(prev.budgetMilliKcal), x(day.date), y(day.budgetMilliKcal), paint)
            previous = day
        }
        paint.pathEffect = null
        days.forEach { day -> paint.color = budget; canvas.drawCircle(x(day.date), y(day.budgetMilliKcal), 2 * density, paint) }
        previous = null; paint.color = intake; paint.strokeWidth = 2.5f * density
        days.forEach { day ->
            val prev = previous
            if (day.recordCount > 0) {
                if (prev != null && prev.recordCount > 0 && ChronoUnit.DAYS.between(LocalDate.parse(prev.date), LocalDate.parse(day.date)) == 1L)
                    canvas.drawLine(x(prev.date), y(prev.totalMilliKcal), x(day.date), y(day.totalMilliKcal), paint)
                canvas.drawCircle(x(day.date), y(day.totalMilliKcal), 3.5f * density, paint)
            }
            previous = day
        }
        paint.color = text
        canvas.drawText(start.toString().substring(5), left, height - 8 * density, paint)
        val label = end.toString().substring(5)
        canvas.drawText(label, right - paint.measureText(label), height - 8 * density, paint)
        if (days.none { it.recordCount > 0 }) canvas.drawText("此范围尚无摄入记录", left + 12 * density, (top + bottom) / 2, paint)
    }
}
