package com.example.caloriewidget.domain

import com.example.caloriewidget.data.DaySummary
import java.time.LocalDate

object Statistics {
    fun recorded(days: List<DaySummary>) = days.filter { it.recordCount > 0 }
    fun range(days: List<DaySummary>, count: Long?, today: LocalDate): List<DaySummary> =
        days.filter { it.date <= today.toString() && (count == null || it.date >= today.minusDays(count - 1).toString()) }
    fun average(days: List<DaySummary>): Long? {
        val values = recorded(days)
        return if (values.isEmpty()) null else values.fold(java.math.BigDecimal.ZERO) { acc, day ->
            acc.add(java.math.BigDecimal.valueOf(day.totalMilliKcal))
        }.divide(java.math.BigDecimal.valueOf(values.size.toLong()), 0, java.math.RoundingMode.HALF_UP).longValueExact()
    }
}
