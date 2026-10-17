package com.lifepowers.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GoalsTest {
    private val thu = LocalDate.of(2026, 10, 15)

    @Test fun periodsFollowCadence() {
        assertEquals(Period(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18)), Goals.periodOf(thu, Cadence.WEEKLY))
        assertEquals(Period(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)), Goals.periodOf(thu, Cadence.MONTHLY))
        assertEquals(Period(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)), Goals.periodOf(thu, Cadence.YEARLY))
        val last = Goals.lastPeriods(thu, Cadence.WEEKLY, 3)
        assertEquals(LocalDate.of(2026, 9, 28), last.first().start)
        assertEquals(LocalDate.of(2026, 10, 12), last.last().start)
    }

    @Test fun countGoalStatesUseForecastColors() {
        val spec = GoalSpec(Cadence.WEEKLY, TargetKind.COUNT, target = 3.0)
        val week = Goals.periodOf(thu, Cadence.WEEKLY)
        val two = listOf(LocalDate.of(2026, 10, 12) to 1.0, LocalDate.of(2026, 10, 14) to 1.0)
        val p = Goals.progress(spec, week, two)
        assertEquals(PeriodState.MOSTLY, p.state)
        assertEquals(Tier.GOOD, p.state.tier)
        assertEquals(PeriodState.UNTOUCHED, Goals.progress(spec, week, emptyList()).state)
        assertEquals(PeriodState.MET, Goals.progress(spec, week, two + (thu to 1.0)).state)
        assertEquals(PeriodState.STARTED, Goals.progress(spec, week, two.take(1)).state)
    }

    @Test fun minutesSumAndMetricDaysQualify() {
        val med = GoalSpec(Cadence.DAILY, TargetKind.MINUTES, target = 10.0)
        assertEquals(PeriodState.MET, Goals.progress(med, Period(thu, thu), listOf(thu to 4.0, thu to 7.0)).state)
        val deep = GoalSpec(Cadence.WEEKLY, TargetKind.METRIC, target = 5.0, metric = Metrics.SLEEP_DEEP_MIN, comparison = Comparison.AT_LEAST, threshold = 60.0)
        val week = Goals.periodOf(thu, Cadence.WEEKLY)
        val nights = (12..16).associate { LocalDate.of(2026, 10, it) to if (it == 13) 40.0 else 70.0 }
        val p = Goals.progress(deep, week, emptyList(), nights)
        assertEquals(4.0, p.value, 0.0)
        assertEquals(PeriodState.MOSTLY, p.state)
    }

    @Test fun runSkipsTheOpenPeriodButNotAClosedMiss() {
        val spec = GoalSpec(Cadence.DAILY, TargetKind.CHECK)
        val checks = (1..4).map { thu.minusDays(it.toLong()) to 1.0 }
        val history = Goals.history(spec, thu, 7, checks)
        assertEquals(4, Goals.currentRun(history, thu))
        val withGap = checks.filter { it.first != thu.minusDays(2) }
        assertEquals(1, Goals.currentRun(Goals.history(spec, thu, 7, withGap), thu))
        assertEquals(2, Goals.bestRun(Goals.history(spec, thu, 7, withGap)))
    }

    @Test fun closingLineOnlyOnLastDayWhenNotMet() {
        val spec = GoalSpec(Cadence.WEEKLY, TargetKind.COUNT, target = 3.0)
        val sunday = LocalDate.of(2026, 10, 18)
        val week = Goals.periodOf(sunday, Cadence.WEEKLY)
        val p = Goals.progress(spec, week, listOf(thu to 1.0, sunday to 1.0))
        assertEquals("The week is ending, 2 of 3 done.", Goals.closingLine(spec, p, sunday))
        assertNull(Goals.closingLine(spec, p, thu))
        val grid = Goals.dotGrid(spec, sunday, listOf(thu to 1.0))
        assertEquals(28, grid.size)
        assertTrue(grid.first { it.first == thu }.second)
    }
}
