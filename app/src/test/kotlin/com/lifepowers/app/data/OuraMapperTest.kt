package com.lifepowers.app.data

import com.lifepowers.app.connectors.OuraMapper
import com.lifepowers.app.domain.EventKinds
import com.lifepowers.core.Metrics
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OuraMapperTest {
    private val zone = 120 // UTC+2

    @Test
    fun dailySleepScoreLandsOnLocalMidnight() {
        val body = """{"data":[{"id":"x","day":"2026-10-14","score":81,"contributors":{"deep_sleep":90}}],"next_token":null}"""
        val samples = OuraMapper.dailySleep(body, zone)
        assertEquals(1, samples.size)
        val s = samples.single()
        assertEquals(Metrics.SCORE_SLEEP, s.metric)
        assertEquals(81.0, s.value)
        assertEquals(LocalDate.of(2026, 10, 14).atStartOfDay().toInstant(ZoneOffset.ofHours(2)).toEpochMilli(), s.startUtcMs)
        assertEquals(zone, s.zoneOffsetMin)
        assertEquals("oura_api", s.source)
        assertNull(s.endUtcMs)
    }

    @Test
    fun missingScoreIsSkipped() {
        val body = """{"data":[{"day":"2026-10-14","score":null},{"day":"2026-10-15","score":70}]}"""
        val samples = OuraMapper.dailyReadiness(body, zone)
        assertEquals(listOf(70.0), samples.map { it.value })
        assertEquals(Metrics.SCORE_READINESS, samples.single().metric)
    }

    @Test
    fun sleepPeriodMapsDurationsAndTimes() {
        val body = """{"data":[{
            "day":"2026-10-15","type":"long_sleep",
            "bedtime_start":"2026-10-14T23:30:00+02:00","bedtime_end":"2026-10-15T07:00:00+02:00",
            "total_sleep_duration":25200,"deep_sleep_duration":5400,"rem_sleep_duration":6000,
            "light_sleep_duration":13800,"awake_time":1800,"latency":600,"efficiency":93,
            "average_hrv":48.5,"lowest_heart_rate":52,"average_breath":14.2
        },{
            "day":"2026-10-15","type":"sleep",
            "bedtime_start":"2026-10-15T14:00:00+02:00","bedtime_end":"2026-10-15T14:30:00+02:00",
            "total_sleep_duration":1800
        }]}"""
        val samples = OuraMapper.sleep(body, zone)
        val byMetric = samples.associate { it.metric to it.value }
        assertEquals(12, samples.size)
        assertEquals(420.0, byMetric[Metrics.SLEEP_TOTAL_MIN])
        assertEquals(90.0, byMetric[Metrics.SLEEP_DEEP_MIN])
        assertEquals(100.0, byMetric[Metrics.SLEEP_REM_MIN])
        assertEquals(230.0, byMetric[Metrics.SLEEP_LIGHT_MIN])
        assertEquals(30.0, byMetric[Metrics.SLEEP_AWAKE_MIN])
        assertEquals(10.0, byMetric[Metrics.SLEEP_ONSET_LATENCY_MIN])
        assertEquals(0.93, byMetric[Metrics.SLEEP_EFFICIENCY])
        assertEquals(48.5, byMetric[Metrics.HRV_NIGHT_AVG])
        assertEquals(52.0, byMetric[Metrics.HR_RESTING])
        assertEquals(14.2, byMetric[Metrics.RESP_RATE_NIGHT])

        val start = java.time.OffsetDateTime.parse("2026-10-14T23:30:00+02:00").toInstant().toEpochMilli()
        val end = java.time.OffsetDateTime.parse("2026-10-15T07:00:00+02:00").toInstant().toEpochMilli()
        assertEquals(start.toDouble(), byMetric[Metrics.SLEEP_BEDTIME_UTC])
        assertEquals(end.toDouble(), byMetric[Metrics.SLEEP_WAKE_UTC])
        assertTrue(samples.all { it.startUtcMs == start && it.endUtcMs == end && it.zoneOffsetMin == 120 })
    }

    @Test
    fun dailyActivityAndStress() {
        val activity = """{"data":[{"day":"2026-10-14","score":77,"steps":8421,"active_calories":512}]}"""
        val a = OuraMapper.dailyActivity(activity, zone).associate { it.metric to it.value }
        assertEquals(8421.0, a[Metrics.ACTIVITY_STEPS])
        assertEquals(512.0, a[Metrics.ACTIVITY_ACTIVE_KCAL])
        assertEquals(77.0, a[Metrics.SCORE_ACTIVITY])

        val stress = """{"data":[{"day":"2026-10-14","stress_high":5400,"recovery_high":3600,"day_summary":"normal"}]}"""
        val s = OuraMapper.dailyStress(stress, zone).single()
        assertEquals(Metrics.STRESS_DAY_HIGH_MIN, s.metric)
        assertEquals(90.0, s.value)
    }

    @Test
    fun tagsMapKnownCodesAndKeepUnknownOnes() {
        val body = """{"data":[
            {"tag_type_code":"tag_generic_alcohol","start_time":"2026-10-14T20:15:00+02:00","end_time":null,"start_day":"2026-10-14","comment":"two glasses"},
            {"tag_type_code":"tag_generic_meditation","start_time":null,"start_day":"2026-10-14","comment":null,"custom_name":null}
        ],"next_token":"abc"}"""
        val events = OuraMapper.tags(body, zone)
        assertEquals(2, events.size)
        val alcohol = events[0]
        assertEquals(EventKinds.ALCOHOL, alcohol.kind)
        assertEquals("two glasses", alcohol.note)
        assertEquals(java.time.OffsetDateTime.parse("2026-10-14T20:15:00+02:00").toInstant().toEpochMilli(), alcohol.startUtcMs)
        val other = events[1]
        assertEquals("oura_tag", other.kind)
        assertEquals("tag_generic_meditation", other.note)
        assertEquals(OuraMapper.dayStart("2026-10-14", zone), other.startUtcMs)
        assertEquals("abc", OuraMapper.nextToken(body))
    }

    @Test
    fun unknownKeysAndEmptyPagesAreTolerated() {
        assertTrue(OuraMapper.dailySleep("""{"data":[],"next_token":null,"extra":{"a":1}}""", zone).isEmpty())
        assertNull(OuraMapper.nextToken("not json"))
    }
}
