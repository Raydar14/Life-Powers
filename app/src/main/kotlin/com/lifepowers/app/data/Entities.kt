package com.lifepowers.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One canonical measurement. Mirrors metric_sample in docs/DATA_MODEL.md. */
@Entity(
    tableName = "metric_sample",
    indices = [
        Index(value = ["metric", "start_utc", "source"], unique = true),
        Index(value = ["metric", "start_utc"]),
    ],
)
data class MetricSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val metric: String,
    val value: Double,
    @ColumnInfo(name = "start_utc") val startUtc: Long,
    @ColumnInfo(name = "end_utc") val endUtc: Long? = null,
    @ColumnInfo(name = "zone_offset_min") val zoneOffsetMin: Int,
    val source: String,
    @ColumnInfo(name = "is_canonical") val isCanonical: Boolean = true,
)

/** One aggregate per metric per local day. Agg is stored by enum name. */
@Entity(tableName = "daily_metric", primaryKeys = ["local_date", "metric", "agg"])
data class DailyMetricEntity(
    @ColumnInfo(name = "local_date") val localDate: String,
    val metric: String,
    val agg: String,
    val value: Double,
    val n: Int,
    val z30: Double? = null,
)

@Entity(tableName = "behavior_event", indices = [Index(value = ["kind", "start_utc"])])
data class BehaviorEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    @ColumnInfo(name = "start_utc") val startUtc: Long,
    @ColumnInfo(name = "end_utc") val endUtc: Long? = null,
    @ColumnInfo(name = "zone_offset_min") val zoneOffsetMin: Int,
    val quantity: Double? = null,
    val unit: String? = null,
    val note: String? = null,
    val source: String,
)

/** Single row, id = 1. Enums are stored by name, dates as ISO strings. */
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "display_name") val displayName: String? = null,
    @ColumnInfo(name = "date_of_birth") val dateOfBirth: String? = null,
    @ColumnInfo(name = "sex_at_birth") val sexAtBirth: String? = null,
    @ColumnInfo(name = "gender_identity") val genderIdentity: String? = null,
    @ColumnInfo(name = "menstrual_status") val menstrualStatus: String? = null,
    @ColumnInfo(name = "cycle_length_days") val cycleLengthDays: Int? = null,
    @ColumnInfo(name = "cycle_variability") val cycleVariabilityDays: Int? = null,
    @ColumnInfo(name = "last_period_start") val lastPeriodStart: String? = null,
    val contraception: String,
    val hrt: String? = null,
    @ColumnInfo(name = "gah_therapy") val genderAffirmingHormones: String? = null,
    @ColumnInfo(name = "due_or_delivery") val dueOrDeliveryDate: String? = null,
    val medications: String? = null,
    @ColumnInfo(name = "hormonal_model") val hormonalModel: String,
    @ColumnInfo(name = "onboarding_done") val onboardingDone: Boolean = false,
    @ColumnInfo(name = "play_first") val playFirst: Boolean = false,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "voice_checkin", indices = [Index(value = ["start_utc"])])
data class VoiceCheckinEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "start_utc") val startUtc: Long,
    @ColumnInfo(name = "zone_offset_min") val zoneOffsetMin: Int,
    @ColumnInfo(name = "duration_s") val durationS: Double,
    @ColumnInfo(name = "prompt_id") val promptId: String,
    val transcript: String? = null,
    @ColumnInfo(name = "features_json") val featuresJson: String? = null,
    @ColumnInfo(name = "words_json") val wordsJson: String? = null,
    @ColumnInfo(name = "indices_json") val indicesJson: String? = null,
    @ColumnInfo(name = "symptoms_json") val symptomsJson: String,
    @ColumnInfo(name = "quality_ok") val qualityOk: Boolean = true,
    @ColumnInfo(name = "quality_note") val qualityNote: String? = null,
    @ColumnInfo(name = "crisis_flag") val crisisFlag: Boolean = false,
)

/** One row per local day per component. Component and tier are stored by enum name. */
@Entity(tableName = "forecast", primaryKeys = ["local_date", "component"])
data class ForecastEntity(
    @ColumnInfo(name = "local_date") val localDate: String,
    val component: String,
    val tier: String,
    val score: Double? = null,
    @ColumnInfo(name = "inputs_used") val inputsUsed: Int,
    @ColumnInfo(name = "reasons_json") val reasonsJson: String,
)

@Entity(tableName = "sync_cursor")
data class SyncCursorEntity(
    @PrimaryKey val source: String,
    val cursor: String? = null,
    @ColumnInfo(name = "last_success_at") val lastSuccessAt: Long? = null,
    @ColumnInfo(name = "last_error") val lastError: String? = null,
)
