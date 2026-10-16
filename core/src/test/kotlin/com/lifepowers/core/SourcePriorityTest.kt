package com.lifepowers.core

import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class SourcePriorityTest {
    private fun ms(s: String) = LocalDateTime.parse(s).toInstant(ZoneOffset.ofHours(-7)).toEpochMilli()

    @Test fun ouraApiWinsSleepOverHealthConnectCopy() {
        val api = Sample(Metrics.SLEEP_TOTAL_MIN, 430.0, ms("2026-10-15T23:40"), ms("2026-10-16T06:50"), -420, SourcePriority.OURA_API)
        val hc = Sample(Metrics.SLEEP_TOTAL_MIN, 425.0, ms("2026-10-15T23:41"), ms("2026-10-16T06:49"), -420, SourcePriority.HC_OURA)
        val kept = SourcePriority.select(listOf(hc, api))
        assertEquals(listOf(SourcePriority.OURA_API), kept.map { it.source })
    }

    @Test fun stepsAreNotDoubledAcrossSources() {
        val watch = (0 until 4).map { Sample(Metrics.ACTIVITY_STEPS, 2000.0, ms("2026-10-16T09:00") + it * 3_600_000L, null, -420, SourcePriority.HC_SAMSUNG) }
        val ring = Sample(Metrics.ACTIVITY_STEPS, 7600.0, ms("2026-10-16T00:00"), null, -420, SourcePriority.OURA_API)
        val kept = SourcePriority.select(watch + ring)
        val total = Rollup.daily(kept).first { it.agg == Agg.SUM }.value
        assertEquals(8000.0, total, 1e-9)
    }

    @Test fun unlistedMetricsKeepEverySource() {
        val a = Sample(Metrics.GLUCOSE_SAMPLE, 90.0, ms("2026-10-16T08:00"), null, -420, "health_connect:com.dexcom.g7")
        val b = Sample(Metrics.GLUCOSE_SAMPLE, 95.0, ms("2026-10-16T08:05"), null, -420, "libreview_import")
        assertEquals(2, SourcePriority.select(listOf(a, b)).size)
    }

    @Test fun differentDaysAreJudgedSeparately() {
        val d1 = Sample(Metrics.ACTIVITY_STEPS, 5000.0, ms("2026-10-15T12:00"), null, -420, SourcePriority.OURA_API)
        val d2a = Sample(Metrics.ACTIVITY_STEPS, 6000.0, ms("2026-10-16T12:00"), null, -420, SourcePriority.OURA_API)
        val d2b = Sample(Metrics.ACTIVITY_STEPS, 6100.0, ms("2026-10-16T12:00"), null, -420, SourcePriority.HC_SAMSUNG)
        val kept = SourcePriority.select(listOf(d1, d2a, d2b)).map { it.value }
        assertEquals(listOf(5000.0, 6100.0), kept)
    }
}
