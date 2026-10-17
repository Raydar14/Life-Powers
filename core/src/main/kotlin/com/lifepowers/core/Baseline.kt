package com.lifepowers.core

import kotlin.math.abs

/** Within person baselines: robust statistics over a trailing window. */
object Baseline {
    const val MIN_WINDOW_VALUES = 10
    /** IQR to standard deviation for a normal distribution. */
    private const val IQR_TO_SD = 1.349

    data class Stats(val median: Double, val iqr: Double, val n: Int) {
        val robustSd: Double get() = if (iqr > 0) iqr / IQR_TO_SD else 0.0
    }

    fun median(values: List<Double>): Double {
        if (values.isEmpty()) return Double.NaN
        val s = values.sorted()
        val mid = s.size / 2
        return if (s.size % 2 == 0) (s[mid - 1] + s[mid]) / 2 else s[mid]
    }

    fun percentile(values: List<Double>, p: Double): Double {
        if (values.isEmpty()) return Double.NaN
        val s = values.sorted()
        val pos = p * (s.size - 1)
        val lo = pos.toInt()
        val hi = minOf(lo + 1, s.size - 1)
        val frac = pos - lo
        return s[lo] + (s[hi] - s[lo]) * frac
    }

    fun stats(values: List<Double>): Stats {
        val clean = values.filter { !it.isNaN() }
        if (clean.isEmpty()) return Stats(Double.NaN, Double.NaN, 0)
        return Stats(median(clean), percentile(clean, 0.75) - percentile(clean, 0.25), clean.size)
    }

    /**
     * Robust z score of [value] against [window]. Returns null when the window is too small
     * or has no spread.
     */
    fun z(value: Double, window: List<Double>): Double? {
        val s = stats(window)
        if (s.n < MIN_WINDOW_VALUES) return null
        val sd = s.robustSd
        if (sd <= 0.0) return if (abs(value - s.median) < 1e-9) 0.0 else null
        return (value - s.median) / sd
    }

    /**
     * For an ordered daily series, z of each day against the previous [windowDays] values.
     * Index i uses values [i - windowDays, i).
     */
    fun trailingZ(series: List<Double>, windowDays: Int = 30): List<Double?> =
        series.indices.map { i ->
            val from = maxOf(0, i - windowDays)
            if (i == 0) null else z(series[i], series.subList(from, i))
        }
}
