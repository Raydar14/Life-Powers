package com.lifepowers.app.breath

import com.lifepowers.app.domain.BreathGuide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BreathCodecTest {
    @Test
    fun guideRoundTrips() {
        val all = BreathGuide(visual = true, tones = true, voice = true, vibration = true)
        assertEquals("visual,tones,voice,vibration", BreathCodec.encodeGuide(all))
        assertEquals(all, BreathCodec.decodeGuide(BreathCodec.encodeGuide(all)))
        val some = BreathGuide(visual = false, tones = true, voice = false, vibration = true)
        assertEquals("tones,vibration", BreathCodec.encodeGuide(some))
        assertEquals(some, BreathCodec.decodeGuide("tones,vibration"))
    }

    @Test
    fun guideDecodeIsForgiving() {
        assertEquals(BreathGuide(visual = false, voice = true), BreathCodec.decodeGuide(" Voice , humming "))
        assertEquals(BreathGuide(), BreathCodec.decodeGuide(""))
        assertEquals(BreathGuide(), BreathCodec.decodeGuide(null))
        assertEquals(BreathGuide(), BreathCodec.decodeGuide("humming"))
    }

    @Test
    fun timelineRoundTripsWithBlanks() {
        val values = listOf(72.0, null, 68.46, Double.NaN, 70.25)
        val text = BreathCodec.encodeTimeline(values)
        assertEquals("72,,68.5,,70.3", text)
        assertEquals(listOf(72.0, null, 68.5, null, 70.3), BreathCodec.decodeTimeline(text))
    }

    @Test
    fun timelineEdges() {
        assertEquals("", BreathCodec.encodeTimeline(emptyList()))
        assertEquals(emptyList(), BreathCodec.decodeTimeline(""))
        assertEquals(emptyList(), BreathCodec.decodeTimeline(null))
        assertEquals(listOf(null, null), BreathCodec.decodeTimeline(","))
        assertEquals(listOf(60.0, null), BreathCodec.decodeTimeline("60,x"))
    }

    @Test
    fun timelineMeansEachFiveSeconds() {
        val nan = Double.NaN
        val series = doubleArrayOf(60.0, 62.0, 64.0, nan, 64.0, nan, nan, nan, nan, nan, 70.0, 71.0)
        assertEquals(listOf(62.5, null, 70.5), BreathCodec.timeline(series))
        assertTrue(BreathCodec.timeline(DoubleArray(0)).isEmpty())
    }

    @Test
    fun gapsBecomeNull() {
        assertEquals(listOf(60.0, null), BreathCodec.gapsAsNull(doubleArrayOf(60.0, Double.NaN)))
    }
}
