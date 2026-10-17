package com.lifepowers.core

import kotlin.test.Test
import kotlin.test.assertEquals

class SymptomParserTest {
    @Test fun poundingHeadAndSoBloated() {
        val r = SymptomParser.parse("My head is pounding and I am so bloated today.").associate { it.kind to it.severity }
        assertEquals(5, r["headache"])
        assertEquals(4, r["bloating"])
    }

    @Test fun mildWordsLowerSeverity() {
        val r = SymptomParser.parse("a little foggy, slight cramps").associate { it.kind to it.severity }
        assertEquals(1, r["brain_fog"])
        assertEquals(1, r["cramps"])
    }

    @Test fun noQualifierIsThree() {
        assertEquals(3, SymptomParser.parse("headache").single().severity)
    }
}
