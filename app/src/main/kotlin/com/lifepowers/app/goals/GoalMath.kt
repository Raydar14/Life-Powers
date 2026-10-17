package com.lifepowers.app.goals

import com.lifepowers.app.domain.Goal
import com.lifepowers.core.Cadence
import com.lifepowers.core.GoalSpec
import com.lifepowers.core.Goals
import com.lifepowers.core.PeriodProgress
import com.lifepowers.core.PeriodState
import com.lifepowers.core.TargetKind
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale
import kotlin.math.roundToLong

/** Pure helpers shared by the goal repository, the timer, and the reminders. */
object GoalMath {
    /** Days in the habit dot grid. */
    const val GRID_DAYS = 28

    /** Timer sessions shorter than this are dropped, like a cancel. */
    const val MIN_SESSION_MIN = 0.5

    fun spec(goal: Goal) = GoalSpec(
        cadence = goal.cadence,
        kind = goal.kind,
        target = goal.target,
        metric = goal.metric,
        comparison = goal.comparison,
        threshold = goal.threshold,
    )

    /** How many periods the history strip shows for a cadence. */
    fun historyCount(cadence: Cadence): Int = when (cadence) {
        Cadence.DAILY -> 12
        Cadence.WEEKLY -> 8
        Cadence.MONTHLY -> 6
        Cadence.YEARLY -> 3
    }

    /** The first local day a goal's view needs: the start of its oldest history period or of the dot grid. */
    fun historyStart(cadence: Cadence, today: LocalDate): LocalDate {
        val oldest = Goals.lastPeriods(today, cadence, historyCount(cadence)).first().start
        val gridStart = today.minusDays((GRID_DAYS - 1).toLong())
        return if (oldest.isBefore(gridStart)) oldest else gridStart
    }

    /** Days from [from] to [today], inclusive. */
    fun daysBetween(from: LocalDate, today: LocalDate): Int = (today.toEpochDay() - from.toEpochDay() + 1).toInt().coerceAtLeast(1)

    /** Elapsed minutes rounded to one decimal. */
    fun minutes(startUtcMs: Long, endUtcMs: Long): Double = round1((endUtcMs - startUtcMs).coerceAtLeast(0) / 60_000.0)

    fun round1(v: Double): Double = (v * 10).roundToLong() / 10.0

    fun formatAmount(v: Double): String =
        if (v == Math.floor(v)) v.toLong().toString() else String.format(Locale.ROOT, "%.1f", v)

    /**
     * The next local time a reminder is due strictly after [now], honoring [days] (empty means every
     * day). Null when there are no times.
     */
    fun nextReminder(times: List<LocalTime>, days: Set<DayOfWeek>, now: LocalDateTime): LocalDateTime? {
        if (times.isEmpty()) return null
        val sorted = times.sorted()
        for (ahead in 0..7L) {
            val date = now.toLocalDate().plusDays(ahead)
            if (days.isNotEmpty() && date.dayOfWeek !in days) continue
            for (t in sorted) {
                val at = date.atTime(t)
                if (at.isAfter(now)) return at
            }
        }
        return null
    }

    /** The next [at] local time strictly after [now]. */
    fun nextDaily(at: LocalTime, now: LocalDateTime): LocalDateTime {
        val today = now.toLocalDate().atTime(at)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    /**
     * A gentle reminder line from the current period's progress: "Not yet today. Whenever you are
     * ready." or "2 of 3 this week so far." Null when the period is already met, so nothing is posted.
     */
    fun reminderLine(goal: Goal, current: PeriodProgress): String? {
        if (current.state == PeriodState.MET) return null
        val period = when (goal.cadence) {
            Cadence.DAILY -> "today"
            Cadence.WEEKLY -> "this week"
            Cadence.MONTHLY -> "this month"
            Cadence.YEARLY -> "this year"
        }
        if (current.value <= 0.0) return "Not yet $period. Whenever you are ready."
        val unit = when (goal.kind) {
            TargetKind.MINUTES -> " min"
            TargetKind.METRIC -> if (current.target == 1.0) " day" else " days"
            else -> ""
        }
        return "${formatAmount(current.value)} of ${formatAmount(current.target)}$unit $period so far."
    }
}
