package com.lifepowers.app.experiments

import com.lifepowers.app.data.personal.ExperimentDayEntity
import com.lifepowers.app.data.personal.ExperimentEntity
import com.lifepowers.app.domain.ExperimentRecord
import com.lifepowers.core.Condition
import com.lifepowers.core.DayRule
import com.lifepowers.core.ExperimentSpec
import com.lifepowers.core.Experiments
import com.lifepowers.core.Metrics
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** What the hourly worker should post for one experiment, if anything. */
sealed interface ExperimentNotice {
    data class Today(val condition: Condition, val text: String) : ExperimentNotice
    data object Complete : ExperimentNotice
}

/**
 * Pure helpers for experiments: row mapping, the outcome window, day counting, notice decisions,
 * and words. No Android types, so plain JUnit tests cover them.
 */
object ExperimentMath {

    const val COMPLETE_TEXT = "Your experiment is complete. See what happened."
    const val OFF_DAY_TEXT = "Today is an off day for your experiment. Live as usual."

    /** Notices for experiments sit between goal closing notices and the sky notices. */
    fun noticeId(experimentId: Long): Int = 30_000 + (experimentId % 10_000).toInt()

    fun toRecord(e: ExperimentEntity): ExperimentRecord = ExperimentRecord(
        spec = ExperimentSpec(
            id = e.id,
            behavior = e.behavior,
            outcomeMetric = e.outcomeMetric,
            startedOn = LocalDate.parse(e.startedOn),
            blockDays = e.blockDays,
            targetDays = e.targetDays,
            outcomeLag = e.outcomeLag,
        ),
        name = e.name,
        reminderHour = e.reminderHour,
        endedOn = e.endedOn?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    )

    /** [id] 0 lets Room pick the id. */
    fun toEntity(r: ExperimentRecord, id: Long = r.spec.id): ExperimentEntity = ExperimentEntity(
        id = id,
        name = r.name,
        behavior = r.spec.behavior,
        outcomeMetric = r.spec.outcomeMetric,
        outcomeLag = r.spec.outcomeLag,
        blockDays = r.spec.blockDays,
        targetDays = r.spec.targetDays,
        startedOn = r.spec.startedOn.toString(),
        endedOn = r.endedOn?.toString(),
        reminderHour = r.reminderHour,
    )

    /** Answers given for one experiment, by date. Rows with a bad date are skipped. */
    fun adherence(days: List<ExperimentDayEntity>): Map<LocalDate, Boolean> =
        days.mapNotNull { d -> runCatching { LocalDate.parse(d.localDate) }.getOrNull()?.let { it to d.adhered } }.toMap()

    /**
     * The days of outcome to read: from the start through the last scheduled day plus the lag,
     * never past [today] or the day the experiment was ended. Returns the count of days and the
     * last day, as MetricRepository.series takes them.
     */
    fun outcomeWindow(spec: ExperimentSpec, endedOn: LocalDate?, today: LocalDate): Pair<Int, LocalDate> {
        val lastNeeded = Experiments.endsOn(spec).plusDays(spec.outcomeLag.toLong())
        var end = minOf(lastNeeded, today)
        if (endedOn != null) end = minOf(end, endedOn.plusDays(spec.outcomeLag.toLong()))
        if (end.isBefore(spec.startedOn)) end = spec.startedOn
        val span = ChronoUnit.DAYS.between(spec.startedOn, end).toInt() + 1
        return maxOf(span, spec.targetDays + 2) to end
    }

    /** The day scored up to: today, or the day it was ended. */
    fun scoreDay(endedOn: LocalDate?, today: LocalDate): LocalDate = if (endedOn != null && endedOn.isBefore(today)) endedOn else today

    /** Day 1 is the start day. Zero before the start, the target once it has passed. */
    fun dayIndex(spec: ExperimentSpec, on: LocalDate): Int {
        val d = ChronoUnit.DAYS.between(spec.startedOn, on) + 1
        return d.coerceIn(0L, spec.targetDays.toLong()).toInt()
    }

    /** True once every scheduled day is behind [today]. */
    fun pastTarget(spec: ExperimentSpec, today: LocalDate): Boolean = today.isAfter(Experiments.endsOn(spec))

    /** True once the outcome for the last scheduled day has had its day to land too. */
    fun complete(spec: ExperimentSpec, today: LocalDate): Boolean =
        today.isAfter(Experiments.endsOn(spec).plusDays(spec.outcomeLag.toLong()))

    /**
     * Outcomes measured overnight (sleep, HRV, resting heart rate, overnight glucose) belong to the
     * morning they end, so an evening behavior shows up the next day.
     */
    fun defaultLag(metric: String): Int = if (Metrics.spec(metric).dayRule == DayRule.NIGHT_ENDING) 1 else 0

    /**
     * Decides the hourly notice. A day notice goes out once, in the reminder hour or the hour
     * after it (periodic work drifts), and only on a scheduled day. The completion notice goes out
     * once, in the daytime, even when daily reminders are off, and the experiment is never ended
     * automatically.
     */
    fun noticeFor(
        record: ExperimentRecord,
        today: LocalDate,
        hour: Int,
        notifiedToday: Boolean,
        completeNotified: Boolean,
    ): ExperimentNotice? {
        if (record.endedOn != null) return null
        val spec = record.spec
        if (complete(spec, today)) {
            if (completeNotified) return null
            val from = record.reminderHour ?: 9
            return if (hour in from..21 || (from > 21 && hour >= from)) ExperimentNotice.Complete else null
        }
        val reminder = record.reminderHour ?: return null
        if (notifiedToday) return null
        if (hour != reminder && hour != reminder + 1) return null
        val condition = Experiments.condition(spec, today) ?: return null
        return ExperimentNotice.Today(condition, dayText(spec.behavior, condition))
    }

    fun dayText(behavior: String, condition: Condition): String = when (condition) {
        Condition.ON -> "Today is an on day: ${sentenceEnd(lowerFirst(behavior.trim()))}"
        Condition.OFF -> OFF_DAY_TEXT
    }

    /** Lowers the first letter unless the word is an acronym such as "HRV". */
    fun lowerFirst(s: String): String {
        if (s.isEmpty()) return s
        if (s.length > 1 && s[1].isUpperCase()) return s
        return s.substring(0, 1).lowercase(Locale.ROOT) + s.substring(1)
    }

    fun upperFirst(s: String): String =
        if (s.isEmpty()) s else s.substring(0, 1).uppercase(Locale.ROOT) + s.substring(1)

    private fun sentenceEnd(s: String): String = if (s.endsWith(".") || s.endsWith("!") || s.endsWith("?")) s else "$s."

    /** The spec with a behavior that reads well mid sentence, for Experiments.sentence. */
    fun forSentence(spec: ExperimentSpec): ExperimentSpec =
        spec.copy(behavior = lowerFirst(spec.behavior.trim().trimEnd('.')))

    /** A value in the outcome's own units: "71 min", "42 ms", "96 mg/dL", "+0.4". */
    fun formatValue(metric: String, value: Double?, locale: Locale = Locale.getDefault()): String {
        if (value == null || value.isNaN()) return "--"
        val unit = Metrics.spec(metric).unit
        fun whole(v: Double) = v.roundToInt().toString()
        fun dec(v: Double, n: Int) = String.format(locale, "%.${n}f", v)
        fun signed(v: Double, n: Int) = (if (v > 0) "+" else "") + dec(v, n)
        return when (unit) {
            "min" -> if (abs(value) >= 10) "${whole(value)} min" else "${dec(value, 1)} min"
            "ms" -> "${whole(value)} ms"
            "bpm" -> "${dec(value, 1)} bpm"
            "mg/dL" -> "${whole(value)} mg/dL"
            "score", "count" -> whole(value)
            "pct" -> "${dec(value, 1)} percent"
            "ratio" -> "${whole(value * 100)} percent"
            "z", "-1..1" -> signed(value, 2)
            "0..1", "0..10" -> dec(value, 1)
            "" -> dec(value, 1)
            else -> "${dec(value, 1)} $unit"
        }
    }

    /** The first [days] of the schedule, for the preview dots. */
    fun preview(spec: ExperimentSpec, days: Int = 14): List<Condition> = Experiments.schedule(spec).take(days).map { it.second }
}
