package com.lifepowers.core

/**
 * When two sources report the same metric for the same day (Oura's own API and Oura's copy in
 * Health Connect, or a watch and a ring both writing steps), only the highest ranked source present
 * is kept so sums are not doubled. Mirrors the source priority table in docs/DATA_SOURCES.md.
 * Sources are matched by prefix, so `health_connect` covers `health_connect:<package>`.
 */
object SourcePriority {
    const val OURA_API = "oura_api"
    const val HC_OURA = "health_connect:com.ouraring.oura"
    const val HC_SAMSUNG = "health_connect:com.sec.android.app.shealth"
    const val HC = "health_connect"

    private val ringFirst = listOf(OURA_API, HC_OURA, HC_SAMSUNG, HC)
    private val watchFirst = listOf(HC_SAMSUNG, HC, HC_OURA, OURA_API)

    private val byMetric: Map<String, List<String>> = buildMap {
        listOf(
            Metrics.SLEEP_TOTAL_MIN, Metrics.SLEEP_DEEP_MIN, Metrics.SLEEP_REM_MIN, Metrics.SLEEP_LIGHT_MIN,
            Metrics.SLEEP_AWAKE_MIN, Metrics.SLEEP_ONSET_LATENCY_MIN, Metrics.SLEEP_BEDTIME_UTC, Metrics.SLEEP_WAKE_UTC,
            Metrics.SLEEP_EFFICIENCY, Metrics.HRV_NIGHT_AVG, Metrics.HR_RESTING, Metrics.RESP_RATE_NIGHT,
            Metrics.TEMP_DEVIATION, Metrics.SPO2_NIGHT_AVG,
        ).forEach { put(it, ringFirst) }
        listOf(
            Metrics.ACTIVITY_STEPS, Metrics.ACTIVITY_ACTIVE_KCAL, Metrics.WORKOUT_SESSION, Metrics.HR_SAMPLE,
        ).forEach { put(it, watchFirst) }
    }

    /** Lower is better. Null when the metric has no priority list or the source is not on it. */
    fun rank(metric: String, source: String): Int? {
        val list = byMetric[metric] ?: return null
        val i = list.indexOfFirst { source == it || source.startsWith("$it:") }
        return if (i < 0) null else i
    }

    fun select(samples: List<Sample>): List<Sample> {
        if (samples.isEmpty()) return samples
        val out = ArrayList<Sample>(samples.size)
        samples.groupBy { it.metric to Days.localDay(it, Metrics.spec(it.metric).dayRule) }.forEach { (key, group) ->
            val sources = group.map { it.source }.distinct()
            if (sources.size == 1) { out += group; return@forEach }
            val ranked = sources.associateWith { rank(key.first, it) ?: Int.MAX_VALUE }
            val best = ranked.values.min()
            if (best == Int.MAX_VALUE) { out += group; return@forEach }
            out += group.filter { ranked[it.source] == best }
        }
        return out.sortedBy { it.startUtcMs }
    }
}
