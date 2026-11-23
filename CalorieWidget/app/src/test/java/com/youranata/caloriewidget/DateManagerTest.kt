package com.youranata.caloriewidget

import com.youranata.caloriewidget.domain.DateManager
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class DateManagerTest {
    @Test fun nextMidnightHonorsSpringAndAutumnDst() {
        val zone = ZoneId.of("America/Los_Angeles")
        for ((date, hours) in listOf(LocalDate.of(2026, 3, 8) to 23L, LocalDate.of(2026, 11, 1) to 25L)) {
            val start = date.atStartOfDay(zone).toInstant()
            assertEquals(hours, (DateManager.nextMidnight(Clock.fixed(start, zone)) - start.toEpochMilli()) / 3_600_000)
        }
    }
    @Test fun dateOwnershipUsesExplicitZone() {
        val instant = Instant.parse("2026-11-23T00:30:00Z").toEpochMilli()
        assertEquals("2026-11-22", DateManager.dateFromTimestamp(instant, ZoneId.of("America/Los_Angeles")))
        assertEquals("2026-11-23", DateManager.dateFromTimestamp(instant, ZoneId.of("Asia/Hong_Kong")))
    }
    @Test fun skipsMultipleDaysWithoutReplayingReset() {
        val clock = Clock.fixed(Instant.parse("2026-11-26T12:00:00Z"), ZoneId.of("UTC"))
        assertEquals("2026-11-26", DateManager.today(clock).toString())
        assertEquals(Instant.parse("2026-11-27T00:00:00Z").toEpochMilli(), DateManager.nextMidnight(clock))
    }
}
