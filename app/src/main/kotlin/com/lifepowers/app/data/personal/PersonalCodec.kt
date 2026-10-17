package com.lifepowers.app.data.personal

import java.time.DayOfWeek
import java.time.LocalTime
import java.util.Locale

/**
 * Comma separated list columns of the personal database: reminder times as HH:mm, days as ISO day
 * numbers 1 to 7, tags as plain words. Unreadable items are skipped rather than failing the row.
 */
object PersonalCodec {

    fun encodeTimes(times: List<LocalTime>): String =
        times.distinct().sorted().joinToString(",") { String.format(Locale.ROOT, "%02d:%02d", it.hour, it.minute) }

    fun decodeTimes(s: String?): List<LocalTime> =
        split(s).mapNotNull { runCatching { LocalTime.parse(it) }.getOrNull() }
            .map { it.withSecond(0).withNano(0) }
            .distinct()
            .sorted()

    fun encodeDays(days: Set<DayOfWeek>): String = days.map { it.value }.sorted().joinToString(",")

    fun decodeDays(s: String?): Set<DayOfWeek> =
        split(s).mapNotNull { it.toIntOrNull() }.filter { it in 1..7 }.map { DayOfWeek.of(it) }.toSet()

    fun encodeTags(tags: List<String>): String =
        tags.map { it.replace(",", " ").trim() }.filter { it.isNotEmpty() }.distinct().joinToString(",")

    fun decodeTags(s: String?): List<String> = split(s).distinct()

    private fun split(s: String?): List<String> =
        s.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
}
