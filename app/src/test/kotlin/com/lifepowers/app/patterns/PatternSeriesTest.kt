package com.lifepowers.app.patterns

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PatternSeriesTest {
    private val today = LocalDate.of(2026, 10, 14)

    @Test
    fun rangeIsInclusive() {
        assertEquals(3, PatternSeries.range(today.minusDays(2), today).size)
        assertTrue(PatternSeries.range(today, today.minusDays(1)).isEmpty())
    }

    @Test
    fun coveredRangeStartsAtFirstValue() {
        val a = mapOf(today.minusDays(3) to 1.0)
        val b = mapOf(today.minusDays(10) to 2.0, today.plusDays(2) to 5.0)
        val range = PatternSeries.coveredRange(listOf(a, b), today)
        assertEquals(today.minusDays(10), range.first())
        assertEquals(today, range.last())
        assertTrue(PatternSeries.coveredRange(listOf(emptyMap()), today).isEmpty())
    }

    @Test
    fun binaryNeedsFiveOnDays() {
        val range = PatternSeries.range(today.minusDays(29), today)
        val four = (0L..3L).map { today.minusDays(it * 3) }.toSet()
        assertNull(PatternSeries.binary("alcohol", four, range))

        val five = four + today.minusDays(20)
        val s = assertNotNull(PatternSeries.binary("alcohol", five, range, higherIsBetter = false))
        assertTrue(s.binary)
        assertEquals(30, s.values.size)
        assertEquals(5, s.values.values.count { it == 1.0 })
        assertEquals(false, s.higherIsBetter)
    }

    @Test
    fun binaryIgnoresDaysOutsideRange() {
        val range = PatternSeries.range(today.minusDays(9), today)
        val days = (0L..4L).map { today.minusDays(20 + it) }.toSet()
        assertNull(PatternSeries.binary("binge", days, range))
    }
}
