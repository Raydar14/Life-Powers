package com.lifepowers.app.breath

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/** Soft sine tones as 16 bit mono samples. Pure, so the shapes can be checked without a speaker. */
object ToneSynth {
    const val SAMPLE_RATE = 44_100
    const val AMPLITUDE = 0.25
    const val LOW_HZ = 220.0
    const val HIGH_HZ = 330.0
    const val TICK_HZ = 660.0
    const val TICK_S = 0.06
    const val TICK_AMPLITUDE = 0.12
    const val MAX_FADE_S = 0.6

    /**
     * A sine gliding from [fromHz] to [toHz] over [durationS], with a raised cosine fade in and out of up to
     * [MAX_FADE_S] (never more than a quarter of the tone each), so it starts and ends at silence.
     */
    fun glide(
        fromHz: Double,
        toHz: Double,
        durationS: Double,
        amplitude: Double = AMPLITUDE,
        sampleRate: Int = SAMPLE_RATE,
    ): ShortArray {
        val n = (durationS.coerceAtLeast(0.0) * sampleRate).roundToInt()
        if (n <= 0) return ShortArray(0)
        val fadeN = (minOf(MAX_FADE_S, durationS / 4) * sampleRate).roundToInt().coerceAtLeast(1)
        val out = ShortArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val f = if (n == 1) fromHz else fromHz + (toHz - fromHz) * i / (n - 1)
            val env = envelope(i, n, fadeN)
            out[i] = toShort(amplitude * env * sin(phase))
            phase += 2 * PI * f / sampleRate
            if (phase > 2 * PI) phase -= 2 * PI
        }
        return out
    }

    /** A short quiet tick for holds: a few milliseconds of rise, then a fast decay to silence. */
    fun tick(
        hz: Double = TICK_HZ,
        durationS: Double = TICK_S,
        amplitude: Double = TICK_AMPLITUDE,
        sampleRate: Int = SAMPLE_RATE,
    ): ShortArray {
        val n = (durationS * sampleRate).roundToInt().coerceAtLeast(1)
        val rise = (0.004 * sampleRate).roundToInt().coerceAtLeast(1)
        return ShortArray(n) { i ->
            val t = i.toDouble() / sampleRate
            val attack = if (i < rise) i.toDouble() / rise else 1.0
            val decay = exp(-t / (durationS / 5))
            val tail = if (i == n - 1) 0.0 else 1.0
            toShort(amplitude * attack * decay * tail * sin(2 * PI * hz * t))
        }
    }

    /** The single soft tone at the end of a planned session: a slow settle from 330 Hz down to 262 Hz. */
    fun completion(sampleRate: Int = SAMPLE_RATE): ShortArray = glide(HIGH_HZ, 262.0, 1.6, AMPLITUDE, sampleRate)

    private fun envelope(i: Int, n: Int, fadeN: Int): Double {
        val fromStart = i.toDouble() / fadeN
        val fromEnd = (n - 1 - i).toDouble() / fadeN
        val x = minOf(1.0, fromStart, fromEnd)
        return 0.5 - 0.5 * kotlin.math.cos(PI * x)
    }

    private fun toShort(v: Double): Short = (v.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).roundToInt().toShort()
}
