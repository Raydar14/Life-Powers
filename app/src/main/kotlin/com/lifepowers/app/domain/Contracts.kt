package com.lifepowers.app.domain

import com.lifepowers.core.Agg
import com.lifepowers.core.AcousticFeatures
import com.lifepowers.core.Component
import com.lifepowers.core.Contraception
import com.lifepowers.core.HormonalModel
import com.lifepowers.core.MenstrualStatus
import com.lifepowers.core.ParsedSymptom
import com.lifepowers.core.Phase
import com.lifepowers.core.Sample
import com.lifepowers.core.SexAtBirth
import com.lifepowers.core.Tier
import com.lifepowers.core.VoiceIndices
import com.lifepowers.core.WordsScores
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/*
 * Shared contracts between the data layer, the connectors, the voice pipeline, and the UI.
 * Everything below is implemented in the data package and consumed by view models.
 * Keep this file small and stable; other packages depend on it.
 */

/** One daily aggregate with its robust z score against the trailing 30 days, when available. */
data class DailyMetricPoint(
    val localDate: LocalDate,
    val metric: String,
    val agg: Agg,
    val value: Double,
    val n: Int,
    val z30: Double?,
)

data class ForecastSnapshot(
    val localDate: LocalDate,
    val component: Component,
    val tier: Tier,
    val score: Double?,
    val inputsUsed: Int,
    val reasons: List<String>,
)

/** A behavior, symptom, craving, or context event. Kinds follow docs/DATA_MODEL.md. */
data class BehaviorEvent(
    val id: Long = 0,
    val kind: String,
    val startUtcMs: Long,
    val endUtcMs: Long? = null,
    val zoneOffsetMin: Int,
    val quantity: Double? = null,
    val unit: String? = null,
    val note: String? = null,
    val source: String,
)

object EventKinds {
    const val ALCOHOL = "alcohol"
    const val CAFFEINE = "caffeine"
    const val MEAL = "meal"
    const val SUPPLEMENT = "supplement"
    const val MEDICATION = "medication"
    const val ILLNESS = "illness"
    const val STRESS_EVENT = "stress_event"
    const val PERIOD_START = "period_start"
    const val CRAVING = "craving"
    const val URGE_TO_BINGE = "urge_to_binge"
    const val BINGE = "binge"
    const val JOURNAL = "journal"
    const val TAROT_PULL = "tarot_pull"
    const val SCREENS_IN_BED = "screens_in_bed"
    const val HORMONE_DOSE = "hormone_dose"
    const val HABIT_SESSION = "habit_session"
    fun symptom(kind: String) = "symptom.$kind"
}

data class UserProfileState(
    val displayName: String? = null,
    val dateOfBirth: LocalDate? = null,
    val sexAtBirth: SexAtBirth? = null,
    val genderIdentity: String? = null,
    val menstrualStatus: MenstrualStatus? = null,
    val cycleLengthDays: Int? = null,
    val cycleVariabilityDays: Int? = null,
    val lastPeriodStart: LocalDate? = null,
    val contraception: Contraception = Contraception.NONE,
    val hrt: String? = null,
    val genderAffirmingHormones: String? = null,
    val dueOrDeliveryDate: LocalDate? = null,
    val medications: String? = null,
    val hormonalModel: HormonalModel = HormonalModel.NO_CYCLE,
    val onboardingDone: Boolean = false,
    val playFirst: Boolean = false,
)

data class PhaseForDay(val localDate: LocalDate, val phase: Phase, val cycleDay: Int?, val confidence: Double, val basis: String)

/** One saved voice check-in, after on device processing. */
data class CheckinRecord(
    val id: Long = 0,
    val startUtcMs: Long,
    val zoneOffsetMin: Int,
    val durationS: Double,
    val promptId: String,
    val transcript: String?,
    val features: AcousticFeatures?,
    val words: WordsScores?,
    val indices: VoiceIndices?,
    val symptoms: List<ParsedSymptom>,
    val qualityOk: Boolean,
    val qualityNote: String?,
    val crisisFlag: Boolean,
)

enum class SyncState { IDLE, RUNNING, OK, ERROR, NOT_CONNECTED }

data class SyncStatus(val connectorId: String, val state: SyncState, val lastSuccessUtcMs: Long?, val lastError: String?)

data class ConnectorResult(val samples: List<Sample>, val events: List<BehaviorEvent> = emptyList(), val newCursor: String? = null)

/** One data source. Implementations live in the connectors package. */
interface Connector {
    val id: String
    val displayName: String
    suspend fun isAvailable(): Boolean
    suspend fun sync(cursor: String?, zoneOffsetMin: Int): ConnectorResult
}

interface MetricRepository {
    suspend fun upsertSamples(samples: List<Sample>)
    suspend fun rebuildDaily(from: LocalDate, to: LocalDate)
    fun dailyBetween(from: LocalDate, to: LocalDate): Flow<List<DailyMetricPoint>>
    suspend fun series(metric: String, agg: Agg, lastDays: Int, endInclusive: LocalDate): List<DailyMetricPoint>
    suspend fun samplesBetween(metric: String, fromUtcMs: Long, toUtcMs: Long): List<Sample>
}

interface EventRepository {
    suspend fun add(event: BehaviorEvent): Long
    suspend fun delete(id: Long)
    fun between(fromUtcMs: Long, toUtcMs: Long): Flow<List<BehaviorEvent>>
    suspend fun countOfKind(kind: String): Int
}

interface ProfileRepository {
    val profile: Flow<UserProfileState>
    suspend fun save(profile: UserProfileState)
    suspend fun phaseFor(date: LocalDate): PhaseForDay
}

interface ForecastRepository {
    fun forDay(date: LocalDate): Flow<List<ForecastSnapshot>>
    fun dayTiers(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Tier>>
    suspend fun recompute(date: LocalDate)
}

interface CheckinRepository {
    suspend fun save(record: CheckinRecord): Long
    fun recent(limit: Int): Flow<List<CheckinRecord>>
    suspend fun baselineCount(): Int
}

interface SyncManager {
    val status: Flow<List<SyncStatus>>
    suspend fun syncNow()
    fun schedulePeriodic()
}

/** Settings that gate assistant features and other switches. Stored in DataStore. */
interface SettingsRepository {
    val assistantMaster: Flow<Boolean>
    val keepAudio: Flow<Boolean>
    val ouraToken: Flow<String?>
    suspend fun setAssistantMaster(on: Boolean)
    suspend fun setKeepAudio(on: Boolean)
    suspend fun setOuraToken(token: String?)
}

/** Fixed zone helper used when a sample does not carry its own offset. */
fun nowZoneOffsetMin(): Int = java.time.ZoneId.systemDefault().rules.getOffset(Instant.now()).totalSeconds / 60
