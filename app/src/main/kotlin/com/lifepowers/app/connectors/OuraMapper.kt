package com.lifepowers.app.connectors

import com.lifepowers.app.domain.BehaviorEvent
import com.lifepowers.app.domain.EventKinds
import com.lifepowers.core.Metrics
import com.lifepowers.core.Sample
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

/*
 * Oura API v2 response shapes, limited to the fields the app reads. Unknown keys are ignored.
 * Every field is optional so a missing contributor never fails a whole page.
 */

@Serializable
data class OuraPage<T>(val data: List<T> = emptyList(), @SerialName("next_token") val nextToken: String? = null)

@Serializable
data class OuraDailyScore(val day: String? = null, val score: Int? = null)

@Serializable
data class OuraSleepPeriod(
    val day: String? = null,
    val type: String? = null,
    @SerialName("bedtime_start") val bedtimeStart: String? = null,
    @SerialName("bedtime_end") val bedtimeEnd: String? = null,
    @SerialName("total_sleep_duration") val totalSleepDuration: Int? = null,
    @SerialName("deep_sleep_duration") val deepSleepDuration: Int? = null,
    @SerialName("rem_sleep_duration") val remSleepDuration: Int? = null,
    @SerialName("light_sleep_duration") val lightSleepDuration: Int? = null,
    @SerialName("awake_time") val awakeTime: Int? = null,
    val latency: Int? = null,
    val efficiency: Int? = null,
    @SerialName("average_hrv") val averageHrv: Double? = null,
    @SerialName("lowest_heart_rate") val lowestHeartRate: Int? = null,
    @SerialName("average_breath") val averageBreath: Double? = null,
)

@Serializable
data class OuraDailyActivity(
    val day: String? = null,
    val score: Int? = null,
    val steps: Int? = null,
    @SerialName("active_calories") val activeCalories: Int? = null,
)

@Serializable
data class OuraDailyStress(
    val day: String? = null,
    @SerialName("stress_high") val stressHigh: Int? = null,
)

@Serializable
data class OuraTag(
    @SerialName("tag_type_code") val tagTypeCode: String? = null,
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("end_time") val endTime: String? = null,
    @SerialName("start_day") val startDay: String? = null,
    val comment: String? = null,
    @SerialName("custom_name") val customName: String? = null,
)

/** Pure mapping from Oura JSON pages to canonical samples and events. */
object OuraMapper {
    const val SOURCE = "oura_api"

    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private val tagKinds: Map<String, String> = mapOf(
        "tag_generic_alcohol" to EventKinds.ALCOHOL,
        "tag_generic_caffeine" to EventKinds.CAFFEINE,
        "tag_generic_sick" to EventKinds.ILLNESS,
        "tag_generic_cold" to EventKinds.ILLNESS,
        "tag_generic_flu" to EventKinds.ILLNESS,
        "tag_generic_stress" to EventKinds.STRESS_EVENT,
        "tag_generic_big_meal" to EventKinds.MEAL,
        "tag_generic_late_meal" to EventKinds.MEAL,
        "tag_generic_supplement" to EventKinds.SUPPLEMENT,
        "tag_generic_medication" to EventKinds.MEDICATION,
    )

    fun <T> page(body: String, item: KSerializer<T>): OuraPage<T> =
        json.decodeFromString(OuraPage.serializer(item), body)

    /** The pagination token of any page, whatever its item shape. Null on a malformed body. */
    fun nextToken(body: String): String? =
        runCatching { page(body, JsonElement.serializer()).nextToken }.getOrNull()

    fun dailySleep(body: String, zoneOffsetMin: Int): List<Sample> =
        page(body, OuraDailyScore.serializer()).data.mapNotNull { dayScore(it, Metrics.SCORE_SLEEP, zoneOffsetMin) }

    fun dailyReadiness(body: String, zoneOffsetMin: Int): List<Sample> =
        page(body, OuraDailyScore.serializer()).data.mapNotNull { dayScore(it, Metrics.SCORE_READINESS, zoneOffsetMin) }

    fun dailyActivity(body: String, zoneOffsetMin: Int): List<Sample> =
        page(body, OuraDailyActivity.serializer()).data.flatMap { row ->
            val start = dayStart(row.day, zoneOffsetMin) ?: return@flatMap emptyList()
            listOfNotNull(
                row.steps?.let { daySample(Metrics.ACTIVITY_STEPS, it.toDouble(), start, zoneOffsetMin) },
                row.activeCalories?.let { daySample(Metrics.ACTIVITY_ACTIVE_KCAL, it.toDouble(), start, zoneOffsetMin) },
                row.score?.let { daySample(Metrics.SCORE_ACTIVITY, it.toDouble(), start, zoneOffsetMin) },
            )
        }

    fun dailyStress(body: String, zoneOffsetMin: Int): List<Sample> =
        page(body, OuraDailyStress.serializer()).data.mapNotNull { row ->
            val start = dayStart(row.day, zoneOffsetMin) ?: return@mapNotNull null
            row.stressHigh?.let { daySample(Metrics.STRESS_DAY_HIGH_MIN, it / 60.0, start, zoneOffsetMin) }
        }

    /** Long sleep periods only. Naps are excluded from night metrics by the data model. */
    fun sleep(body: String, zoneOffsetMin: Int): List<Sample> =
        page(body, OuraSleepPeriod.serializer()).data
            .filter { it.type == null || it.type == "long_sleep" }
            .flatMap { p ->
                val start = parseInstant(p.bedtimeStart) ?: return@flatMap emptyList()
                val end = parseInstant(p.bedtimeEnd) ?: return@flatMap emptyList()
                val zone = parseOffsetMin(p.bedtimeStart) ?: zoneOffsetMin
                fun s(metric: String, value: Double?) =
                    value?.let { Sample(metric, it, start, end, zone, SOURCE) }
                listOfNotNull(
                    s(Metrics.SLEEP_TOTAL_MIN, p.totalSleepDuration?.let { it / 60.0 }),
                    s(Metrics.SLEEP_DEEP_MIN, p.deepSleepDuration?.let { it / 60.0 }),
                    s(Metrics.SLEEP_REM_MIN, p.remSleepDuration?.let { it / 60.0 }),
                    s(Metrics.SLEEP_LIGHT_MIN, p.lightSleepDuration?.let { it / 60.0 }),
                    s(Metrics.SLEEP_AWAKE_MIN, p.awakeTime?.let { it / 60.0 }),
                    s(Metrics.SLEEP_ONSET_LATENCY_MIN, p.latency?.let { it / 60.0 }),
                    s(Metrics.SLEEP_EFFICIENCY, p.efficiency?.let { it / 100.0 }),
                    s(Metrics.HRV_NIGHT_AVG, p.averageHrv),
                    s(Metrics.HR_RESTING, p.lowestHeartRate?.toDouble()),
                    s(Metrics.RESP_RATE_NIGHT, p.averageBreath),
                    s(Metrics.SLEEP_BEDTIME_UTC, start.toDouble()),
                    s(Metrics.SLEEP_WAKE_UTC, end.toDouble()),
                )
            }

    fun tags(body: String, zoneOffsetMin: Int): List<BehaviorEvent> =
        page(body, OuraTag.serializer()).data.mapNotNull { t ->
            val start = parseInstant(t.startTime) ?: dayStart(t.startDay, zoneOffsetMin) ?: return@mapNotNull null
            val zone = parseOffsetMin(t.startTime) ?: zoneOffsetMin
            val code = t.tagTypeCode?.lowercase()
            val kind = code?.let { tagKinds[it] }
            BehaviorEvent(
                kind = kind ?: "oura_tag",
                startUtcMs = start,
                endUtcMs = parseInstant(t.endTime),
                zoneOffsetMin = zone,
                note = if (kind == null) (t.customName ?: t.comment ?: code) else t.comment,
                source = SOURCE,
            )
        }

    private fun dayScore(row: OuraDailyScore, metric: String, zoneOffsetMin: Int): Sample? {
        val start = dayStart(row.day, zoneOffsetMin) ?: return null
        return row.score?.let { daySample(metric, it.toDouble(), start, zoneOffsetMin) }
    }

    private fun daySample(metric: String, value: Double, dayStartMs: Long, zoneOffsetMin: Int) =
        Sample(metric, value, dayStartMs, null, zoneOffsetMin, SOURCE)

    fun dayStart(day: String?, zoneOffsetMin: Int): Long? {
        val date = day?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        return date.atStartOfDay().toInstant(ZoneOffset.ofTotalSeconds(zoneOffsetMin * 60)).toEpochMilli()
    }

    fun parseInstant(s: String?): Long? =
        s?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }

    fun parseOffsetMin(s: String?): Int? =
        s?.let { runCatching { OffsetDateTime.parse(it).offset.totalSeconds / 60 }.getOrNull() }
}
