package com.lifepowers.core

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * A daily series for the patterns engine. Binary series hold 1.0 on days a behavior or symptom
 * happened and 0.0 on covered days it did not; days outside coverage are simply absent.
 */
data class DaySeries(val key: String, val values: Map<LocalDate, Double>, val binary: Boolean = false, val higherIsBetter: Boolean? = null)

enum class InsightKind { TRENDS_WITH, AFTER_BEHAVIOR }

data class Insight(
    val kind: InsightKind,
    val input: String,
    val outcome: String,
    /** Outcome day minus input day. 1 means the input on one day and the outcome the next. */
    val lag: Int,
    val n: Int,
    /** Spearman rho for TRENDS_WITH, difference in outcome spreads (with minus without) for AFTER_BEHAVIOR. */
    val effect: Double,
    val pValue: Double,
    val qValue: Double,
    val preliminary: Boolean,
    /** True when the association is good for the person, false when not, null when the outcome has no good direction. */
    val good: Boolean?,
    val nWith: Int = 0,
    val nWithout: Int = 0,
) {
    val direction: Int get() = if (effect >= 0) 1 else -1
    val strength: String get() = when (kind) {
        InsightKind.TRENDS_WITH -> when { abs(effect) >= 0.6 -> "strong"; abs(effect) >= 0.45 -> "clear"; else -> "gentle" }
        InsightKind.AFTER_BEHAVIOR -> when { abs(effect) >= 0.9 -> "strong"; abs(effect) >= 0.6 -> "clear"; else -> "gentle" }
    }
}

/**
 * Within person correlations with lags, held to sample size rules and a false discovery rate so a
 * month of noise does not turn into a feed of findings.
 */
object Patterns {
    const val MIN_N = 14
    const val FULL_N = 30
    const val MIN_RHO = 0.3
    const val MIN_SPREAD = 0.4
    const val MIN_GROUP = 5
    const val FDR = 0.10

    fun ranks(v: List<Double>): DoubleArray {
        val idx = v.indices.sortedBy { v[it] }
        val r = DoubleArray(v.size)
        var i = 0
        while (i < idx.size) {
            var j = i
            while (j + 1 < idx.size && v[idx[j + 1]] == v[idx[i]]) j++
            val avg = (i + j) / 2.0 + 1
            for (k in i..j) r[idx[k]] = avg
            i = j + 1
        }
        return r
    }

    fun pearson(x: DoubleArray, y: DoubleArray): Double {
        val n = x.size
        if (n < 3) return 0.0
        val mx = x.average(); val my = y.average()
        var sxy = 0.0; var sxx = 0.0; var syy = 0.0
        for (i in 0 until n) { val dx = x[i] - mx; val dy = y[i] - my; sxy += dx * dy; sxx += dx * dx; syy += dy * dy }
        return if (sxx <= 0 || syy <= 0) 0.0 else sxy / sqrt(sxx * syy)
    }

    fun spearman(x: List<Double>, y: List<Double>): Double = pearson(ranks(x), ranks(y))

    private fun lnGamma(x: Double): Double {
        val c = doubleArrayOf(76.18009172947146, -86.50532032941677, 24.01409824083091, -1.231739572450155, 0.1208650973866179e-2, -0.5395239384953e-5)
        var y = x
        val tmp = x + 5.5 - (x + 0.5) * ln(x + 5.5)
        var ser = 1.000000000190015
        for (cj in c) { y += 1; ser += cj / y }
        return -tmp + ln(2.5066282746310005 * ser / x)
    }

    private fun betacf(a: Double, b: Double, x: Double): Double {
        val fpmin = 1e-300
        var c = 1.0
        var d = 1.0 - (a + b) * x / (a + 1)
        if (abs(d) < fpmin) d = fpmin
        d = 1 / d
        var h = d
        for (m in 1..200) {
            val m2 = 2 * m
            var aa = m * (b - m) * x / ((a - 1 + m2) * (a + m2))
            d = 1 + aa * d; if (abs(d) < fpmin) d = fpmin
            c = 1 + aa / c; if (abs(c) < fpmin) c = fpmin
            d = 1 / d; h *= d * c
            aa = -(a + m) * (a + b + m) * x / ((a + m2) * (a + 1 + m2))
            d = 1 + aa * d; if (abs(d) < fpmin) d = fpmin
            c = 1 + aa / c; if (abs(c) < fpmin) c = fpmin
            d = 1 / d
            val del = d * c
            h *= del
            if (abs(del - 1) < 3e-12) break
        }
        return h
    }

    /** Regularized incomplete beta function I_x(a, b). */
    fun incompleteBeta(a: Double, b: Double, x: Double): Double {
        if (x <= 0) return 0.0
        if (x >= 1) return 1.0
        val bt = exp(lnGamma(a + b) - lnGamma(a) - lnGamma(b) + a * ln(x) + b * ln(1 - x))
        return if (x < (a + 1) / (a + b + 2)) bt * betacf(a, b, x) / a else 1 - bt * betacf(b, a, 1 - x) / b
    }

    /** Two sided p value for Student's t with [df] degrees of freedom. */
    fun pFromT(t: Double, df: Double): Double = if (df <= 0) 1.0 else incompleteBeta(df / 2, 0.5, df / (df + t * t))

    fun pFromRho(rho: Double, n: Int): Double {
        if (n < 4) return 1.0
        val r = rho.coerceIn(-0.999999, 0.999999)
        return pFromT(r * sqrt((n - 2) / (1 - r * r)), (n - 2).toDouble())
    }

    /** Benjamini Hochberg adjusted q values, in the input order. */
    fun bh(p: List<Double>): List<Double> {
        val m = p.size
        if (m == 0) return emptyList()
        val order = p.indices.sortedBy { p[it] }
        val q = DoubleArray(m)
        var running = 1.0
        for (k in m - 1 downTo 0) {
            val i = order[k]
            running = minOf(running, p[i] * m / (k + 1))
            q[i] = running
        }
        return q.toList()
    }

    private fun family(key: String) = key.substringBefore('.')

    private fun pairUp(input: DaySeries, outcome: DaySeries, lag: Int): List<Pair<Double, Double>> =
        input.values.mapNotNull { (d, x) -> outcome.values[d.plusDays(lag.toLong())]?.let { x to it } }

    private fun goodFor(outcome: DaySeries, direction: Int): Boolean? = outcome.higherIsBetter?.let { if (it) direction > 0 else direction < 0 }

    private fun test(input: DaySeries, outcome: DaySeries, lag: Int): Insight? {
        val pairs = pairUp(input, outcome, lag)
        if (pairs.size < MIN_N) return null
        if (input.binary) {
            val with = pairs.filter { it.first > 0.5 }.map { it.second }
            val without = pairs.filter { it.first <= 0.5 }.map { it.second }
            if (with.size < MIN_GROUP || without.size < MIN_GROUP) return null
            val all = pairs.map { it.second }
            val sd = Baseline.stats(all).robustSd.takeIf { it > 0 } ?: stdev(all)
            if (sd <= 0) return null
            val m1 = with.average(); val m2 = without.average()
            val v1 = variance(with); val v2 = variance(without)
            val se = sqrt(v1 / with.size + v2 / without.size)
            if (se <= 0) return null
            val t = (m1 - m2) / se
            val df = (v1 / with.size + v2 / without.size).let { s -> s * s / ((v1 / with.size).let { it * it } / (with.size - 1) + (v2 / without.size).let { it * it } / (without.size - 1)) }
            val effect = (m1 - m2) / sd
            return Insight(InsightKind.AFTER_BEHAVIOR, input.key, outcome.key, lag, pairs.size, effect, pFromT(t, df), 1.0, pairs.size < FULL_N, goodFor(outcome, if (effect >= 0) 1 else -1), with.size, without.size)
        }
        val rho = spearman(pairs.map { it.first }, pairs.map { it.second })
        if (rho.isNaN()) return null
        return Insight(InsightKind.TRENDS_WITH, input.key, outcome.key, lag, pairs.size, rho, pFromRho(rho, pairs.size), 1.0, pairs.size < FULL_N, goodFor(outcome, if (rho >= 0) 1 else -1))
    }

    private fun variance(v: List<Double>): Double { val m = v.average(); return v.sumOf { (it - m) * (it - m) } / (v.size - 1).coerceAtLeast(1) }
    private fun stdev(v: List<Double>) = sqrt(variance(v))

    /**
     * Tests every input against every outcome at each lag, keeps the stronger lag per pair, corrects for
     * the number of tests, and returns findings that clear the size and strength rules, strongest first.
     */
    fun run(inputs: List<DaySeries>, outcomes: List<DaySeries>, lags: List<Int> = listOf(0, 1)): List<Insight> {
        val tested = mutableListOf<Insight>()
        for (o in outcomes) for (i in inputs) {
            if (i.key == o.key || family(i.key) == family(o.key)) continue
            lags.mapNotNull { test(i, o, it) }.minByOrNull { it.pValue }?.let { tested.add(it) }
        }
        val q = bh(tested.map { it.pValue * lags.size.coerceAtLeast(1) }.map { it.coerceAtMost(1.0) })
        return tested.mapIndexed { k, ins -> ins.copy(qValue = q[k]) }
            .filter { it.qValue <= FDR }
            .filter { if (it.kind == InsightKind.TRENDS_WITH) abs(it.effect) >= MIN_RHO else abs(it.effect) >= MIN_SPREAD }
            .sortedWith(compareBy<Insight> { it.preliminary }.thenByDescending { abs(it.effect) })
    }

    /**
     * A plain sentence for a finding. [label] turns a key into words ("deep sleep", "alcohol").
     */
    fun sentence(ins: Insight, label: (String) -> String): String {
        val input = label(ins.input)
        val outcome = label(ins.outcome)
        val higherLower = if (ins.direction > 0) "higher" else "lower"
        return when (ins.kind) {
            InsightKind.AFTER_BEHAVIOR -> {
                val whenText = if (ins.lag == 0) "on days with $input" else "the day after $input"
                "Your $outcome tends to run $higherLower $whenText (${ins.nWith} days with, ${ins.nWithout} without)."
            }
            InsightKind.TRENDS_WITH -> {
                val whenText = if (ins.lag == 0) "on days with more $input" else "the day after more $input"
                "Your $outcome tends to be $higherLower $whenText (${ins.n} days)."
            }
        }
    }

    /** Inputs and outcomes from DESIGN.md section 6 that the engine tests by default. */
    val DEFAULT_OUTCOMES = listOf(
        Metrics.HRV_NIGHT_AVG, Metrics.HR_RESTING, Metrics.SLEEP_DEEP_MIN, Metrics.SLEEP_REM_MIN, Metrics.SLEEP_TOTAL_MIN,
        Metrics.SLEEP_ONSET_LATENCY_MIN, Metrics.SCORE_READINESS, Metrics.GLUCOSE_OVERNIGHT_MEAN, Metrics.GLUCOSE_FASTING,
        Metrics.BP_SYSTOLIC, Metrics.VOICE_ENERGY_INDEX, Metrics.VOICE_FLATNESS_INDEX, Metrics.VOICE_TENSION_INDEX,
        Metrics.WORDS_VALENCE, Metrics.FEEL_ENERGY, Metrics.FEEL_EASE,
    )

    val DEFAULT_INPUTS = listOf(
        Metrics.SCREEN_TOTAL_MIN, Metrics.SCREEN_AFTER_21_MIN, Metrics.ACTIVITY_STEPS, Metrics.ACTIVITY_ACTIVE_KCAL,
        Metrics.SLEEP_TOTAL_MIN, Metrics.HRV_NIGHT_AVG, Metrics.STRESS_DAY_HIGH_MIN, Metrics.WEATHER_PRESSURE_SWING,
        Metrics.WEATHER_DAYLIGHT_MIN, Metrics.AIR_PM25, Metrics.MOON_ILLUMINATION, Metrics.SPACE_KP_MAX, Metrics.HABIT_MINUTES, Metrics.BREATH_MINUTES,
    )
}
