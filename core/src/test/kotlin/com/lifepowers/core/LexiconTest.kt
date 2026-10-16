package com.lifepowers.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LexiconTest {
    @Test fun fineButBusyReadsNeutralToPositive() {
        val s = Lexicon.score("Honestly fine. Slept okay I think. Busy day ahead, kind of a lot, but fine.")
        assertTrue(s.valence >= 0.5)
        assertTrue(s.selfFocus >= 0.0)
    }

    @Test fun worriedTextRaisesAnxiety() {
        val s = Lexicon.score("I'm worried and on edge, what if it never gets better")
        assertTrue(s.anxietyMarkers > 0.4)
        assertTrue(s.valence <= 0.5)
    }

    @Test fun energySelfReportParses() {
        assertEquals(6.0, Lexicon.energySelfReport("energy is maybe a six today"))
        assertEquals(7.0, Lexicon.energySelfReport("my energy 7 out of 10"))
        assertEquals(null, Lexicon.energySelfReport("no mention"))
    }
}
