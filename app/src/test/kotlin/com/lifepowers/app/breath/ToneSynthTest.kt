package com.lifepowers.app.breath

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToneSynthTest {
    private fun peak(s: ShortArray): Int = s.maxOf { abs(it.toInt()) }

    @Test
    fun glideLastsThePhaseAndStaysSoft() {
        val tone = ToneSynth.glide(220.0, 330.0, 4.0)
        assertEquals(4 * ToneSynth.SAMPLE_RATE, tone.size)
        assertTrue(peak(tone) <= (ToneSynth.AMPLITUDE * Short.MAX_VALUE).toInt() + 1)
        assertTrue(peak(tone) > (0.2 * Short.MAX_VALUE).toInt())
    }

    @Test
    fun glideFadesInAndOut() {
        val tone = ToneSynth.glide(330.0, 220.0, 2.0)
        assertEquals(0, tone.first().toInt())
        assertTrue(abs(tone.last().toInt()) < 50)
        // The first 10 ms are far quieter than the middle.
        val head = tone.copyOfRange(0, 441)
        val middle = tone.copyOfRange(tone.size / 2 - 441, tone.size / 2 + 441)
        assertTrue(peak(head) * 10 < peak(middle))
    }

    @Test
    fun glideRises() {
        // Count zero crossings in the first and last half second: a rising glide crosses more often at the end.
        val tone = ToneSynth.glide(220.0, 330.0, 4.0)
        fun crossings(from: Int, to: Int) = (from + 1 until to).count { (tone[it - 1] < 0) != (tone[it] < 0) }
        val half = ToneSynth.SAMPLE_RATE / 2
        assertTrue(crossings(tone.size - half, tone.size) > crossings(0, half))
    }

    @Test
    fun tickIsShortQuietAndEndsSilent() {
        val tick = ToneSynth.tick()
        assertEquals((0.06 * ToneSynth.SAMPLE_RATE).toInt(), tick.size)
        assertTrue(peak(tick) <= (ToneSynth.TICK_AMPLITUDE * Short.MAX_VALUE).toInt() + 1)
        assertEquals(0, tick.last().toInt())
    }

    @Test
    fun emptyForNoTime() {
        assertEquals(0, ToneSynth.glide(220.0, 330.0, 0.0).size)
        assertEquals(0, ToneSynth.glide(220.0, 330.0, -1.0).size)
    }
}
