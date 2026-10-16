package com.lifepowers.core

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class RollupTest {
    private fun ms(date: String, time: String) =
        java.time.LocalDateTime.parse("${date}T$time").toInstant(ZoneOffset.ofHours(-7)).toEpochMilli()

    @Test fun nightEndingAssignsSleepToWakeDate() {
        val s = Sample(Metrics.SLEEP_TOTAL_MIN, 420.0, ms("2026-10-15", "23:50"), ms("2026-10-16", "06:45"), -420, "oura")
        assertEquals(LocalDate.of(2026, 10, 16), Days.localDay(s, DayRule.NIGHT_ENDING))
    }

    @Test fun lateAfternoonNapGoesToNextNight() {
        val s = Sample(Metrics.SLEEP_TOTAL_MIN, 40.0, ms("2026-10-15", "15:00"), ms("2026-10-15", "15:40"), -420, "oura")
        assertEquals(LocalDate.of(2026, 10, 16), Days.localDay(s, DayRule.NIGHT_ENDING))
    }

    @Test fun glucoseRollupHasTimeInRange() {
        val base = ms("2026-10-16", "08:00")
        val values = listOf(90.0, 110.0, 160.0, 130.0)
        val samples = values.mapIndexed { i, v -> Sample(Metrics.GLUCOSE_SAMPLE, v, base + i * 300_000L, null, -420, "cgm") }
        val daily = Rollup.daily(samples)
        val tir = daily.first { it.agg == Agg.TIR_70_140 }
        assertEquals(0.75, tir.value, 1e-9)
        assertEquals(4, tir.n)
        assertEquals(LocalDate.of(2026, 10, 16), tir.localDate)
    }

    @Test fun stepsSumPerDay() {
        val d1 = ms("2026-10-16", "09:00"); val d2 = ms("2026-10-17", "09:00")
        val samples = listOf(
            Sample(Metrics.ACTIVITY_STEPS, 3000.0, d1, null, -420, "hc"),
            Sample(Metrics.ACTIVITY_STEPS, 5000.0, d1 + 3_600_000, null, -420, "hc"),
            Sample(Metrics.ACTIVITY_STEPS, 1000.0, d2, null, -420, "hc"),
        )
        val daily = Rollup.daily(samples).filter { it.agg == Agg.SUM }
        assertEquals(listOf(8000.0, 1000.0), daily.map { it.value })
    }
}
