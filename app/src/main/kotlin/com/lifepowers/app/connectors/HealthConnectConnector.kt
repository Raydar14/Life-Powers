package com.lifepowers.app.connectors

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.lifepowers.app.domain.Connector
import com.lifepowers.app.domain.ConnectorResult
import com.lifepowers.core.Metrics
import com.lifepowers.core.Sample
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

/**
 * Reads the shared record types from Health Connect. The cursor is the ISO instant of the last
 * read window end; the first run reads 30 days back. Each record type is read independently so a
 * missing permission costs one stream, not the sync.
 */
@Singleton
class HealthConnectConnector @Inject constructor(
    @ApplicationContext private val context: Context,
) : Connector {

    override val id: String = ID
    override val displayName: String = "Health Connect"

    override suspend fun isAvailable(): Boolean =
        runCatching { HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE }.getOrDefault(false)

    override suspend fun sync(cursor: String?, zoneOffsetMin: Int): ConnectorResult {
        val now = Instant.now()
        val start = cursor?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?.minus(OVERLAP)
            ?: now.minus(Duration.ofDays(30))
        val client = HealthConnectClient.getOrCreate(context)
        val range = TimeRangeFilter.between(start, now)
        val out = ArrayList<Sample>()

        read(client, SleepSessionRecord::class, range) { r ->
            val s = r.startTime.toEpochMilli()
            val e = r.endTime.toEpochMilli()
            fun night(metric: String, value: Double) = Sample(metric, value, s, e, zoneOffsetMin, SOURCE)
            out += night(Metrics.SLEEP_TOTAL_MIN, (e - s) / 60000.0)
            out += night(Metrics.SLEEP_BEDTIME_UTC, s.toDouble())
            out += night(Metrics.SLEEP_WAKE_UTC, e.toDouble())
            if (r.stages.isNotEmpty()) {
                var deep = 0L; var rem = 0L; var light = 0L; var awake = 0L
                r.stages.forEach { st ->
                    val d = st.endTime.toEpochMilli() - st.startTime.toEpochMilli()
                    when (st.stage) {
                        SleepSessionRecord.STAGE_TYPE_DEEP -> deep += d
                        SleepSessionRecord.STAGE_TYPE_REM -> rem += d
                        SleepSessionRecord.STAGE_TYPE_LIGHT -> light += d
                        SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED -> awake += d
                        else -> {}
                    }
                }
                out += night(Metrics.SLEEP_DEEP_MIN, deep / 60000.0)
                out += night(Metrics.SLEEP_REM_MIN, rem / 60000.0)
                out += night(Metrics.SLEEP_LIGHT_MIN, light / 60000.0)
                out += night(Metrics.SLEEP_AWAKE_MIN, awake / 60000.0)
            }
        }
        read(client, HeartRateRecord::class, range) { r ->
            r.samples.forEach { hr ->
                val t = hr.time.toEpochMilli()
                out += Sample(Metrics.HR_SAMPLE, hr.beatsPerMinute.toDouble(), t, null, zoneOffsetMin, SOURCE)
            }
        }
        read(client, HeartRateVariabilityRmssdRecord::class, range) { r ->
            val t = r.time.toEpochMilli()
            out += Sample(Metrics.HRV_NIGHT_AVG, r.heartRateVariabilityMillis, t, t, zoneOffsetMin, SOURCE)
        }
        read(client, RestingHeartRateRecord::class, range) { r ->
            val t = r.time.toEpochMilli()
            out += Sample(Metrics.HR_RESTING, r.beatsPerMinute.toDouble(), t, t, zoneOffsetMin, SOURCE)
        }
        read(client, StepsRecord::class, range) { r ->
            out += Sample(
                Metrics.ACTIVITY_STEPS, r.count.toDouble(),
                r.startTime.toEpochMilli(), r.endTime.toEpochMilli(), zoneOffsetMin, SOURCE,
            )
        }
        read(client, ExerciseSessionRecord::class, range) { r ->
            val s = r.startTime.toEpochMilli()
            val e = r.endTime.toEpochMilli()
            out += Sample(Metrics.WORKOUT_SESSION, (e - s) / 60000.0, s, e, zoneOffsetMin, SOURCE)
        }
        read(client, BloodGlucoseRecord::class, range) { r ->
            out += Sample(Metrics.GLUCOSE_SAMPLE, r.level.inMilligramsPerDeciliter, r.time.toEpochMilli(), null, zoneOffsetMin, SOURCE)
        }
        read(client, BloodPressureRecord::class, range) { r ->
            val t = r.time.toEpochMilli()
            out += Sample(Metrics.BP_SYSTOLIC, r.systolic.inMillimetersOfMercury, t, null, zoneOffsetMin, SOURCE)
            out += Sample(Metrics.BP_DIASTOLIC, r.diastolic.inMillimetersOfMercury, t, null, zoneOffsetMin, SOURCE)
        }
        read(client, WeightRecord::class, range) { r ->
            out += Sample(Metrics.WEIGHT, r.weight.inKilograms, r.time.toEpochMilli(), null, zoneOffsetMin, SOURCE)
        }
        read(client, BodyFatRecord::class, range) { r ->
            out += Sample(Metrics.BODY_FAT_PCT, r.percentage.value, r.time.toEpochMilli(), null, zoneOffsetMin, SOURCE)
        }
        read(client, OxygenSaturationRecord::class, range) { r ->
            val t = r.time.toEpochMilli()
            out += Sample(Metrics.SPO2_NIGHT_AVG, r.percentage.value, t, t, zoneOffsetMin, SOURCE)
        }
        read(client, RespiratoryRateRecord::class, range) { r ->
            val t = r.time.toEpochMilli()
            out += Sample(Metrics.RESP_RATE_NIGHT, r.rate, t, t, zoneOffsetMin, SOURCE)
        }

        return ConnectorResult(samples = out, newCursor = now.toString())
    }

    private suspend fun <T : Record> read(
        client: HealthConnectClient,
        type: KClass<T>,
        range: TimeRangeFilter,
        onRecord: (T) -> Unit,
    ) {
        try {
            var token: String? = null
            do {
                val response = client.readRecords(
                    ReadRecordsRequest(recordType = type, timeRangeFilter = range, pageSize = PAGE_SIZE, pageToken = token),
                )
                response.records.forEach(onRecord)
                token = response.pageToken
            } while (token != null)
        } catch (e: Exception) {
            // One stream is missing a permission or the provider rejected the read; keep the rest.
        }
    }

    companion object {
        const val ID = "health_connect"
        const val SOURCE = "health_connect"
        private const val PAGE_SIZE = 1000
        /** Re-read a little before the cursor so records written late by the vendor are not missed. */
        private val OVERLAP: Duration = Duration.ofHours(12)

        val RECORD_TYPES: List<KClass<out Record>> = listOf(
            SleepSessionRecord::class,
            HeartRateRecord::class,
            HeartRateVariabilityRmssdRecord::class,
            RestingHeartRateRecord::class,
            StepsRecord::class,
            ExerciseSessionRecord::class,
            BloodGlucoseRecord::class,
            BloodPressureRecord::class,
            WeightRecord::class,
            BodyFatRecord::class,
            OxygenSaturationRecord::class,
            RespiratoryRateRecord::class,
        )

        /** Read permissions for every record type the connector uses, for the UI to request. */
        val PERMISSIONS: Set<String> = RECORD_TYPES.map { HealthPermission.getReadPermission(it) }.toSet()
    }
}
