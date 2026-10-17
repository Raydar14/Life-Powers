package com.lifepowers.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class BreathingTest {
    private fun codes(p: BreathPattern) = Breathing.validate(p).map { it.code }

    @Test fun longerInhaleIsBlocked() {
        val p = BreathPattern(6.0, 0.0, 4.0, 0.0)
        assertFalse(Breathing.isAllowed(p))
        assertTrue("inhale_longer" in codes(p))
        assertTrue(Breathing.isAllowed(BreathPattern(5.0, 0.0, 5.0, 0.0)))
    }

    @Test fun fastPaceAndLongHoldsAreBlockedAndSlowHoldsCautioned() {
        assertTrue("quick" in codes(BreathPattern(2.0, 0.0, 2.0, 0.0)))
        assertTrue("hold_too_long" in codes(BreathPattern(4.0, 25.0, 8.0, 0.0)))
        val longHold = Breathing.validate(BreathPattern(4.0, 12.0, 8.0, 0.0))
        assertTrue(longHold.any { it.code == "long_hold" && it.severity == BreathSeverity.CAUTION })
        assertTrue(Breathing.isAllowed(BreathPattern(4.0, 12.0, 8.0, 0.0)))
        assertTrue("very_slow" in codes(BreathPattern(10.0, 0.0, 12.0, 0.0)))
        assertTrue("too_short" in codes(BreathPattern(1.0, 0.0, 3.0, 0.0)))
    }

    @Test fun presetsAreAllAllowedAndResonanceIsNoted() {
        Breathing.PRESETS.forEach { assertTrue(it.name, Breathing.isAllowed(it.pattern)) }
        assertTrue("resonance" in codes(Breathing.PRESETS.first().pattern))
        assertEquals("4 in, 7 hold, 8 out", BreathPattern(4.0, 7.0, 8.0, 0.0).describe())
    }

    @Test fun pointAtWalksThroughPhases() {
        val box = BreathPattern(4.0, 4.0, 4.0, 4.0)
        assertEquals(BreathPhase.INHALE, Breathing.pointAt(box, 1.0).phase)
        assertEquals(BreathPhase.HOLD_IN, Breathing.pointAt(box, 5.0).phase)
        assertEquals(BreathPhase.EXHALE, Breathing.pointAt(box, 9.0).phase)
        assertEquals(BreathPhase.HOLD_OUT, Breathing.pointAt(box, 13.0).phase)
        val next = Breathing.pointAt(box, 17.0)
        assertEquals(BreathPhase.INHALE, next.phase)
        assertEquals(1, next.cycle)
        val noHolds = BreathPattern(4.0, 0.0, 6.0, 0.0)
        assertEquals(BreathPhase.EXHALE, Breathing.pointAt(noHolds, 4.5).phase)
        assertEquals(1.0, Breathing.pointAt(box, 6.0).fill, 1e-9)
    }

    @Test fun parsesHeartRateMeasurement() {
        // Flags 0x10: 8 bit rate, RR present. 72 bpm, RR 1024 (1000 ms) and 870 (~850 ms).
        val v = byteArrayOf(0x10, 72, 0x00, 0x04, 0x66, 0x03)
        val r = Biofeedback.parseHeartRateMeasurement(v, 1000L)!!
        assertEquals(72.0, r.bpm, 0.0)
        assertEquals(2, r.rrMs.size)
        assertEquals(1000.0, r.rrMs[0], 0.01)
        // 16 bit rate with energy field and no RR.
        val v16 = byteArrayOf(0x09, 0x2C, 0x01, 0x10, 0x00)
        assertEquals(300.0, Biofeedback.parseHeartRateMeasurement(v16, 0L)!!.bpm, 0.0)
        assertNull(Biofeedback.parseHeartRateMeasurement(byteArrayOf(), 0L))
    }

    @Test fun breathSyncedHeartRateScoresHigh() {
        val cycle = 10.0
        val synced = DoubleArray(80) { 65 + 6 * sin(2 * PI * it / cycle) }
        val flat = DoubleArray(80) { i -> 65 + if (i % 7 == 0) 3.0 else -0.5 }
        val s = Biofeedback.sync(synced, 1 / cycle)!!
        assertTrue("sync $s", s > 0.8)
        assertTrue((Biofeedback.sync(flat, 1 / cycle) ?: 0.0) < 0.3)
        assertEquals(12.0, Biofeedback.swing(synced, cycle)!!, 0.6)
        val rr = (0 until 40).map { 900.0 + if (it % 2 == 0) 30 else -30 }
        assertEquals(60.0, Biofeedback.rmssd(rr)!!, 0.5)
        assertNotNull(Biofeedback.rmssd(rr + listOf(5000.0)))
    }

    @Test fun summaryComparesStartAndEnd() {
        val start = 0L
        val readings = (0 until 300).map { s -> HeartReading(start + s * 1000L, if (s < 60) 75.0 else if (s >= 240) 64.0 else 70.0) }
        val sum = Biofeedback.summary(readings, start, start + 300_000L, BreathPattern(5.0, 0.0, 5.0, 0.0))
        assertEquals(75.0, sum.startBpm!!, 0.01)
        assertEquals(64.0, sum.endBpm!!, 0.01)
    }
}
