package com.lifepowers.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

class PatternsTest {
    private val start = LocalDate.of(2026, 6, 1)

    @Test fun statsHelpers() {
        assertEquals(1.0, Patterns.spearman(listOf(1.0, 2.0, 3.0, 4.0), listOf(10.0, 20.0, 25.0, 40.0)), 1e-9)
        assertEquals(1.0, Patterns.pFromT(0.0, 10.0), 1e-9)
        assertTrue(Patterns.pFromT(3.0, 30.0) < 0.01)
        assertEquals(listOf(0.03, 0.03, 0.04), Patterns.bh(listOf(0.01, 0.02, 0.04)).map { Math.round(it * 100) / 100.0 })
    }

    @Test fun findsAlcoholLoweringNextNightHrvAndIgnoresNoise() {
        val rnd = Random(7)
        val alcohol = (0 until 45).associate { start.plusDays(it.toLong()) to if (it % 3 == 0) 1.0 else 0.0 }
        val hrv = (0 until 46).associate { d ->
            val drank = alcohol[start.plusDays(d - 1L)] == 1.0
            start.plusDays(d.toLong()) to 45.0 + rnd.nextDouble(-4.0, 4.0) - if (drank) 9.0 else 0.0
        }
        val noise = (0 until 45).associate { start.plusDays(it.toLong()) to rnd.nextDouble(0.0, 100.0) }
        val found = Patterns.run(
            inputs = listOf(DaySeries("alcohol", alcohol, binary = true), DaySeries("screen.total_min", noise)),
            outcomes = listOf(DaySeries(Metrics.HRV_NIGHT_AVG, hrv, higherIsBetter = true)),
        )
        val ins = found.single()
        assertEquals("alcohol", ins.input)
        assertEquals(1, ins.lag)
        assertEquals(InsightKind.AFTER_BEHAVIOR, ins.kind)
        assertEquals(false, ins.good)
        assertTrue(ins.effect < 0)
        val text = Patterns.sentence(ins) { if (it == "alcohol") "alcohol" else "overnight HRV" }
        assertTrue(text.startsWith("Your overnight HRV tends to run lower the day after alcohol"))
    }

    @Test fun tooFewDaysSaysNothing() {
        val x = (0 until 10).associate { start.plusDays(it.toLong()) to it.toDouble() }
        assertTrue(Patterns.run(listOf(DaySeries("steps", x)), listOf(DaySeries("hrv.night_avg", x))).isEmpty())
    }

    @Test fun tarotCounts() {
        val pulls = listOf(Tarot.Pull("The Moon", false, "luteal", Tier.GOOD), Tarot.Pull("The Moon", true, "luteal", null), Tarot.Pull("The Star", false, null, null))
        val c = Tarot.counts(pulls)
        assertEquals("The Moon", c.first().card)
        assertEquals(2, c.first().byPhase["luteal"])
        assertEquals(78, Tarot.DECK.size)
        assertEquals("Queen of Cups", Tarot.search("queen of c").first())
    }
}
