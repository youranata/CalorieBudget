package com.youranata.caloriewidget.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateManager {
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val displayFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.getDefault())
    fun today(): LocalDate = LocalDate.now()
    fun todayString(): String = today().format(dateFormatter)
    fun dateFromTimestamp(timestamp: Long): String = Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormatter)
    fun timestamp(date: LocalDate, hour: Int, minute: Int): Long =
        date.atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    fun timestampToLocalDateTime(timestamp: Long): LocalDateTime = Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault()).toLocalDateTime()
    fun parseDate(value: String): LocalDate = LocalDate.parse(value, dateFormatter)
    fun displayDate(value: String): String = runCatching { parseDate(value).format(displayFormatter) }.getOrDefault(value)
    fun displayTime(timestamp: Long): String = timestampToLocalDateTime(timestamp)
        .format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
}
