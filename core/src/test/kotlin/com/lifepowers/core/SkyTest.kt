package com.lifepowers.core

import io.github.cosinekitty.astronomy.Body
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.math.abs

class SkyTest {
    private val portland = GeoPoint(45.52, -122.68)
    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test fun fullMoonIsLitAndNamed() {
        // Full moon of 2024-04-23 23:49 UTC.
        val m = Sky.moon(ms("2024-04-23T23:49:00Z"), portland)
        assertEquals(MoonPhaseName.FULL, m.name)
        assertTrue(m.illumination > 0.98)
        assertEquals(MoonPhaseName.NEW, Sky.phaseName(5.0))
        assertEquals(MoonPhaseName.WAXING_GIBBOUS, Sky.phaseName(140.0))
        assertEquals("Aries", Sky.sign(10.0))
        assertEquals("Pisces", Sky.sign(359.0))
    }

    @Test fun mercuryRetrogradeOfApril2024() {
        // Mercury stationed retrograde on 2024-04-01 and direct on 2024-04-25.
        val periods = Sky.retrogrades(Body.Mercury, ms("2024-03-15T00:00:00Z"), ms("2024-05-15T00:00:00Z"))
        val april = periods.single { it.startUtcMs in ms("2024-03-25T00:00:00Z")..ms("2024-04-10T00:00:00Z") }
        assertTrue(abs(april.startUtcMs - ms("2024-04-01T22:14:00Z")) < 2 * Sky.MS_PER_DAY)
        assertTrue(abs(april.endUtcMs - ms("2024-04-25T12:54:00Z")) < 2 * Sky.MS_PER_DAY)
        val status = Sky.retrogradeNow(ms("2024-04-12T00:00:00Z"), listOf(Body.Mercury))
        assertEquals(1, status.size)
        assertFalse(status.first().stationing)
        assertTrue(Sky.retrogradeNow(april.startUtcMs + 3_600_000L, listOf(Body.Mercury)).first().stationing)
    }

    @Test fun skyTierFollowsTransitsAndEvents() {
        val retro = Sky.RetrogradeStatus(RetrogradePeriod("Mercury", 0, 10), stationing = true)
        assertEquals(Tier.LIKELY_HARD, Sky.tier(true, listOf(retro), false).first)
        assertEquals(Tier.NOT_GREAT, Sky.tier(true, listOf(retro.copy(stationing = false)), true).first)
        assertEquals(Tier.BEST, Sky.tier(false, listOf(retro), true).first)
        assertEquals(Tier.GOOD, Sky.tier(false, emptyList(), false).first)
    }

    @Test fun eclipsesShowersAndPlanets() {
        // Total lunar eclipse of 2025-03-14, peak near 06:59 UTC, visible from the Americas.
        val e = Sky.eclipses(ms("2025-03-01T00:00:00Z"), 30, portland).first { !it.solar }
        assertEquals("total", e.kind)
        assertTrue(abs(e.peakUtcMs - ms("2025-03-14T06:59:00Z")) < 30 * 60_000L)
        assertEquals(true, e.visibleHere)
        val showers = Sky.showers(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), -420, portland)
        assertEquals("Perseids", showers.single().shower.name)
        assertEquals(true, showers.single().visibleHere)
        val sightings = Sky.visiblePlanets(LocalDate.of(2026, 10, 16), -420, portland)
        assertTrue(sightings.all { it.altitudeDeg >= 8.0 })
    }
}
