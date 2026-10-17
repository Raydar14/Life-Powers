package com.lifepowers.app.world

import com.lifepowers.app.domain.DailyMetricPoint
import com.lifepowers.core.Agg
import com.lifepowers.core.AuroraChance
import com.lifepowers.core.EclipseEvent
import com.lifepowers.core.KpReading
import com.lifepowers.core.MeteorShower
import com.lifepowers.core.Pairing
import com.lifepowers.core.ShowerNight
import com.lifepowers.core.SkyWindow
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorldMathTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val day = LocalDate.of(2026, 10, 18)
    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Long = date.atTime(hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun chunksCoverTheRangeWithoutOverlap() {
        val from = LocalDate.of(2025, 10, 18)
        val to = LocalDate.of(2026, 10, 12)
        val chunks = WorldMath.chunks(from, to, 90)
        assertEquals(from, chunks.first().first)
        assertEquals(to, chunks.last().second)
        chunks.zipWithNext().forEach { (a, b) -> assertEquals(a.second.plusDays(1), b.first) }
        chunks.forEach { (a, b) -> assertTrue(b.toEpochDay() - a.toEpochDay() + 1 <= 90) }
        assertEquals(listOf(day to day), WorldMath.chunks(day, day, 90))
        assertTrue(WorldMath.chunks(day, day.minusDays(1), 90).isEmpty())
    }

    @Test
    fun mergeKpPrefersObservationsAndLaterLists() {
        val t = at(day, 0)
        val old = listOf(KpReading(t, 2.0, false), KpReading(t + 3 * WorldMath.HOUR, 3.0, true), KpReading(t - 20 * WorldMath.DAY, 5.0, false))
        val forecast = listOf(KpReading(t, 6.0, true), KpReading(t + 3 * WorldMath.HOUR, 4.0, true))
        val observed = listOf(KpReading(t + 3 * WorldMath.HOUR, 3.67, false))
        val merged = WorldMath.mergeKp(listOf(old, forecast, observed), t - WorldMath.DAY)
        assertEquals(listOf(KpReading(t, 2.0, false), KpReading(t + 3 * WorldMath.HOUR, 3.67, false)), merged)
    }

    @Test
    fun dailyKpMaxNeedsACompletedWellCoveredDay() {
        val yesterday = day.minusDays(1)
        val full = (0 until 8).map { KpReading(at(yesterday, it * 3), 1.0 + it, false) }
        val partial = (0 until 3).map { KpReading(at(yesterday.minusDays(1), 18 + it * 2), 5.0, false) }
        val todays = (0 until 8).map { KpReading(at(day, it * 3), 7.0, false) }
        val predicted = (0 until 8).map { KpReading(at(yesterday.minusDays(2), it * 3), 6.0, true) }
        assertEquals(mapOf(yesterday to 8.0), WorldMath.dailyKpMax(full + partial + todays + predicted, zone, day))
    }

    @Test
    fun nightMaxCoversEveningToMorning() {
        val readings = listOf(
            KpReading(at(day, 12), 7.0, false),
            KpReading(at(day, 15), 3.0, false),
            KpReading(at(day, 21), 4.33, true),
            KpReading(at(day.plusDays(1), 3), 5.0, true),
            KpReading(at(day.plusDays(1), 6), 8.0, true),
        )
        assertEquals(5.0, WorldMath.nightMaxKp(readings, day, zone))
        assertNull(WorldMath.nightMaxKp(readings, day.plusDays(3), zone))
        assertEquals(3.0, WorldMath.kpNow(readings, at(day, 22)))
    }

    @Test
    fun eveningDateAndNextEvening() {
        val early = ZonedDateTime.of(2026, 10, 18, 3, 0, 0, 0, zone)
        assertEquals(day.minusDays(1), WorldMath.eveningDateFor(early))
        assertEquals(day, WorldMath.eveningDateFor(early.withHour(7)))

        val afternoon = ZonedDateTime.of(2026, 10, 18, 15, 0, 0, 0, zone)
        assertEquals(afternoon.withHour(19), WorldMath.nextEvening(afternoon, 19))
        val justBefore = ZonedDateTime.of(2026, 10, 18, 18, 30, 0, 0, zone)
        assertEquals(justBefore.withHour(19).withMinute(0), WorldMath.nextEvening(justBefore, 19, marginMin = 1))
        assertEquals(justBefore.plusDays(1).withHour(19).withMinute(0), WorldMath.nextEvening(justBefore, 19, marginMin = 60))
        val after = ZonedDateTime.of(2026, 10, 18, 19, 0, 5, 0, zone)
        assertEquals(after.plusDays(1).withHour(19).withMinute(0).withSecond(0), WorldMath.nextEvening(after, 19))
    }

    @Test
    fun earliestPersonalDateIgnoresWorldMetrics() {
        fun p(d: LocalDate, metric: String) = DailyMetricPoint(d, metric, Agg.LAST, 1.0, 1, null)
        val points = listOf(
            p(day.minusDays(300), "moon.illumination"),
            p(day.minusDays(200), "weather.pressure_mean"),
            p(day.minusDays(40), "sleep.total_min"),
            p(day.minusDays(10), "activity.steps"),
        )
        assertEquals(day.minusDays(40), WorldMath.earliestPersonalDate(points))
        assertNull(WorldMath.earliestPersonalDate(points.take(2)))
    }

    @Test
    fun notableNightRules() {
        val shower = MeteorShower("Orionids", 10, 18, 20, 16.0)
        fun reasons(
            showers: List<ShowerNight> = emptyList(),
            eclipses: List<EclipseEvent> = emptyList(),
            aurora: AuroraChance? = null,
            pairings: List<Pairing> = emptyList(),
        ) = WorldMath.notableReasons(day, zone, showers, eclipses, aurora, pairings)

        assertTrue(reasons().isEmpty())
        assertEquals(listOf("Orionids peak tonight"), reasons(showers = listOf(ShowerNight(shower, day, 0.3, null, null))))
        assertEquals(listOf("Orionids peak tonight"), reasons(showers = listOf(ShowerNight(shower, day, 0.9, at(day.plusDays(1), 0, 30), true))))
        assertTrue(reasons(showers = listOf(ShowerNight(shower, day, 0.9, at(day.plusDays(1), 2), true))).isEmpty())
        assertTrue(reasons(showers = listOf(ShowerNight(shower, day, 0.1, null, false))).isEmpty())

        val peak = at(day, 22)
        assertEquals(listOf("partial lunar eclipse"), reasons(eclipses = listOf(EclipseEvent(false, "partial", peak, peak, peak, true, 0.5))))
        assertTrue(reasons(eclipses = listOf(EclipseEvent(false, "partial", peak, peak, peak, null, 0.5))).isEmpty())
        val far = peak + 3 * WorldMath.DAY
        assertTrue(reasons(eclipses = listOf(EclipseEvent(false, "total", far, far, far, true, 1.0))).isEmpty())

        assertEquals(listOf("aurora possible"), reasons(aurora = AuroraChance.POSSIBLE))
        assertTrue(reasons(aurora = AuroraChance.UNLIKELY).isEmpty())

        val close = Pairing("Moon", "Venus", 2.0, SkyWindow.EVENING, peak)
        val wide = Pairing("Mars", "Saturn", 3.5, SkyWindow.MORNING, peak)
        assertEquals(listOf("Venus and the moon close together"), reasons(pairings = listOf(close, close.copy(window = SkyWindow.MORNING), wide)))
    }
}
