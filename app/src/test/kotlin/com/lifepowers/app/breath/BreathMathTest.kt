package com.lifepowers.app.breath

import com.lifepowers.app.domain.HeartDevice
import com.lifepowers.core.BreathPattern
import com.lifepowers.core.BreathPhase
import com.lifepowers.core.Breathing
import com.lifepowers.core.HeartReading
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BreathMathTest {
    @Test
    fun phaseTrackerFiresOncePerPhase() {
        val box = BreathPattern(4.0, 4.0, 4.0, 4.0)
        val tracker = PhaseTracker()
        val fired = mutableListOf<BreathPhase>()
        var t = 0.0
        while (t < 32.0) {
            tracker.update(Breathing.pointAt(box, t))?.let { fired.add(it) }
            t += 0.05
        }
        val cycle = listOf(BreathPhase.INHALE, BreathPhase.HOLD_IN, BreathPhase.EXHALE, BreathPhase.HOLD_OUT)
        assertEquals(cycle + cycle, fired)
    }

    @Test
    fun phaseTrackerSeesNewCycleWithoutHolds() {
        val even = BreathPattern(5.0, 0.0, 5.0, 0.0)
        val tracker = PhaseTracker()
        assertEquals(BreathPhase.INHALE, tracker.update(Breathing.pointAt(even, 0.0)))
        assertNull(tracker.update(Breathing.pointAt(even, 4.9)))
        assertEquals(BreathPhase.EXHALE, tracker.update(Breathing.pointAt(even, 5.1)))
        // A tick that jumps a whole cycle still lands on a new in breath.
        assertEquals(BreathPhase.INHALE, tracker.update(Breathing.pointAt(even, 20.5)))
    }

    @Test
    fun phaseTrackerResetFiresAgain() {
        val p = BreathPattern(4.0, 0.0, 6.0, 0.0)
        val tracker = PhaseTracker()
        tracker.update(Breathing.pointAt(p, 1.0))
        assertNull(tracker.update(Breathing.pointAt(p, 2.0)))
        tracker.reset()
        assertEquals(BreathPhase.INHALE, tracker.update(Breathing.pointAt(p, 2.0)))
    }

    @Test
    fun clockLeavesOutPausedTime() {
        val clock = SessionClock(1_000)
        assertEquals(4_000, clock.elapsedMs(5_000))
        clock.pause(5_000)
        assertTrue(clock.paused)
        assertEquals(4_000, clock.elapsedMs(9_000))
        clock.pause(9_000)
        clock.resume(11_000)
        assertFalse(clock.paused)
        assertEquals(5_000, clock.elapsedMs(12_000))
        clock.resume(13_000)
        assertEquals(6_000, clock.elapsedMs(13_000))
        assertEquals(0, SessionClock(10_000).elapsedMs(5_000))
    }

    @Test
    fun nearbyDedupesByAddressStrongestFirst() {
        var list = emptyList<HeartDevice>()
        list = BreathMath.mergeNearby(list, HeartDevice("A", "Polar H10", -80))
        list = BreathMath.mergeNearby(list, HeartDevice("B", "Strap", -60))
        list = BreathMath.mergeNearby(list, HeartDevice("A", "Polar H10", -50))
        assertEquals(listOf("A", "B"), list.map { it.address })
        assertEquals(-50, list.first().rssi)
    }

    @Test
    fun displayNameFallsBack() {
        assertEquals("Polar H10", BreathMath.displayName("Polar H10", "Other"))
        assertEquals("Other", BreathMath.displayName("  ", "Other"))
        assertEquals("Heart rate sensor", BreathMath.displayName(null, null))
    }

    @Test
    fun readingsAreCappedToWindow() {
        var list = emptyList<HeartReading>()
        for (s in 0..10) list = BreathMath.appendCapped(list, HeartReading(s * 1000L, 60.0), windowMs = 5_000)
        assertEquals((5..10).map { it * 1000L }, list.map { it.utcMs })
        val far = BreathMath.appendCapped(list, HeartReading(100_000, 70.0), windowMs = 5_000)
        assertEquals(listOf(100_000L), far.map { it.utcMs })
    }

    @Test
    fun backoffGrows() {
        assertEquals(listOf(1000L, 2000L, 4000L), (1..3).map { BreathMath.backoffMs(it) })
    }

    @Test
    fun minutesRoundToOneDecimal() {
        assertEquals(5.0, BreathMath.minutes(0, 300_000))
        assertEquals(0.5, BreathMath.minutes(0, 30_000))
        assertEquals(0.0, BreathMath.minutes(10, 0))
    }

    @Test
    fun permissionsBySdk() {
        assertEquals(listOf("android.permission.ACCESS_FINE_LOCATION"), BreathMath.requiredPermissions(30))
        assertEquals(
            listOf("android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT"),
            BreathMath.requiredPermissions(31),
        )
    }

    @Test
    fun spokenWords() {
        assertEquals("in", BreathMath.spokenWord(BreathPhase.INHALE))
        assertEquals("hold", BreathMath.spokenWord(BreathPhase.HOLD_IN))
        assertEquals("out", BreathMath.spokenWord(BreathPhase.EXHALE))
        assertEquals("hold", BreathMath.spokenWord(BreathPhase.HOLD_OUT))
    }
}
