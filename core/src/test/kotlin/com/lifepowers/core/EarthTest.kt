package com.lifepowers.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EarthTest {
    private val portland = GeoPoint(45.52, -122.68)

    @Test fun distancesAndRing() {
        assertEquals(80.0, Earth.distanceKm(portland, GeoPoint(46.20, -122.18)), 10.0)
        assertTrue(Earth.onRingOfFire(GeoPoint(35.0, 139.0)))
        assertFalse(Earth.onRingOfFire(GeoPoint(48.85, 2.35)))
        assertEquals("Sakurajima", Earth.findVolcano("Sakurajima")?.name)
        assertEquals(GeoPoint(45.5, -122.7), portland.rounded())
    }

    @Test fun usgsFeedFiltersByRingAndRadius() {
        val body = """{"type":"FeatureCollection","features":[
          {"type":"Feature","id":"a1","properties":{"mag":4.6,"place":"off the coast of Oregon","time":1760000000000,"url":"u"},"geometry":{"type":"Point","coordinates":[-125.5,44.6,10.0]}},
          {"type":"Feature","id":"a2","properties":{"mag":5.1,"place":"central France","time":1760000000000},"geometry":{"type":"Point","coordinates":[2.3,46.0,5.0]}},
          {"type":"Feature","id":"a3","properties":{"mag":2.8,"place":"near Portland","time":1760000000000},"geometry":{"type":"Point","coordinates":[-122.6,45.4,12.0]}},
          {"type":"Feature","id":"a4","properties":{"mag":null,"place":"x","time":1},"geometry":{"type":"Point","coordinates":[0,0,0]}}
        ]}"""
        val quakes = Earth.parseUsgs(body)
        assertEquals(3, quakes.size)
        val kept = Earth.filterQuakes(quakes, portland, 500.0)
        assertEquals(listOf("a1", "a3"), kept.map { it.id })
        assertTrue(Earth.shouldNotify(kept.first(), 4.5, 500.0))
        assertFalse(Earth.shouldNotify(kept.last(), 4.5, 500.0))
        assertTrue(Earth.describe(kept.first()).contains("km from you"))
    }

    @Test fun gvpAndKpParse() {
        val rss = """<rss><channel><item><title>Sakurajima (Japan) - Report for 7 October-13 October 2026</title>
            <link>https://volcano.si.edu/x</link><description><![CDATA[<p>Eruptive activity continued.</p>]]></description>
            <pubDate>Wed, 14 Oct 2026 12:00:00 +0000</pubDate></item></channel></rss>"""
        val reports = Earth.parseGvpRss(rss)
        assertEquals("Sakurajima", reports.single().name)
        assertEquals("Japan", reports.single().country)
        assertEquals("Eruptive activity continued.", reports.single().summary)
        assertTrue(Earth.tagVolcanoReports(reports, portland).single().ringOfFire)

        val table = """[["time_tag","Kp","observed","noaa_scale"],["2026-10-16 00:00:00","2.33","observed",null],["2026-10-17 03:00:00","5.67","predicted","G2"]]"""
        val kp = Earth.parseKp(table)
        assertEquals(2, kp.size)
        assertTrue(kp.last().predicted)
        assertEquals(5.67, kp.last().kp, 1e-9)
        val objects = """[{"time_tag":"2026-10-16T00:00:00","kp_index":3,"estimated_kp":3.33}]"""
        assertEquals(1, Earth.parseKp(objects).size)
        assertEquals(AuroraChance.UNLIKELY, Earth.auroraChance(2.0, portland))
        assertTrue(Earth.auroraChance(8.0, portland) != AuroraChance.UNLIKELY)
        assertTrue(Earth.kpNeeded(portland) in 3.0..7.0)
    }

    @Test fun openMeteoDailyRollup() {
        val hours = (0 until 48).map { "\"2026-10-${15 + it / 24}T${"%02d".format(it % 24)}:00\"" }
        val pressure = (0 until 48).map { if (it < 24) 1015.0 else 1015.0 - (it - 23) * 0.4 }
        val body = """{"utc_offset_seconds":-25200,
          "hourly":{"time":[${hours.joinToString(",")}],"surface_pressure":[${pressure.joinToString(",")}],"relative_humidity_2m":[${(0 until 48).joinToString(",") { "80" }}]},
          "daily":{"time":["2026-10-15","2026-10-16"],"temperature_2m_max":[14.2,12.0],"temperature_2m_min":[8.0,7.5],"daylight_duration":[39600.0,39420.0],"uv_index_max":[3.1,2.0]}}"""
        assertEquals(-420, OpenMeteo.utcOffsetMin(body))
        val days = OpenMeteo.parseWeather(body)
        assertEquals(2, days.size)
        assertEquals(-9.6, days[1].pressureChange24h!!, 1e-6)
        assertEquals(660.0, days[0].daylightMin!!, 1e-6)
        val samples = OpenMeteo.toSamples(days, -420, LocalDate.of(2026, 10, 15))
        assertTrue(samples.none { it.metric == Metrics.WEATHER_PRESSURE_SWING && it.value > 9.0 })
        assertTrue(samples.any { it.metric == Metrics.WEATHER_DAYLIGHT_MIN })
        assertTrue(OpenMeteo.forecastUrl(portland).contains("latitude=45.5&longitude=-122.7"))
    }
}
