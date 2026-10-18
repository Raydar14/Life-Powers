package com.lifepowers.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

enum class Condition { ON, OFF }

/**
 * A self experiment: a behavior to do on ON days and skip on OFF days, and an outcome to watch.
 * [outcomeLag] is 1 when the outcome lands the next day (sleep after an evening behavior).
 */
data class ExperimentSpec(
    val id: Long,
    val behavior: String,
    val outcomeMetric: String,
    val startedOn: LocalDate,
    val blockDays: Int = 3,
    val targetDays: Int = 28,
    val outcomeLag: Int = 0,
)

/** One finished or in progress result, in plain numbers. */
data class ExperimentResult(
    val nOn: Int,
    val nOff: Int,
    val meanOn: Double?,
    val meanOff: Double?,
    /** Difference in the outcome's own spread, ON minus OFF. */
    val effect: Double?,
    val pValue: Double?,
    val ready: Boolean,
    /** True when the difference is good for the person, false when not, null when no direction or no result. */
    val good: Boolean?,
)

object Experiments {
    const val MIN_PER_ARM = 7

    /**
     * Blocks come in pairs and each pair is ON first or OFF first by a coin seeded with the
     * experiment id, so a slow drift over the month does not line up with one condition.
     */
    fun condition(spec: ExperimentSpec, date: LocalDate): Condition? {
        val day = ChronoUnit.DAYS.between(spec.startedOn, date)
        if (day < 0 || day >= spec.targetDays) return null
        val block = (day / spec.blockDays.coerceAtLeast(1)).toInt()
        val pair = block / 2
        val onFirst = Random(spec.id * 7919 + pair).nextBoolean()
        val firstInPair = block % 2 == 0
        return if (firstInPair == onFirst) Condition.ON else Condition.OFF
    }

    fun schedule(spec: ExperimentSpec): List<Pair<LocalDate, Condition>> =
        (0 until spec.targetDays).map { spec.startedOn.plusDays(it.toLong()) }.mapNotNull { d -> condition(spec, d)?.let { d to it } }

    fun endsOn(spec: ExperimentSpec): LocalDate = spec.startedOn.plusDays(spec.targetDays - 1L)

    /**
     * Scores the experiment. Days the person marked as not followed are left out; days with no answer
     * count as followed. [outcome] holds the daily outcome values.
     */
    fun score(spec: ExperimentSpec, adhered: Map<LocalDate, Boolean>, outcome: Map<LocalDate, Double>, today: LocalDate): ExperimentResult {
        val on = mutableListOf<Double>()
        val off = mutableListOf<Double>()
        for ((date, cond) in schedule(spec)) {
            if (date.isAfter(today)) break
            if (adhered[date] == false) continue
            val v = outcome[date.plusDays(spec.outcomeLag.toLong())] ?: continue
            if (cond == Condition.ON) on.add(v) else off.add(v)
        }
        val ready = on.size >= MIN_PER_ARM && off.size >= MIN_PER_ARM
        if (on.size < 2 || off.size < 2) return ExperimentResult(on.size, off.size, on.averageOrNull(), off.averageOrNull(), null, null, false, null)
        val all = on + off
        val sd = Baseline.stats(all).robustSd.takeIf { it > 0 } ?: stdev(all)
        val m1 = on.average(); val m2 = off.average()
        val v1 = variance(on); val v2 = variance(off)
        val se = sqrt(v1 / on.size + v2 / off.size)
        val p = if (se <= 0) null else {
            val df = (v1 / on.size + v2 / off.size).let { s -> s * s / ((v1 / on.size).let { it * it } / (on.size - 1) + (v2 / off.size).let { it * it } / (off.size - 1)) }
            Patterns.pFromT((m1 - m2) / se, df)
        }
        val effect = if (sd > 0) (m1 - m2) / sd else null
        val good = effect?.let { e -> Metrics.spec(spec.outcomeMetric).higherIsBetter?.let { if (it) e > 0 else e < 0 } }
        return ExperimentResult(on.size, off.size, m1, m2, effect, p, ready, good)
    }

    /** A plain sentence for the result. [label] turns the outcome key into words. */
    fun sentence(spec: ExperimentSpec, r: ExperimentResult, label: (String) -> String): String {
        val outcome = label(spec.outcomeMetric)
        if (r.effect == null || r.meanOn == null || r.meanOff == null)
            return "Not enough days yet. ${r.nOn} on days and ${r.nOff} off days so far; about $MIN_PER_ARM of each are needed."
        val dir = if (r.effect >= 0) "higher" else "lower"
        val size = when { abs(r.effect) >= 0.8 -> "clearly"; abs(r.effect) >= 0.4 -> "somewhat"; else -> "barely" }
        val confidence = when {
            !r.ready -> "Early read, keep going."
            (r.pValue ?: 1.0) < 0.05 -> "That difference is unlikely to be chance."
            else -> "That could still be chance."
        }
        return "Your $outcome was $size $dir on days with ${spec.behavior} (${r.nOn} on days, ${r.nOff} off days). $confidence"
    }

    private fun List<Double>.averageOrNull() = if (isEmpty()) null else average()
    private fun variance(v: List<Double>): Double { val m = v.average(); return v.sumOf { (it - m) * (it - m) } / (v.size - 1).coerceAtLeast(1) }
    private fun stdev(v: List<Double>) = sqrt(variance(v))
}
