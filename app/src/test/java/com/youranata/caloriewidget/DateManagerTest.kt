package com.youranata.caloriewidget

import com.youranata.caloriewidget.domain.DateManager
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DateManagerTest {
    @Test fun timestampUsesLocalDate() {
        val timestamp = DateManager.timestamp(LocalDate.of(2025, 1, 2), 8, 30)
        assertEquals("2025-01-02", DateManager.dateFromTimestamp(timestamp))
    }
}
