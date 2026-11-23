package com.youranata.caloriewidget

import com.youranata.caloriewidget.data.DaySummary
import com.youranata.caloriewidget.domain.Statistics
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class StatisticsTest {
    @Test fun missingDaysAreExcludedButExplicitZeroIsIncluded() {
        val days = listOf(DaySummary("2026-11-23", 2000, 1000, 1000, 1),
            DaySummary("2026-11-22", 2000, 0, 2000, 0), DaySummary("2026-11-20", 2000, 0, 2000, 1))
        assertEquals(500L, Statistics.average(days))
        assertEquals(1000L, Statistics.average(Statistics.range(days, 3, LocalDate.parse("2026-11-23"))))
        assertNull(Statistics.average(listOf(days[1])))
    }
}
