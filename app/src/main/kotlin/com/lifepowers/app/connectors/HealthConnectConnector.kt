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
import com.lifepowers.core.StreamCursors
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

/**
 * Reads the shared record types from Health Connect. Each record type keeps its own cursor (the ISO
 * instant of its last complete read, packed with StreamCursors); a type's first run reads 30 days
 * back. A type that fails or lacks permission keeps its old cursor, so granting the permission later
 * still brings in its history. Sources carry the writing app's package for duplicate ranking.
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
        val previous = StreamCursors.decode(cursor)
        val succeeded = HashSet<String>()
        val client = HealthConnectClient.getOrCreate(context)
        val out = ArrayList<Sample>()

        /** Each record type reads from its own cursor, so a type that failed or lacked permission catches up later. */
        fun rangeFor(type: KClass<out Record>): TimeRangeFilter {
            val start = StreamCursors.forStream(previous, streamName(type))
                ?.let { runCatching { Instant.parse(it) }.getOrNull() }
                ?.minus(OVERLAP)
                ?: now.minus(Duration.ofDays(30))
            return TimeRangeFilter.between(start, now)
        }

        suspend fun <T : Record> read(type: KClass<T>, onRecord: (T) -> Unit) {
            if (readAll(client, type, rangeFor(type), onRecord)) succeeded += streamName(type)
        }

        read(SleepSessionRecord::class) { r ->
            val s = r.startTime.toEpochMilli()
            val e = r.endTime.toEpochMilli()
            fun night(metric: String, value: Double) = Sample(metric, value, s, e, zoneOffsetMin, source(r))
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
        read(HeartRateRecord::class) { r ->
            r.samples.forEach { hr ->
                val t = hr.time.toEpochMilli()
                out += Sample(Metrics.HR_SAMPLE, hr.beatsPerMinute.toDouble(), t, null, zoneOffsetMin, source(r))
            }
        }
        read(HeartRateVariabilityRmssdRecord::class) { r ->
            val t = r.time.toEpochMilli()
            out += Sample(Metrics.HRV_NIGHT_AVG, r.heartRateVariabilityMillis, t, t, zoneOffsetMin, source(r))
        }
        read(RestingHeartRateRecord::class) { r ->
            val t = r.time.toEpochMilli()
            out += Sample(Metrics.HR_RESTING, r.beatsPerMinute.toDouble(), t, t, zoneOffsetMin, source(r))
        }
        read(StepsRecord::class) { r ->
            out += Sample(
                Metrics.ACTIVITY_STEPS, r.count.toDouble(),
                r.startTime.toEpochMilli(), r.endTime.toEpochMilli(), zoneOffsetMin, source(r),
            )
        }
        read(ExerciseSessionRecord::class) { r ->
            val s = r.startTime.toEpochMilli()
            val e = r.endTime.toEpochMilli()
            out += Sample(Metrics.WORKOUT_SESSION, (e - s) / 60000.0, s, e, zoneOffsetMin, source(r))
        }
        read(BloodGlucoseRecord::class) { r ->
            out += Sample(Metrics.GLUCOSE_SAMPLE, r.level.inMilligramsPerDeciliter, r.time.toEpochMilli(), null, zoneOffsetMin, source(r))
        }
        read(BloodPressureRecord::class) { r ->
            val t = r.time.toEpochMilli()
            out += Sample(Metrics.BP_SYSTOLIC, r.systolic.inMillimetersOfMercury, t, null, zoneOffsetMin, source(r))
            out += Sample(Metrics.BP_DIASTOLIC, r.diastolic.inMillimetersOfMercury, t, null, zoneOffsetMin, source(r))
        }
        read(WeightRecord::class) { r ->
            out += Sample(Metrics.WEIGHT, r.weight.inKilograms, r.time.toEpochMilli(), null, zoneOffsetMin, source(r))
        }
        read(BodyFatRecord::class) { r ->
            out += Sample(Metrics.BODY_FAT_PCT, r.percentage.value, r.time.toEpochMilli(), null, zoneOffsetMin, source(r))
        }
        read(OxygenSaturationRecord::class) { r ->
            val t = r.time.toEpochMilli()
            out += Sample(Metrics.SPO2_NIGHT_AVG, r.percentage.value, t, t, zoneOffsetMin, source(r))
        }
        read(RespiratoryRateRecord::class) { r ->
            val t = r.time.toEpochMilli()
            out += Sample(Metrics.RESP_RATE_NIGHT, r.rate, t, t, zoneOffsetMin, source(r))
        }

        val streams = RECORD_TYPES.map { streamName(it) }
        return ConnectorResult(samples = out, newCursor = StreamCursors.advance(previous, streams, succeeded, now.toString()))
    }

    /** Health Connect pools many apps; keep which one wrote each record so duplicates can be ranked. */
    private fun source(record: Record): String {
        val pkg = runCatching { record.metadata.dataOrigin.packageName }.getOrNull()
        return if (pkg.isNullOrBlank()) SOURCE else "$SOURCE:$pkg"
    }

    /** Reads every page of one record type. Returns false when the read failed part way or was refused. */
    private suspend fun <T : Record> readAll(
        client: HealthConnectClient,
        type: KClass<T>,
        range: TimeRangeFilter,
        onRecord: (T) -> Unit,
    ): Boolean {
        val buffer = ArrayList<T>()
        return try {
            var token: String? = null
            do {
                val response = client.readRecords(
                    ReadRecordsRequest(recordType = type, timeRangeFilter = range, pageSize = PAGE_SIZE, pageToken = token),
                )
                buffer.addAll(response.records)
                token = response.pageToken
            } while (token != null)
            buffer.forEach(onRecord)
            true
        } catch (e: Exception) {
            // Missing permission or a provider error: keep the other streams, retry this one next time.
            false
        }
    }

    companion object {
        const val ID = "health_connect"
        const val SOURCE = "health_connect"

        fun streamName(type: KClass<out Record>): String = type.simpleName ?: type.toString()
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
