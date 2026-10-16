package com.lifepowers.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Local date and epoch conversions pinned to an explicit zone offset in minutes. */
object Dates {
    fun offset(zoneOffsetMin: Int): ZoneOffset = ZoneOffset.ofTotalSeconds(zoneOffsetMin * 60)

    fun localDate(utcMs: Long, zoneOffsetMin: Int): LocalDate =
        Instant.ofEpochMilli(utcMs).atOffset(offset(zoneOffsetMin)).toLocalDate()

    fun startOfDayMs(date: LocalDate, zoneOffsetMin: Int): Long =
        date.atStartOfDay().toInstant(offset(zoneOffsetMin)).toEpochMilli()

    fun today(zoneOffsetMin: Int): LocalDate = localDate(System.currentTimeMillis(), zoneOffsetMin)

    fun iso(date: LocalDate): String = date.toString()

    fun parseIso(s: String?): LocalDate? = s?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}
