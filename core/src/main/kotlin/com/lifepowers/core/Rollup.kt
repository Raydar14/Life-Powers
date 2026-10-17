package com.lifepowers.core

import kotlin.math.sqrt

/** Turns canonical samples into daily aggregates according to the metric catalog. */
object Rollup {
    fun daily(samples: List<Sample>): List<DailyValue> {
        val out = ArrayList<DailyValue>()
        samples.groupBy { it.metric }.forEach { (metric, forMetric) ->
            val spec = Metrics.spec(metric)
            forMetric.groupBy { Days.localDay(it, spec.dayRule) }.forEach { (day, forDay) ->
                val sorted = forDay.sortedBy { it.startUtcMs }
                val values = sorted.map { it.value }
                spec.aggs.forEach { agg ->
                    aggregate(agg, values)?.let { out += DailyValue(day, metric, agg, it, values.size) }
                }
            }
        }
        return out.sortedWith(compareBy({ it.localDate }, { it.metric }, { it.agg }))
    }

    fun aggregate(agg: Agg, values: List<Double>): Double? {
        if (values.isEmpty()) return null
        return when (agg) {
            Agg.MEAN -> values.average()
            Agg.MIN -> values.min()
            Agg.MAX -> values.max()
            Agg.SUM -> values.sum()
            Agg.LAST -> values.last()
            Agg.COUNT -> values.size.toDouble()
            Agg.CV -> {
                val m = values.average()
                if (m == 0.0 || values.size < 2) 0.0
                else {
                    val sd = sqrt(values.sumOf { (it - m) * (it - m) } / (values.size - 1))
                    sd / m
                }
            }
            Agg.TIR_70_140 -> values.count { it in 70.0..140.0 }.toDouble() / values.size
        }
    }
}
