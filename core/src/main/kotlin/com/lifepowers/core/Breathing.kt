package com.lifepowers.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class BreathPhase(val label: String) { INHALE("Breathe in"), HOLD_IN("Hold"), EXHALE("Breathe out"), HOLD_OUT("Hold") }

/** A breathing pattern in seconds. A hold of 0 means no hold. */
data class BreathPattern(
    val inhaleS: Double,
    val holdInS: Double = 0.0,
    val exhaleS: Double,
    val holdOutS: Double = 0.0,
) {
    val cycleS: Double get() = inhaleS + holdInS + exhaleS + holdOutS
    val breathsPerMinute: Double get() = if (cycleS <= 0) 0.0 else 60.0 / cycleS

    /** "4 in, 7 hold, 8 out", in words and without the empty holds. */
    fun describe(): String {
        fun n(v: Double) = if (v == Math.floor(v)) v.toLong().toString() else String.format("%.1f", v)
        val parts = mutableListOf("${n(inhaleS)} in")
        if (holdInS > 0) parts.add("${n(holdInS)} hold")
        parts.add("${n(exhaleS)} out")
        if (holdOutS > 0) parts.add("${n(holdOutS)} hold")
        return parts.joinToString(", ")
    }

    fun duration(phase: BreathPhase): Double = when (phase) {
        BreathPhase.INHALE -> inhaleS
        BreathPhase.HOLD_IN -> holdInS
        BreathPhase.EXHALE -> exhaleS
        BreathPhase.HOLD_OUT -> holdOutS
    }
}

enum class BreathSeverity { BLOCK, CAUTION, NOTE }

/** One finding about a pattern. BLOCK means the pattern cannot be started or saved. */
data class BreathIssue(val severity: BreathSeverity, val code: String, val message: String)

/** Where the breath is at a moment in a session. */
data class BreathPoint(val phase: BreathPhase, val cycle: Int, val phaseElapsedS: Double, val phaseRemainingS: Double, val fill: Double)

/**
 * Breathing patterns, their safety rules, and where a session is at any moment. The rules are
 * deliberately conservative: a longer in breath than out breath, a pace faster than 20 breaths a
 * minute, and holds over 20 seconds are not allowed; slower or held patterns get a plain caution.
 */
object Breathing {
    const val MIN_PHASE_S = 2.0
    const val MAX_PHASE_S = 20.0
    const val MAX_HOLD_S = 20.0
    const val CAUTION_HOLD_S = 10.0
    const val MAX_BPM = 20.0
    const val CAUTION_SLOW_BPM = 3.0
    const val RESONANCE_LOW_BPM = 4.5
    const val RESONANCE_HIGH_BPM = 6.5

    data class Preset(val name: String, val pattern: BreathPattern, val about: String)

    val PRESETS = listOf(
        Preset("Resonance", BreathPattern(5.5, 0.0, 5.5, 0.0), "About 5.5 breaths a minute, the pace where heart rate and breath tend to sway together."),
        Preset("Longer out breath", BreathPattern(4.0, 0.0, 6.0, 0.0), "A soft way to settle, with the out breath a little longer."),
        Preset("Box", BreathPattern(4.0, 4.0, 4.0, 4.0), "Even sides: in, hold, out, hold."),
        Preset("4 7 8", BreathPattern(4.0, 7.0, 8.0, 0.0), "A long out breath after a held in breath. Often used before sleep."),
        Preset("Easy even", BreathPattern(5.0, 0.0, 5.0, 0.0), "Six breaths a minute, in and out the same length."),
    )

    fun validate(p: BreathPattern): List<BreathIssue> {
        val out = mutableListOf<BreathIssue>()
        fun block(code: String, msg: String) = out.add(BreathIssue(BreathSeverity.BLOCK, code, msg))
        fun caution(code: String, msg: String) = out.add(BreathIssue(BreathSeverity.CAUTION, code, msg))

        if (p.inhaleS < MIN_PHASE_S || p.exhaleS < MIN_PHASE_S)
            block("too_short", "Breathing in and out each need at least 2 seconds.")
        if (p.inhaleS > MAX_PHASE_S || p.exhaleS > MAX_PHASE_S)
            block("too_long_phase", "Keep breathing in and out under 20 seconds each.")
        if (p.holdInS < 0 || p.holdOutS < 0)
            block("negative_hold", "A hold cannot be less than nothing.")
        if (p.inhaleS > p.exhaleS)
            block("inhale_longer", "Your out breath needs to be at least as long as your in breath. A longer in breath than out breath tends to wind the body up rather than settle it, and can tip into overbreathing.")
        if (p.holdInS > MAX_HOLD_S || p.holdOutS > MAX_HOLD_S)
            block("hold_too_long", "Holds longer than 20 seconds are not offered here. They can bring on dizziness or air hunger.")
        if (p.cycleS > 0 && p.breathsPerMinute > MAX_BPM)
            block("too_fast", "This works out to more than 20 breaths a minute, which is fast enough to cause overbreathing. Slow it down.")

        if (out.none { it.code == "hold_too_long" } && (p.holdInS > CAUTION_HOLD_S || p.holdOutS > CAUTION_HOLD_S))
            caution("long_hold", "Holds over 10 seconds can bring lightheadedness. Skip long holds if you are pregnant, have a heart or lung condition, or feel panicky, and stop if you feel dizzy.")
        if (p.cycleS > 0 && p.breathsPerMinute < CAUTION_SLOW_BPM)
            caution("very_slow", "Under 3 breaths a minute is very slow and can feel like air hunger. Ease off if it feels like a strain.")
        if (p.holdInS + p.holdOutS > p.inhaleS + p.exhaleS && out.none { it.severity == BreathSeverity.BLOCK })
            caution("more_holding", "There is more holding than breathing in this pattern. Shorter holds are gentler.")
        if (p.inhaleS >= MIN_PHASE_S && p.inhaleS < 3.0 && p.exhaleS < 3.0 && out.none { it.code == "too_fast" })
            caution("quick", "Short breaths are fine for a minute, but slower breaths are what settles the body.")
        if (out.none { it.severity == BreathSeverity.BLOCK } && p.breathsPerMinute in RESONANCE_LOW_BPM..RESONANCE_HIGH_BPM)
            out.add(BreathIssue(BreathSeverity.NOTE, "resonance", "Near resonance pace, where heart rate tends to rise and fall with each breath."))
        return out
    }

    fun isAllowed(p: BreathPattern): Boolean = validate(p).none { it.severity == BreathSeverity.BLOCK }

    /** Where the breath is [elapsedS] seconds into a session. [fill] runs 0 to 1 on the in breath and 1 to 0 on the out breath. */
    fun pointAt(p: BreathPattern, elapsedS: Double): BreathPoint {
        val cycleS = p.cycleS.coerceAtLeast(0.001)
        val cycle = (elapsedS / cycleS).toInt()
        var t = elapsedS - cycle * cycleS
        for (phase in BreathPhase.values()) {
            val d = p.duration(phase)
            if (d <= 0) continue
            if (t < d) {
                val f = t / d
                val fill = when (phase) {
                    BreathPhase.INHALE -> easeInOut(f)
                    BreathPhase.HOLD_IN -> 1.0
                    BreathPhase.EXHALE -> 1.0 - easeInOut(f)
                    BreathPhase.HOLD_OUT -> 0.0
                }
                return BreathPoint(phase, cycle, t, d - t, fill)
            }
            t -= d
        }
        return BreathPoint(BreathPhase.INHALE, cycle + 1, 0.0, p.inhaleS, 0.0)
    }

    private fun easeInOut(x: Double) = 0.5 - 0.5 * cos(PI * x.coerceIn(0.0, 1.0))
}

/** One heart reading during a session. RR intervals in milliseconds when the sensor sends them. */
data class HeartReading(val utcMs: Long, val bpm: Double, val rrMs: List<Double> = emptyList())

/** Live and summary numbers for a breathing session. */
data class BioSnapshot(
    val bpm: Double?,
    /** RMSSD over the last minute of beat to beat intervals, in ms. Null without RR data. */
    val rmssdMs: Double?,
    /** Average heart rate swing (max minus min) over the last three full breaths, in bpm. */
    val swingBpm: Double?,
    /** Share of heart rate variation that moves at the breathing pace, 0 to 1. */
    val sync: Double?,
)

data class BioSummary(
    val startBpm: Double?,
    val endBpm: Double?,
    val startRmssdMs: Double?,
    val endRmssdMs: Double?,
    val bestSwingBpm: Double?,
    val meanSync: Double?,
    val readings: Int,
)

object Biofeedback {
    const val RR_MIN_MS = 300.0
    const val RR_MAX_MS = 2000.0

    /**
     * Parses a Bluetooth Heart Rate Measurement (0x2A37) value. Flags bit 0 selects an 8 or 16 bit
     * rate, bit 3 marks an energy field to skip, bit 4 marks RR intervals in 1/1024 s.
     */
    fun parseHeartRateMeasurement(value: ByteArray, utcMs: Long): HeartReading? {
        if (value.isEmpty()) return null
        val flags = value[0].toInt() and 0xFF
        var i = 1
        val bpm: Int
        if (flags and 0x01 == 0) {
            if (value.size < 2) return null
            bpm = value[1].toInt() and 0xFF; i = 2
        } else {
            if (value.size < 3) return null
            bpm = (value[1].toInt() and 0xFF) or ((value[2].toInt() and 0xFF) shl 8); i = 3
        }
        if (flags and 0x08 != 0) i += 2
        val rr = mutableListOf<Double>()
        if (flags and 0x10 != 0) {
            while (i + 1 < value.size) {
                val raw = (value[i].toInt() and 0xFF) or ((value[i + 1].toInt() and 0xFF) shl 8)
                rr.add(raw * 1000.0 / 1024.0)
                i += 2
            }
        }
        if (bpm <= 0) return null
        return HeartReading(utcMs, bpm.toDouble(), rr)
    }

    /** Drops implausible intervals and ectopic jumps of more than 20 percent from the previous beat. */
    fun cleanRr(rr: List<Double>): List<Double> {
        val out = mutableListOf<Double>()
        for (x in rr) {
            if (x < RR_MIN_MS || x > RR_MAX_MS) continue
            val prev = out.lastOrNull()
            if (prev != null && abs(x - prev) > 0.2 * prev) continue
            out.add(x)
        }
        return out
    }

    fun rmssd(rr: List<Double>): Double? {
        val c = cleanRr(rr)
        if (c.size < 10) return null
        var sum = 0.0
        for (k in 1 until c.size) { val d = c[k] - c[k - 1]; sum += d * d }
        return sqrt(sum / (c.size - 1))
    }

    /** Heart rate per second from readings: from RR intervals when present, else the reported rate. */
    fun hrSeries(readings: List<HeartReading>, fromUtcMs: Long, toUtcMs: Long): DoubleArray {
        val seconds = ((toUtcMs - fromUtcMs) / 1000).toInt().coerceAtLeast(0)
        val out = DoubleArray(seconds) { Double.NaN }
        val beats = mutableListOf<Pair<Long, Double>>()
        for (r in readings) {
            val rr = cleanRr(r.rrMs)
            if (rr.isNotEmpty()) {
                var t = r.utcMs - rr.sum().toLong()
                for (x in rr) { t += x.toLong(); beats.add(t to 60_000.0 / x) }
            } else beats.add(r.utcMs to r.bpm)
        }
        beats.sortBy { it.first }
        var j = 0
        var last = Double.NaN
        for (s in 0 until seconds) {
            val t = fromUtcMs + (s + 1) * 1000L
            while (j < beats.size && beats[j].first <= t) { last = beats[j].second; j++ }
            out[s] = last
        }
        return out
    }

    /**
     * How much of the heart rate's movement follows the breath: power at the breathing frequency over
     * total variance of the detrended series, scaled to 0 to 1. Needs at least two full breaths.
     */
    fun sync(hrPerSecond: DoubleArray, breathHz: Double): Double? {
        val x = hrPerSecond.filter { !it.isNaN() }
        if (breathHz <= 0 || x.size < (2.0 / breathHz).roundToInt().coerceAtLeast(16)) return null
        val n = x.size
        val mean = x.average()
        val idx = (0 until n).map { it.toDouble() }
        val mi = idx.average()
        val slope = idx.zip(x).sumOf { (i, v) -> (i - mi) * (v - mean) } / idx.sumOf { (it - mi) * (it - mi) }.coerceAtLeast(1e-9)
        val d = x.mapIndexed { i, v -> v - mean - slope * (i - mi) }
        val variance = d.sumOf { it * it } / n
        if (variance < 1e-6) return null
        var re = 0.0; var im = 0.0
        for (i in 0 until n) { re += d[i] * cos(2 * PI * breathHz * i); im += d[i] * sin(2 * PI * breathHz * i) }
        val power = 2 * (re * re + im * im) / (n.toDouble() * n)
        return (power / variance).coerceIn(0.0, 1.0)
    }

    /** Average max minus min heart rate within each of the last [cycles] full breaths. */
    fun swing(hrPerSecond: DoubleArray, cycleS: Double, cycles: Int = 3): Double? {
        val per = cycleS.roundToInt().coerceAtLeast(2)
        val full = hrPerSecond.size / per
        if (full < 1) return null
        val start = (full - cycles).coerceAtLeast(0)
        val swings = (start until full).mapNotNull { c ->
            val w = (c * per until (c + 1) * per).map { hrPerSecond[it] }.filter { !it.isNaN() }
            if (w.size < per / 2) null else w.max() - w.min()
        }
        return swings.takeIf { it.isNotEmpty() }?.average()
    }

    /** The live numbers over the last minute of a session. */
    fun snapshot(readings: List<HeartReading>, nowUtcMs: Long, pattern: BreathPattern): BioSnapshot {
        val recent = readings.filter { it.utcMs > nowUtcMs - 60_000 }
        val series = hrSeries(readings.filter { it.utcMs > nowUtcMs - 90_000 }, nowUtcMs - 64_000, nowUtcMs)
        return BioSnapshot(
            bpm = readings.lastOrNull()?.bpm,
            rmssdMs = rmssd(recent.flatMap { it.rrMs }),
            swingBpm = swing(series, pattern.cycleS),
            sync = sync(series, 1.0 / pattern.cycleS.coerceAtLeast(0.1)),
        )
    }

    /** Start against end, from the first and last minute of the session. */
    fun summary(readings: List<HeartReading>, startUtcMs: Long, endUtcMs: Long, pattern: BreathPattern): BioSummary {
        if (readings.isEmpty()) return BioSummary(null, null, null, null, null, null, 0)
        val first = readings.filter { it.utcMs < startUtcMs + 60_000 }
        val last = readings.filter { it.utcMs > endUtcMs - 60_000 }
        val all = hrSeries(readings, startUtcMs, endUtcMs)
        val per = (pattern.cycleS * 3).roundToInt().coerceAtLeast(6)
        val swings = all.toList().chunked(per).mapNotNull { swing(it.toDoubleArray(), pattern.cycleS) }
        val syncs = all.toList().chunked(64).mapNotNull { sync(it.toDoubleArray(), 1.0 / pattern.cycleS) }
        return BioSummary(
            startBpm = first.map { it.bpm }.takeIf { it.isNotEmpty() }?.average(),
            endBpm = last.map { it.bpm }.takeIf { it.isNotEmpty() }?.average(),
            startRmssdMs = rmssd(first.flatMap { it.rrMs }),
            endRmssdMs = rmssd(last.flatMap { it.rrMs }),
            bestSwingBpm = swings.maxOrNull(),
            meanSync = syncs.takeIf { it.isNotEmpty() }?.average(),
            readings = readings.size,
        )
    }
}
