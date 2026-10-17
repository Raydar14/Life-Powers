package com.lifepowers.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** How often a goal resets. */
enum class Cadence { DAILY, WEEKLY, MONTHLY, YEARLY }

/** How a period's progress is counted. */
enum class TargetKind {
    /** Done or not done, one check closes the period. */
    CHECK,
    /** A count of checks, such as three walks a week. */
    COUNT,
    /** Minutes, summed from timer sessions and manual entries. */
    MINUTES,
    /** A linked metric must sit on the right side of a threshold on enough days. */
    METRIC,
}

enum class Comparison { AT_LEAST, AT_MOST }

/**
 * A goal as the engine sees it. [target] is the count or minutes for COUNT and MINUTES, the number of
 * qualifying days for METRIC (a weekly "most nights" goal is 5), and ignored for CHECK.
 */
data class GoalSpec(
    val cadence: Cadence,
    val kind: TargetKind,
    val target: Double = 1.0,
    val metric: String? = null,
    val comparison: Comparison = Comparison.AT_LEAST,
    val threshold: Double? = null,
)

/** One period of a goal: its first and last local day, inclusive. */
data class Period(val start: LocalDate, val endInclusive: LocalDate) {
    val key: String get() = start.toString()
    fun contains(date: LocalDate) = !date.isBefore(start) && !date.isAfter(endInclusive)
    val days: Int get() = (endInclusive.toEpochDay() - start.toEpochDay() + 1).toInt()
}

/**
 * How a period went, in the forecast colors: met is hot pink, mostly is light pink, started is
 * lavender, untouched is pale. There is no missed or failed state.
 */
enum class PeriodState(val tier: Tier) {
    MET(Tier.BEST),
    MOSTLY(Tier.GOOD),
    STARTED(Tier.NOT_GREAT),
    UNTOUCHED(Tier.UNKNOWN),
}

data class PeriodProgress(val period: Period, val value: Double, val target: Double, val state: PeriodState) {
    val fraction: Double get() = if (target <= 0) 0.0 else (value / target).coerceIn(0.0, 1.0)
}

object Goals {
    val WEEK_START: DayOfWeek = DayOfWeek.MONDAY
    const val MOSTLY_AT = 0.66

    fun periodOf(date: LocalDate, cadence: Cadence): Period = when (cadence) {
        Cadence.DAILY -> Period(date, date)
        Cadence.WEEKLY -> {
            val s = date.with(TemporalAdjusters.previousOrSame(WEEK_START))
            Period(s, s.plusDays(6))
        }
        Cadence.MONTHLY -> Period(date.withDayOfMonth(1), date.with(TemporalAdjusters.lastDayOfMonth()))
        Cadence.YEARLY -> Period(date.withDayOfYear(1), date.with(TemporalAdjusters.lastDayOfYear()))
    }

    fun previous(period: Period, cadence: Cadence): Period = periodOf(period.start.minusDays(1), cadence)

    /** The last [count] periods ending with the one that holds [today], oldest first. */
    fun lastPeriods(today: LocalDate, cadence: Cadence, count: Int): List<Period> {
        val out = ArrayList<Period>(count)
        var p = periodOf(today, cadence)
        repeat(count) { out.add(p); p = previous(p, cadence) }
        return out.reversed()
    }

    fun target(spec: GoalSpec): Double = if (spec.kind == TargetKind.CHECK) 1.0 else spec.target.coerceAtLeast(1.0)

    fun state(value: Double, target: Double): PeriodState {
        if (value <= 0.0) return PeriodState.UNTOUCHED
        val f = if (target <= 0) 1.0 else value / target
        return when {
            f >= 1.0 -> PeriodState.MET
            f >= MOSTLY_AT -> PeriodState.MOSTLY
            else -> PeriodState.STARTED
        }
    }

    fun qualifies(spec: GoalSpec, value: Double): Boolean {
        val t = spec.threshold ?: return false
        return when (spec.comparison) {
            Comparison.AT_LEAST -> value >= t
            Comparison.AT_MOST -> value <= t
        }
    }

    /**
     * Progress for one period.
     * [checks] are the habit checks inside the period: one entry per check, holding minutes for
     * MINUTES goals and 1.0 otherwise. [metricDays] are the daily values of the linked metric.
     */
    fun progress(spec: GoalSpec, period: Period, checks: List<Pair<LocalDate, Double>>, metricDays: Map<LocalDate, Double> = emptyMap()): PeriodProgress {
        val inPeriod = checks.filter { period.contains(it.first) }
        val value = when (spec.kind) {
            TargetKind.CHECK -> if (inPeriod.isNotEmpty()) 1.0 else 0.0
            TargetKind.COUNT -> inPeriod.size.toDouble()
            TargetKind.MINUTES -> inPeriod.sumOf { it.second }
            TargetKind.METRIC -> {
                val fromMetric = metricDays.filterKeys { period.contains(it) }.count { qualifies(spec, it.value) }
                val manualDays = inPeriod.map { it.first }.toSet().count { it !in metricDays.keys }
                (fromMetric + manualDays).toDouble()
            }
        }
        val target = target(spec)
        return PeriodProgress(period, value, target, state(value, target))
    }

    /** Progress for each of the last [count] periods, oldest first. */
    fun history(spec: GoalSpec, today: LocalDate, count: Int, checks: List<Pair<LocalDate, Double>>, metricDays: Map<LocalDate, Double> = emptyMap()): List<PeriodProgress> =
        lastPeriods(today, spec.cadence, count).map { progress(spec, it, checks, metricDays) }

    /**
     * The current run: consecutive met periods ending with the latest one. The open period counts
     * when it is already met and is skipped (not breaking the run) while it is still in progress.
     */
    fun currentRun(history: List<PeriodProgress>, today: LocalDate): Int {
        var run = 0
        for ((i, p) in history.withIndex().reversed()) {
            val open = i == history.lastIndex && p.period.contains(today)
            if (p.state == PeriodState.MET) run++
            else if (open) continue
            else break
        }
        return run
    }

    /** The longest run of met periods anywhere in [history]. */
    fun bestRun(history: List<PeriodProgress>): Int {
        var best = 0; var cur = 0
        for (p in history) { if (p.state == PeriodState.MET) { cur++; best = maxOf(best, cur) } else cur = 0 }
        return best
    }

    /**
     * The four week dot grid for a habit: 28 days ending today, oldest first, each day true when it
     * holds at least one check or (for METRIC goals) a qualifying metric value.
     */
    fun dotGrid(spec: GoalSpec, today: LocalDate, checks: List<Pair<LocalDate, Double>>, metricDays: Map<LocalDate, Double> = emptyMap(), days: Int = 28): List<Pair<LocalDate, Boolean>> {
        val checked = checks.map { it.first }.toSet()
        return (days - 1 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            val metricOk = metricDays[d]?.let { spec.kind == TargetKind.METRIC && qualifies(spec, it) } ?: false
            d to (d in checked || metricOk)
        }
    }

    /**
     * The gentle period closing line, or null when there is nothing to say. Fires on the last day of a
     * weekly, monthly, or yearly period that is not yet met: "The week is ending, 2 of 3 done."
     */
    fun closingLine(spec: GoalSpec, progress: PeriodProgress, today: LocalDate): String? {
        if (spec.cadence == Cadence.DAILY) return null
        if (today != progress.period.endInclusive || progress.state == PeriodState.MET) return null
        val unit = when (spec.cadence) { Cadence.WEEKLY -> "week"; Cadence.MONTHLY -> "month"; Cadence.YEARLY -> "year"; Cadence.DAILY -> "day" }
        val done = formatAmount(progress.value, spec.kind)
        val of = formatAmount(progress.target, spec.kind)
        return "The $unit is ending, $done of $of done."
    }

    private fun formatAmount(v: Double, kind: TargetKind): String {
        val n = if (v == Math.floor(v)) v.toLong().toString() else String.format("%.1f", v)
        return if (kind == TargetKind.MINUTES) "$n min" else n
    }
}
