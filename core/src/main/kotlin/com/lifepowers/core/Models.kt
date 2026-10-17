package com.lifepowers.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** One canonical measurement after normalization. */
data class Sample(
    val metric: String,
    val value: Double,
    val startUtcMs: Long,
    val endUtcMs: Long? = null,
    val zoneOffsetMin: Int,
    val source: String,
)

/** One aggregate for one metric on one local day. */
data class DailyValue(
    val localDate: LocalDate,
    val metric: String,
    val agg: Agg,
    val value: Double,
    val n: Int,
)

object Days {
    /**
     * Assigns a sample to a local day. Calendar metrics use the local date of the start time.
     * Night-ending metrics use the local date of the end time when the end falls between
     * 00:00 and 14:00, otherwise the day after the start.
     */
    fun localDay(sample: Sample, rule: DayRule): LocalDate {
        val offset = ZoneOffset.ofTotalSeconds(sample.zoneOffsetMin * 60)
        return when (rule) {
            DayRule.CALENDAR -> Instant.ofEpochMilli(sample.startUtcMs).atOffset(offset).toLocalDate()
            DayRule.NIGHT_ENDING -> {
                val endMs = sample.endUtcMs ?: sample.startUtcMs
                val end = Instant.ofEpochMilli(endMs).atOffset(offset)
                if (end.toLocalTime() < LocalTime.of(14, 0)) end.toLocalDate()
                else end.toLocalDate().plusDays(1)
            }
        }
    }
}
