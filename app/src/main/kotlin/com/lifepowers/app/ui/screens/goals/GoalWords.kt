package com.lifepowers.app.ui.screens.goals

import com.lifepowers.app.domain.AutoSources
import com.lifepowers.app.domain.Goal
import com.lifepowers.app.domain.GoalView
import com.lifepowers.app.domain.HabitCheck
import com.lifepowers.app.ui.components.Fmt
import com.lifepowers.core.Cadence
import com.lifepowers.core.Comparison
import com.lifepowers.core.DayRule
import com.lifepowers.core.Metrics
import com.lifepowers.core.PeriodState
import com.lifepowers.core.TargetKind
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** One measurement a goal can watch, with friendly words and stepper bounds. */
internal data class MetricChoice(
    val key: String,
    val label: String,
    /** Unit written after a threshold, empty when the label already says it ("8,000" steps). */
    val unit: String,
    /** Unit written under the stepper numeral. */
    val unitWord: String,
    val default: Double,
    val step: Double,
    val min: Double,
    val max: Double,
    val comparison: Comparison,
)

/** Goals as words: targets, runs, and the current period. */
internal object GoalWords {

    val metricChoices: List<MetricChoice> = listOf(
        MetricChoice(Metrics.ACTIVITY_STEPS, "steps", "", "steps", 8000.0, 500.0, 500.0, 50000.0, Comparison.AT_LEAST),
        MetricChoice(Metrics.SLEEP_DEEP_MIN, "deep sleep", "min", "minutes", 60.0, 5.0, 5.0, 240.0, Comparison.AT_LEAST),
        MetricChoice(Metrics.SLEEP_TOTAL_MIN, "total sleep", "min", "minutes", 420.0, 15.0, 60.0, 720.0, Comparison.AT_LEAST),
        MetricChoice(Metrics.HR_RESTING, "resting heart rate", "bpm", "beats a minute", 58.0, 1.0, 30.0, 120.0, Comparison.AT_MOST),
        MetricChoice(Metrics.SCREEN_TOTAL_MIN, "phone time", "min", "minutes", 120.0, 15.0, 15.0, 720.0, Comparison.AT_MOST),
        MetricChoice(Metrics.HRV_NIGHT_AVG, "HRV", "ms", "ms", 40.0, 1.0, 5.0, 200.0, Comparison.AT_LEAST),
    )

    fun choice(key: String?): MetricChoice? = metricChoices.firstOrNull { it.key == key }

    fun metricLabel(key: String): String = choice(key)?.label ?: Fmt.metricName(key)

    /** Sleep, HRV and resting heart rate belong to the night that ends on the day. */
    fun nights(key: String?): Boolean = key != null && Metrics.spec(key).dayRule == DayRule.NIGHT_ENDING

    /** Whole numbers with grouping ("8,000"), otherwise one decimal. */
    fun amount(v: Double): String =
        if (v == floor(v)) String.format(Locale.getDefault(), "%,d", v.roundToLong()) else Fmt.num(v, 1)

    fun thresholdText(key: String, value: Double): String {
        val unit = choice(key)?.unit ?: Metrics.spec(key).unit.takeIf { it.isNotBlank() && it != "count" }.orEmpty()
        return if (unit.isEmpty()) amount(value) else "${amount(value)} $unit"
    }

    fun metricPhrase(key: String?, comparison: Comparison, threshold: Double?): String? {
        if (key == null || threshold == null) return null
        val word = if (comparison == Comparison.AT_LEAST) "over" else "under"
        return "${metricLabel(key)} $word ${thresholdText(key, threshold)}"
    }

    fun per(c: Cadence): String = when (c) {
        Cadence.DAILY -> "a day"
        Cadence.WEEKLY -> "a week"
        Cadence.MONTHLY -> "a month"
        Cadence.YEARLY -> "a year"
    }

    fun unit(c: Cadence): String = when (c) {
        Cadence.DAILY -> "day"
        Cadence.WEEKLY -> "week"
        Cadence.MONTHLY -> "month"
        Cadence.YEARLY -> "year"
    }

    fun thisPeriod(c: Cadence): String = when (c) {
        Cadence.DAILY -> "today"
        Cadence.WEEKLY -> "this week"
        Cadence.MONTHLY -> "this month"
        Cadence.YEARLY -> "this year"
    }

    fun each(c: Cadence): String = "Each ${unit(c)}"

    fun times(n: Int): String = when (n) {
        1 -> "once"
        2 -> "twice"
        else -> "$n times"
    }

    fun plural(word: String, n: Int): String = if (n == 1) word else "${word}s"

    fun dayWord(metric: String?): String = if (nights(metric)) "night" else "day"

    fun targetWords(goal: Goal): String =
        targetWords(goal.cadence, goal.kind, goal.target, goal.metric, goal.comparison, goal.threshold)

    /** "3 times a week", "10 minutes a day", "Deep sleep over 60 min, 5 nights a week". */
    fun targetWords(cadence: Cadence, kind: TargetKind, target: Double, metric: String?, comparison: Comparison, threshold: Double?): String {
        val n = target.roundToInt().coerceAtLeast(1)
        val phrase = metricPhrase(metric, comparison, threshold)
        val text = when (kind) {
            TargetKind.CHECK -> {
                val often = when (cadence) {
                    Cadence.DAILY -> "every day"
                    Cadence.WEEKLY -> "once a week"
                    Cadence.MONTHLY -> "once a month"
                    Cadence.YEARLY -> "once a year"
                }
                if (phrase != null) "$phrase, $often" else often
            }
            TargetKind.COUNT -> "${times(n)} ${per(cadence)}"
            TargetKind.MINUTES -> "$n ${plural("minute", n)} ${per(cadence)}"
            TargetKind.METRIC -> {
                val day = dayWord(metric)
                val what = phrase ?: "a measurement"
                if (cadence == Cadence.DAILY) "$what, every $day" else "$what, $n ${plural(day, n)} ${per(cadence)}"
            }
        }
        return text.replaceFirstChar { it.uppercase() }
    }

    fun autoWords(goal: Goal): String? = when (goal.autoSource) {
        AutoSources.METRIC -> "Confirms itself from your data"
        AutoSources.PHONE_DOWN -> "Confirms itself from phone usage"
        AutoSources.JOURNAL -> "Confirms itself from the journal"
        else -> null
    }

    fun reminderWords(goal: Goal): String? {
        if (goal.reminderTimes.isEmpty()) return null
        val times = goal.reminderTimes.sorted().map { Fmt.timeOfDay(it) }
        val head = if (times.size == 1) "Reminder ${times[0]}" else "Reminders ${Fmt.joinSentence(times)}"
        val days = goal.reminderDays
        if (days.isEmpty() || days.size == 7) return head
        val names = DayOfWeek.values().filter { it in days }.map { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
        return "$head on ${Fmt.joinSentence(names)}"
    }

    /** The muted line under a goal's name. */
    fun subline(goal: Goal): String =
        listOfNotNull(targetWords(goal), autoWords(goal), reminderWords(goal)).joinToString(separator = ". ", postfix = ".")

    /** Where the current period stands: "2 of 3 this week", "Done today". */
    fun nowLine(view: GoalView): String {
        val g = view.goal
        val p = view.current
        val period = thisPeriod(g.cadence)
        return when (g.kind) {
            TargetKind.CHECK -> if (p.state == PeriodState.MET) "Done $period" else "Not yet $period"
            TargetKind.COUNT -> "${amount(p.value)} of ${amount(p.target)} $period"
            TargetKind.MINUTES -> "${amount(p.value)} of ${amount(p.target)} min $period"
            TargetKind.METRIC -> if (g.cadence == Cadence.DAILY) {
                if (p.state == PeriodState.MET) "Met $period" else "Not yet $period"
            } else {
                "${amount(p.value)} of ${amount(p.target)} ${dayWord(g.metric)}s $period"
            }
        }
    }

    /** "4 weeks running", or null before the first met period. */
    fun runWords(cadence: Cadence, run: Int): String? = if (run <= 0) null else "$run ${plural(unit(cadence), run)} running"

    fun statusLine(view: GoalView): String =
        listOfNotNull(nowLine(view), runWords(view.goal.cadence, view.currentRun)).joinToString(separator = ". ", postfix = ".")

    fun checkText(check: HabitCheck, kind: TargetKind): String = when {
        check.source == "timer" -> "${amount(check.amount)} min with the timer"
        kind == TargetKind.MINUTES -> "${amount(check.amount)} min"
        check.source == "auto" -> "Checked from your data"
        else -> "Checked"
    }

    fun clockOf(utcMs: Long): String =
        Fmt.timeOfDay(Instant.ofEpochMilli(utcMs).atZone(ZoneId.systemDefault()).toLocalTime())
}
