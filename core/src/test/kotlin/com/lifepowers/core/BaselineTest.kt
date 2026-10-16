package com.lifepowers.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BaselineTest {
    @Test fun medianOddAndEven() {
        assertEquals(3.0, Baseline.median(listOf(5.0, 1.0, 3.0)))
        assertEquals(2.5, Baseline.median(listOf(4.0, 1.0, 2.0, 3.0)))
    }

    @Test fun zNeedsTenValues() {
        assertNull(Baseline.z(5.0, List(9) { it.toDouble() }))
        assertTrue(Baseline.z(20.0, List(10) { it.toDouble() })!! > 0)
    }

    @Test fun zIsRobustToOutlier() {
        val window = List(29) { 50.0 + (it % 5) } + listOf(500.0)
        val z = Baseline.z(60.0, window)!!
        assertTrue(z in 1.0..6.0, "z was $z")
    }

    @Test fun trailingZUsesOnlyPast() {
        val series = List(40) { 10.0 + (it % 7) }
        val zs = Baseline.trailingZ(series)
        assertNull(zs[0])
        assertNull(zs[5])
        assertTrue(zs[35] != null)
    }
}
