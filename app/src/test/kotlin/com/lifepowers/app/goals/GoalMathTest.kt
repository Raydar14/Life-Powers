package com.lifepowers.app.goals

import com.lifepowers.app.domain.Goal
import com.lifepowers.core.Cadence
import com.lifepowers.core.Goals
import com.lifepowers.core.PeriodProgress
import com.lifepowers.core.TargetKind
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class GoalMathTest {
    // A Wednesday.
    private val today = LocalDate.of(2026, 10, 14)

    private fun progress(goal: Goal, value: Double): PeriodProgress {
        val spec = GoalMath.spec(goal)
        val target = Goals.target(spec)
        return PeriodProgress(Goals.periodOf(today, goal.cadence), value, target, Goals.state(value, target))
    }

    @Test
    fun historyStartCoversGridAndOldestPeriod() {
        assertEquals(today.minusDays(27), GoalMath.historyStart(Cadence.DAILY, today))
        // Eight weeks back from the Monday of this week.
        assertEquals(LocalDate.of(2026, 8, 24), GoalMath.historyStart(Cadence.WEEKLY, today))
        assertEquals(LocalDate.of(2026, 5, 1), GoalMath.historyStart(Cadence.MONTHLY, today))
        assertEquals(LocalDate.of(2024, 1, 1), GoalMath.historyStart(Cadence.YEARLY, today))
    }

    @Test
    fun minutesRoundToOneDecimal() {
        assertEquals(10.0, GoalMath.minutes(0, 600_000))
        assertEquals(1.5, GoalMath.minutes(0, 90_000))
        assertEquals(0.4, GoalMath.minutes(0, 25_000))
        assertEquals(0.0, GoalMath.minutes(1000, 0))
    }

    @Test
    fun nextReminderLaterTodayOrNextAllowedDay() {
        val times = listOf(LocalTime.of(20, 0), LocalTime.of(8, 0))
        val now = today.atTime(9, 0)
        assertEquals(today.atTime(20, 0), GoalMath.nextReminder(times, emptySet(), now))
        assertEquals(today.plusDays(1).atTime(8, 0), GoalMath.nextReminder(times, emptySet(), today.atTime(20, 0)))
        // Only Mondays: from a Wednesday the next one is five days on.
        assertEquals(LocalDateTime.of(2026, 10, 19, 8, 0), GoalMath.nextReminder(times, setOf(DayOfWeek.MONDAY), now))
        assertNull(GoalMath.nextReminder(emptyList(), emptySet(), now))
    }

    @Test
    fun nextReminderSameWeekdayNextWeek() {
        val now = today.atTime(21, 0)
        assertEquals(today.plusDays(7).atTime(8, 0), GoalMath.nextReminder(listOf(LocalTime.of(8, 0)), setOf(DayOfWeek.WEDNESDAY), now))
    }

    @Test
    fun nextDailyRollsOver() {
        assertEquals(today.atTime(18, 0), GoalMath.nextDaily(LocalTime.of(18, 0), today.atTime(17, 59)))
        assertEquals(today.plusDays(1).atTime(18, 0), GoalMath.nextDaily(LocalTime.of(18, 0), today.atTime(18, 0)))
    }

    @Test
    fun reminderLinesAreGentle() {
        val walk = Goal(name = "Walk", cadence = Cadence.WEEKLY, kind = TargetKind.COUNT, target = 3.0)
        assertEquals("2 of 3 this week so far.", GoalMath.reminderLine(walk, progress(walk, 2.0)))
        assertEquals("Not yet this week. Whenever you are ready.", GoalMath.reminderLine(walk, progress(walk, 0.0)))
        assertNull(GoalMath.reminderLine(walk, progress(walk, 3.0)))

        val stretch = Goal(name = "Stretch", cadence = Cadence.DAILY, kind = TargetKind.CHECK)
        assertEquals("Not yet today. Whenever you are ready.", GoalMath.reminderLine(stretch, progress(stretch, 0.0)))

        val sit = Goal(name = "Sit", cadence = Cadence.DAILY, kind = TargetKind.MINUTES, target = 20.0)
        assertEquals("12.5 of 20 min today so far.", GoalMath.reminderLine(sit, progress(sit, 12.5)))
    }

    @Test
    fun reminderLinesAvoidShameWords() {
        val goals = listOf(
            Goal(name = "a", cadence = Cadence.DAILY, kind = TargetKind.CHECK),
            Goal(name = "b", cadence = Cadence.MONTHLY, kind = TargetKind.METRIC, target = 20.0),
            Goal(name = "c", cadence = Cadence.YEARLY, kind = TargetKind.COUNT, target = 12.0),
        )
        val banned = listOf("missed", "failed", "broke", "lost", "behind")
        for (g in goals) for (v in listOf(0.0, 1.0, 5.0)) {
            val line = GoalMath.reminderLine(g, progress(g, v)) ?: continue
            banned.forEach { assertFalse(line.lowercase().contains(it), line) }
        }
    }
}
