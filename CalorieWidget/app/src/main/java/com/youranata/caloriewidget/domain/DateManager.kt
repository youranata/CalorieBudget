package com.youranata.caloriewidget.domain

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object DateManager {
    fun today(clock: Clock = Clock.systemDefaultZone()): LocalDate = LocalDate.now(clock)
    fun todayString(): String = today().toString()
    fun dateFromTimestamp(timestamp: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate().toString()
    fun timestamp(date: LocalDate, hour: Int, minute: Int, zone: ZoneId = ZoneId.systemDefault()): Long =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    fun nextMidnight(clock: Clock = Clock.systemDefaultZone()): Long =
        LocalDate.now(clock).plusDays(1).atStartOfDay(clock.zone).toInstant().toEpochMilli()
    fun parseDate(value: String): LocalDate = LocalDate.parse(value)
    fun displayDate(value: String): String = parseDate(value).format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
    fun displayTime(timestamp: Long, zoneId: String = ZoneId.systemDefault().id): String =
        Instant.ofEpochMilli(timestamp).atZone(zone(zoneId)).format(DateTimeFormatter.ofPattern("HH:mm"))
    fun zone(value: String): ZoneId = runCatching { ZoneId.of(value) }.getOrDefault(ZoneId.systemDefault())
}
