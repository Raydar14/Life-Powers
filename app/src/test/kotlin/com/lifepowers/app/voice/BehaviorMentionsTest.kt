package com.lifepowers.app.voice

import com.lifepowers.app.domain.EventKinds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BehaviorMentionsTest {
    @Test fun detectsEachFamily() {
        val kinds = BehaviorMentions.detect("Two coffees, then a stressful busy day, scrolling in bed, and a beer.")
        assertTrue(EventKinds.CAFFEINE in kinds)
        assertTrue(EventKinds.STRESS_EVENT in kinds)
        assertTrue(BehaviorMentions.SCREENS_IN_BED in kinds)
        assertTrue(EventKinds.ALCOHOL in kinds)
    }

    @Test fun drinkingWaterIsNotAlcohol() {
        assertFalse(EventKinds.ALCOHOL in BehaviorMentions.detect("Trying to drink more water today."))
        assertTrue(EventKinds.ALCOHOL in BehaviorMentions.detect("Had a couple of drinks after dinner."))
    }

    @Test fun quantities() {
        assertEquals(2.0, BehaviorMentions.quantityFor(EventKinds.CAFFEINE, "Two coffees this morning."))
        assertEquals(3.0, BehaviorMentions.quantityFor(EventKinds.ALCOHOL, "I had 3 beers."))
        assertEquals(2.0, BehaviorMentions.quantityFor(EventKinds.ALCOHOL, "a couple of glasses of wine"))
        assertEquals(1.0, BehaviorMentions.quantityFor(EventKinds.CAFFEINE, "an espresso after lunch"))
        assertEquals(null, BehaviorMentions.quantityFor(EventKinds.CAFFEINE, "slept well"))
    }

    @Test fun crisisPhrasesWithCurlyApostrophe() {
        assertTrue(CrisisPhrases.matches("I don’t want to be here anymore"))
        assertFalse(CrisisPhrases.matches("I want to dye my hair"))
        assertFalse(CrisisPhrases.matches(null))
    }
}
