package com.lifepowers.app.data

import com.lifepowers.app.connectors.Interval
import com.lifepowers.app.connectors.ScreenEvent
import com.lifepowers.app.connectors.ScreenIntervals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScreenIntervalsTest {
    private val day = 1_760_400_000_000L // an arbitrary local midnight
    private val min = 60_000L
    private val hour = 60 * min

    private fun fg(t: Long, pkg: String = "a") = ScreenEvent(t, pkg, ScreenEvent.Type.FOREGROUND)
    private fun bg(t: Long, pkg: String = "a") = ScreenEvent(t, pkg, ScreenEvent.Type.BACKGROUND)
    private fun off(t: Long) = ScreenEvent(t, "", ScreenEvent.Type.SCREEN_OFF)

    @Test
    fun mergeUnionsOverlappingAndTouchingIntervals() {
        val merged = ScreenIntervals.merge(
            listOf(Interval(10, 20), Interval(15, 30), Interval(30, 40), Interval(50, 60), Interval(55, 56), Interval(70, 70)),
        )
        assertEquals(listOf(Interval(10, 40), Interval(50, 60)), merged)
    }

    @Test
    fun reconstructPairsForegroundAndBackgroundPerPackage() {
        val events = listOf(
            fg(day + 1 * hour, "a"),
            fg(day + 1 * hour + 10 * min, "b"),
            bg(day + 1 * hour + 20 * min, "a"),
            bg(day + 1 * hour + 30 * min, "b"),
            fg(day + 5 * hour, "c"),
        )
        val intervals = ScreenIntervals.reconstruct(events, day + 5 * hour + 15 * min)
        assertEquals(listOf(Interval(day + hour, day + hour + 30 * min), Interval(day + 5 * hour, day + 5 * hour + 15 * min)), intervals)
    }

    @Test
    fun nestedActivityResumesDoNotCloseEarly() {
        val events = listOf(
            fg(day, "a"),
            fg(day + 5 * min, "a"),
            bg(day + 6 * min, "a"),
            bg(day + 10 * min, "a"),
        )
        assertEquals(listOf(Interval(day, day + 10 * min)), ScreenIntervals.reconstruct(events, day + hour))
    }

    @Test
    fun screenOffClosesEverything() {
        val events = listOf(fg(day, "a"), fg(day + min, "b"), off(day + 3 * min), bg(day + 9 * min, "a"))
        assertEquals(listOf(Interval(day, day + 3 * min)), ScreenIntervals.reconstruct(events, day + hour))
    }

    @Test
    fun summarizeClipsToDayAndCountsLateMinutes() {
        val intervals = listOf(
            Interval(day + 9 * hour, day + 9 * hour + 30 * min),
            Interval(day + 20 * hour + 50 * min, day + 21 * hour + 25 * min),
            Interval(day + 23 * hour + 30 * min, day + 24 * hour + 40 * min),
            Interval(day + 26 * hour, day + 26 * hour + 10 * min),
            Interval(day + 29 * hour, day + 30 * hour),
        )
        val s = ScreenIntervals.summarize(intervals, day)
        assertEquals(30.0 + 35.0 + 30.0, s.totalMin)
        assertEquals(25.0 + 30.0, s.after21Min)
        assertEquals(day + 26 * hour + 10 * min, s.lastUseUtcMs)
    }

    @Test
    fun summarizeWithNothingYieldsZeros() {
        val s = ScreenIntervals.summarize(emptyList(), day)
        assertEquals(0.0, s.totalMin)
        assertEquals(0.0, s.after21Min)
        assertNull(s.lastUseUtcMs)
    }

    @Test
    fun lastUseIsClippedAtFourInTheMorning() {
        val s = ScreenIntervals.summarize(listOf(Interval(day + 27 * hour, day + 29 * hour)), day)
        assertEquals(day + 28 * hour, s.lastUseUtcMs)
    }
}
