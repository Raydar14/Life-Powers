package com.lifepowers.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StreamCursorsTest {
    private val streams = listOf("sleep", "daily_activity", "daily_stress")

    @Test fun failedStreamKeepsItsCursor() {
        val prev = StreamCursors.decode("sleep=2026-10-01;daily_activity=2026-10-01;daily_stress=2026-10-01")
        val next = StreamCursors.advance(prev, streams, setOf("sleep", "daily_stress"), "2026-10-15")
        val d = StreamCursors.decode(next)
        assertEquals("2026-10-15", d["sleep"])
        assertEquals("2026-10-01", d["daily_activity"])
        assertEquals("2026-10-15", d["daily_stress"])
    }

    @Test fun firstRunFailureLeavesStreamUnset() {
        val next = StreamCursors.advance(emptyMap(), streams, setOf("sleep"), "2026-10-15")
        val d = StreamCursors.decode(next)
        assertEquals("2026-10-15", d["sleep"])
        assertNull(StreamCursors.forStream(d, "daily_activity"))
    }

    @Test fun legacyCursorAppliesToEveryStream() {
        val d = StreamCursors.decode("2026-10-09T10:00:00Z")
        assertEquals("2026-10-09T10:00:00Z", StreamCursors.forStream(d, "anything"))
    }

    @Test fun instantsSurviveRoundTrip() {
        val enc = StreamCursors.advance(emptyMap(), listOf("StepsRecord"), setOf("StepsRecord"), "2026-10-16T23:59:01.123Z")
        assertEquals("2026-10-16T23:59:01.123Z", StreamCursors.decode(enc)["StepsRecord"])
    }
}
