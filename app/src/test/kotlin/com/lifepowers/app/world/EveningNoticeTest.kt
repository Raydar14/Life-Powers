package com.lifepowers.app.world

import com.lifepowers.app.domain.LocationMode
import com.lifepowers.app.domain.LocationSetting
import com.lifepowers.app.domain.SkyToday
import com.lifepowers.core.EclipseEvent
import com.lifepowers.core.GeoPoint
import com.lifepowers.core.MeteorShower
import com.lifepowers.core.MoonInfo
import com.lifepowers.core.MoonPhaseName
import com.lifepowers.core.Pairing
import com.lifepowers.core.ShowerNight
import com.lifepowers.core.SkyWindow
import com.lifepowers.core.Tier
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EveningNoticeTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val sunday = LocalDate.of(2026, 10, 18)
    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Long = date.atTime(hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val sevenPm = at(sunday, 19)

    private fun moon(nextFull: Long = at(sunday.plusDays(20), 12), supermoon: Boolean = false) = MoonInfo(
        phaseAngle = 300.0, illumination = 0.3, name = MoonPhaseName.WANING_CRESCENT, sign = "Leo", distanceKm = 380_000.0,
        nextNewUtcMs = at(sunday.plusDays(5), 0), nextFullUtcMs = nextFull, nextFullIsSupermoon = supermoon, riseUtcMs = null, setUtcMs = null,
    )

    private fun sky(
        date: LocalDate = sunday,
        showers: List<ShowerNight> = emptyList(),
        eclipses: List<EclipseEvent> = emptyList(),
        pairings: List<Pairing> = emptyList(),
        moon: MoonInfo = moon(),
    ) = SkyToday(
        date = date,
        location = LocationSetting(LocationMode.CITY, "Portland, Oregon", GeoPoint(45.5, -122.7)),
        moon = moon, retrogrades = emptyList(), planets = emptyList(), pairings = pairings, eclipses = eclipses, showers = showers,
        kpNow = null, kpTonightMax = null, aurora = null, kpNeeded = null, weather = null, tier = Tier.GOOD, tierReasons = emptyList(),
        transitsOn = false,
    )

    private val orionids = MeteorShower("Orionids", 10, 18, 20, 16.0)

    @Test
    fun quietEveningSaysNothing() {
        assertNull(EveningNotice.compose(sky(), sky(sunday.plusDays(1)), zone, sevenPm))
    }

    @Test
    fun showerWithEarlyMoonsetPromisesDarkSkies() {
        val s = ShowerNight(orionids, sunday, 0.6, at(sunday, 23), true)
        assertEquals(
            "Orionids peak tonight, best after 1 am. The moon sets at 11 pm, so skies will be dark.",
            EveningNotice.compose(sky(showers = listOf(s)), null, zone, sevenPm),
        )
    }

    @Test
    fun showerUnderLateMoonWaitsForMoonset() {
        val s = ShowerNight(orionids, sunday, 0.7, at(sunday.plusDays(1), 3, 20), true)
        assertEquals("Orionids peak tonight, best after 3:20 am, once the moon sets.", EveningNotice.compose(sky(showers = listOf(s)), null, zone, sevenPm))
    }

    @Test
    fun showerNotVisibleHereOrOnAnotherNightIsSkipped() {
        val hidden = ShowerNight(orionids, sunday, 0.1, null, false)
        val later = ShowerNight(orionids, sunday.plusDays(3), 0.1, null, true)
        assertNull(EveningNotice.compose(sky(showers = listOf(hidden, later)), null, zone, sevenPm))
    }

    @Test
    fun eclipseTwoDaysOutIsNamedByWeekday() {
        val start = at(sunday.plusDays(2), 21, 14)
        val e = EclipseEvent(false, "partial", start + 3_600_000, start, start + 7_200_000, true, 0.4)
        assertEquals(
            "Partial lunar eclipse Tuesday, starts 9:14 pm from where you are.",
            EveningNotice.compose(sky(eclipses = listOf(e)), null, zone, sevenPm),
        )
    }

    @Test
    fun eclipseAfterMidnightBelongsToTonight() {
        val start = at(sunday.plusDays(1), 1, 30)
        val e = EclipseEvent(false, "total", start + 3_600_000, start, start + 7_200_000, true, 1.0)
        assertEquals("Total lunar eclipse tonight, starts 1:30 am from where you are.", EveningNotice.compose(sky(eclipses = listOf(e)), null, zone, sevenPm))
    }

    @Test
    fun penumbralFarOrHiddenEclipsesAreSkipped() {
        val s = at(sunday, 21)
        val list = listOf(
            EclipseEvent(false, "penumbral", s, s, s + 3_600_000, true, 0.0),
            EclipseEvent(false, "partial", s, s, s + 3_600_000, false, 0.3),
            EclipseEvent(false, "partial", s + 10 * 86_400_000L, s + 10 * 86_400_000L, s + 10 * 86_400_000L + 3_600_000, true, 0.3),
        )
        assertNull(EveningNotice.compose(sky(eclipses = list), null, zone, sevenPm))
    }

    @Test
    fun pairingsAtDuskAndBeforeDawnTomorrow() {
        val today = sky(pairings = listOf(Pairing("Venus", "Jupiter", 1.2, SkyWindow.EVENING, at(sunday, 19)), Pairing("Mars", "Saturn", 1.0, SkyWindow.MORNING, at(sunday, 6))))
        val tomorrow = sky(sunday.plusDays(1), pairings = listOf(Pairing("Moon", "Mars", 2.5, SkyWindow.MORNING, at(sunday.plusDays(1), 6))))
        assertEquals(
            "Venus and Jupiter close together at dusk. Mars and the moon close together before dawn.",
            EveningNotice.compose(today, tomorrow, zone, sevenPm),
        )
    }

    @Test
    fun widePairingsAreSkipped() {
        val today = sky(pairings = listOf(Pairing("Venus", "Jupiter", 3.6, SkyWindow.EVENING, at(sunday, 19))))
        assertNull(EveningNotice.compose(today, null, zone, sevenPm))
    }

    @Test
    fun supermoonTomorrowNight() {
        val m = moon(nextFull = at(sunday.plusDays(1), 22), supermoon = true)
        assertEquals("Supermoon tomorrow night.", EveningNotice.compose(sky(moon = m), null, zone, sevenPm))
        val early = moon(nextFull = at(sunday.plusDays(1), 3), supermoon = true)
        assertEquals("Supermoon tonight.", EveningNotice.compose(sky(moon = early), null, zone, sevenPm))
        val ordinary = moon(nextFull = at(sunday.plusDays(1), 22), supermoon = false)
        assertNull(EveningNotice.compose(sky(moon = ordinary), null, zone, sevenPm))
    }

    @Test
    fun clockUsesTwelveHourLowerCase() {
        assertEquals("12:05 am", EveningNotice.clock(at(sunday, 0, 5), zone))
        assertEquals("12 pm", EveningNotice.clock(at(sunday, 12), zone))
        assertEquals("9:14 pm", EveningNotice.clock(at(sunday, 21, 14), zone))
        assertEquals("11 pm", EveningNotice.clock(at(sunday, 23), zone))
        assertEquals("6:30 pm", EveningNotice.clock(at(sunday, 22, 30), ZoneOffset.ofHours(-4)))
    }
}
