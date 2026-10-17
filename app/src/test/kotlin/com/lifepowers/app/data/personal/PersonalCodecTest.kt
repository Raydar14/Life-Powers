package com.lifepowers.app.data.personal

import java.time.DayOfWeek
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PersonalCodecTest {

    @Test
    fun timesRoundTripSortedAndPadded() {
        val s = PersonalCodec.encodeTimes(listOf(LocalTime.of(21, 5), LocalTime.of(7, 30), LocalTime.of(7, 30)))
        assertEquals("07:30,21:05", s)
        assertEquals(listOf(LocalTime.of(7, 30), LocalTime.of(21, 5)), PersonalCodec.decodeTimes(s))
    }

    @Test
    fun unreadableTimesAreSkipped() {
        assertEquals(listOf(LocalTime.of(8, 0)), PersonalCodec.decodeTimes(" 08:00 , 25:00, nope,,"))
        assertTrue(PersonalCodec.decodeTimes("").isEmpty())
        assertTrue(PersonalCodec.decodeTimes(null).isEmpty())
    }

    @Test
    fun daysUseIsoNumbers() {
        val s = PersonalCodec.encodeDays(setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
        assertEquals("1,3,7", s)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY), PersonalCodec.decodeDays(s))
        assertEquals(setOf(DayOfWeek.FRIDAY), PersonalCodec.decodeDays("5,0,8,x"))
    }

    @Test
    fun tagsDropCommasAndBlanks() {
        val s = PersonalCodec.encodeTags(listOf("garden", " ", "slow, quiet", "garden"))
        assertEquals("garden,slow  quiet", s)
        assertEquals(listOf("garden", "slow  quiet"), PersonalCodec.decodeTags(s))
    }
}
