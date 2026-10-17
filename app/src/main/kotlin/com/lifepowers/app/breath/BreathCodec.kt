package com.lifepowers.app.breath

import com.lifepowers.app.domain.BreathGuide
import kotlin.math.roundToLong

/**
 * Text forms for the breath tables. The guide is a comma list of modes ("visual,tones,voice,vibration");
 * the heart rate timeline is comma separated numbers with blank slots where there was no reading.
 */
object BreathCodec {
    const val VISUAL = "visual"
    const val TONES = "tones"
    const val VOICE = "voice"
    const val VIBRATION = "vibration"

    /** Seconds per slot in a stored heart rate timeline. */
    const val TIMELINE_STEP_S = 5

    fun encodeGuide(g: BreathGuide): String = buildList {
        if (g.visual) add(VISUAL)
        if (g.tones) add(TONES)
        if (g.voice) add(VOICE)
        if (g.vibration) add(VIBRATION)
    }.joinToString(",")

    /** Unknown words are ignored. Nothing usable falls back to the default guide, since at least one mode must be on. */
    fun decodeGuide(s: String?): BreathGuide {
        val modes = s.orEmpty().split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        val g = BreathGuide(
            visual = VISUAL in modes,
            tones = TONES in modes,
            voice = VOICE in modes,
            vibration = VIBRATION in modes,
        )
        return if (g.visual || g.tones || g.voice || g.vibration) g else BreathGuide()
    }

    /** Numbers keep one decimal, whole numbers lose the ".0", and null or NaN become an empty slot. */
    fun encodeTimeline(values: List<Double?>): String =
        values.joinToString(",") { v -> if (v == null || v.isNaN() || v.isInfinite()) "" else number(v) }

    /** An empty string is an empty timeline. */
    fun decodeTimeline(s: String?): List<Double?> {
        if (s.isNullOrEmpty()) return emptyList()
        return s.split(',').map { it.trim().toDoubleOrNull()?.takeIf { v -> !v.isNaN() } }
    }

    /** Means of each [stepS] seconds of a per second series, ignoring gaps; a slot with no values is null. */
    fun timeline(hrPerSecond: DoubleArray, stepS: Int = TIMELINE_STEP_S): List<Double?> {
        if (stepS <= 0 || hrPerSecond.isEmpty()) return emptyList()
        return (hrPerSecond.indices step stepS).map { from ->
            val slot = (from until minOf(from + stepS, hrPerSecond.size)).map { hrPerSecond[it] }.filter { !it.isNaN() }
            if (slot.isEmpty()) null else round1(slot.average())
        }
    }

    /** A per second series with gaps as null, for the live line. */
    fun gapsAsNull(hrPerSecond: DoubleArray): List<Double?> = hrPerSecond.map { if (it.isNaN()) null else it }

    fun round1(v: Double): Double = (v * 10).roundToLong() / 10.0

    private fun number(v: Double): String {
        val r = round1(v)
        return if (r == Math.floor(r) && kotlin.math.abs(r) < 1e15) r.toLong().toString() else r.toString()
    }
}
