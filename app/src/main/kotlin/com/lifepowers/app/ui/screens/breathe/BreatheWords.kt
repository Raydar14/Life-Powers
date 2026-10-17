package com.lifepowers.app.ui.screens.breathe

import com.lifepowers.app.domain.BreathGuide
import com.lifepowers.app.ui.components.Fmt
import com.lifepowers.core.BreathPattern
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Plain words for the breathing screens. Sentence case, no symbols standing in for words. */
internal object BreathWords {

    /** "4" or "4.5". */
    fun secs(v: Double): String {
        val half = (v * 2).roundToLong() / 2.0
        return if (half == Math.floor(half)) half.toLong().toString() else String.format(Locale.getDefault(), "%.1f", half)
    }

    /** "5.5 breaths a minute", "6 breaths a minute". */
    fun pace(p: BreathPattern): String {
        val bpm = p.breathsPerMinute
        val rounded = (bpm * 10).roundToInt() / 10.0
        val n = if (rounded == Math.floor(rounded)) rounded.toLong().toString() else String.format(Locale.getDefault(), "%.1f", rounded)
        return if (n == "1") "1 breath a minute" else "$n breaths a minute"
    }

    /** "Visual timer and tones". */
    fun guide(g: BreathGuide): String {
        val parts = buildList {
            if (g.visual) add("visual timer")
            if (g.tones) add("tones")
            if (g.voice) add("spoken cues")
            if (g.vibration) add("vibration")
        }
        val joined = Fmt.joinSentence(parts)
        return joined.replaceFirstChar { it.titlecase(Locale.getDefault()) }.ifEmpty { "Visual timer" }
    }

    /** "10 minutes" or "Open ended". */
    fun length(min: Int?): String = when (min) {
        null -> "Open ended"
        1 -> "1 minute"
        else -> "$min minutes"
    }

    /** A session's length in words: "12 minutes", "1 minute", "45 seconds". */
    fun spent(ms: Long): String {
        val s = (ms / 1000.0).roundToInt().coerceAtLeast(0)
        return when {
            s < 60 -> if (s == 1) "1 second" else "$s seconds"
            s < 90 -> "1 minute"
            else -> "${(s / 60.0).roundToInt()} minutes"
        }
    }

    /** Whole minutes for the summary sentence, at least one. */
    fun minutesOf(ms: Long): Int = (ms / 60_000.0).roundToInt().coerceAtLeast(1)

    /** Heart rate following the breath, as words. */
    fun sync(s: Double): String = when {
        s >= 0.6 -> "in step with your breath"
        s >= 0.3 -> "finding its rhythm"
        else -> "not in step yet"
    }

    /** "72 to 64 bpm". */
    fun hrChange(start: Double, end: Double): String = "${Fmt.num(start)} to ${Fmt.num(end)} bpm"

    /** A gentle reading of the change from start to end. */
    fun hrChangeLine(start: Double, end: Double): String {
        val d = end - start
        return when {
            abs(d) < 2 -> "Your heart rate held about steady."
            d < 0 -> "Your heart rate settled by ${Fmt.num(-d)} bpm."
            else -> "Your heart rate rose by ${Fmt.num(d)} bpm. That can happen early on or after moving."
        }
    }

    /** "Thursday, October 16 at 7:30 am". */
    fun startedAt(utcMs: Long, zoneOffsetMin: Int): String =
        "${Fmt.dateLine(Fmt.localDateOf(utcMs, zoneOffsetMin))} at ${Fmt.timeAt(utcMs, zoneOffsetMin)}"

    /** 9:05, or 1:02:05 past the hour. */
    fun clock(seconds: Double): String {
        val s = seconds.roundToLong().coerceAtLeast(0L)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%d:%02d", m, sec)
    }
}
