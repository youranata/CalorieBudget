package com.youranata.caloriewidget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.youranata.caloriewidget.data.DaySummary
import com.youranata.caloriewidget.domain.CalorieMath

class StatsChartView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(46, 125, 101); strokeWidth = 6f; style = Paint.Style.STROKE }
    private val budgetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(232, 151, 45); strokeWidth = 3f; style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 28f }
    private var data: List<DaySummary> = emptyList()
    fun setData(value: List<DaySummary>) { data = value; invalidate() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas); if (data.isEmpty()) { canvas.drawText("暂无可绘制的历史记录", 24f, height / 2f, textPaint); return }
        val left = 70f; val right = width - 24f; val top = 28f; val bottom = height - 48f
        val maxValue = maxOf(data.maxOf { it.totalMilliKcal }, data.maxOf { it.budgetMilliKcal }, 1L).toFloat()
        fun x(index: Int) = if (data.size == 1) (left + right) / 2 else left + (right - left) * index / (data.size - 1)
        fun y(value: Long) = bottom - (bottom - top) * value / maxValue
        val path = Path(); data.forEachIndexed { index, item -> if (index == 0) path.moveTo(x(index), y(item.totalMilliKcal)) else path.lineTo(x(index), y(item.totalMilliKcal)) }
        canvas.drawPath(path, linePaint)
        data.forEachIndexed { index, item ->
            canvas.drawCircle(x(index), y(item.totalMilliKcal), 7f, linePaint)
            canvas.drawLine(x(index), y(item.budgetMilliKcal), x(index), bottom, budgetPaint)
            if (index == 0 || index == data.lastIndex || data.size <= 7) canvas.drawText(item.date.substring(5), x(index) - 28, height - 12f, textPaint)
        }
        canvas.drawText("绿色：摄入  橙色：预算", left, top + 2, textPaint)
    }
}
