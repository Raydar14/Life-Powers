package com.lifepowers.app.domain

import com.lifepowers.core.BioSnapshot
import com.lifepowers.core.BioSummary
import com.lifepowers.core.BreathPattern
import com.lifepowers.core.BreathPoint
import com.lifepowers.core.Condition
import com.lifepowers.core.ExperimentResult
import com.lifepowers.core.ExperimentSpec
import com.lifepowers.core.HeartReading
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

/*
 * Phase 3 contracts: breathing with live biofeedback, experiments, assistant features behind the
 * master switch, and backup, export, and delete. Phase 1 and 2 contracts stay unchanged.
 */

// Breathing

/** How a session guides the breath. Any combination; at least one must be on. */
data class BreathGuide(
    /** The drawn cloud that swells and settles, with the seconds counting down. */
    val visual: Boolean = true,
    /** Soft tones: rising for in, falling for out, a quiet tick for holds. Keeps going with the screen off. */
    val tones: Boolean = false,
    /** A spoken word at each change: in, hold, out. Uses the phone's own offline voice. */
    val voice: Boolean = false,
    /** A short buzz at each change. */
    val vibration: Boolean = false,
)

data class SavedBreathPattern(
    val id: Long = 0,
    val name: String,
    val pattern: BreathPattern,
    val guide: BreathGuide = BreathGuide(),
    /** Planned length in minutes, or null for open ended. */
    val sessionMin: Int? = 5,
    val createdUtcMs: Long = System.currentTimeMillis(),
    val lastUsedUtcMs: Long? = null,
)

data class BreathSessionRecord(
    val id: Long = 0,
    val patternId: Long?,
    val patternName: String,
    val pattern: BreathPattern,
    val startUtcMs: Long,
    val endUtcMs: Long,
    val zoneOffsetMin: Int,
    /** The heart rate source's name, such as "Polar H10", or null when none was connected. */
    val sensor: String?,
    val summary: BioSummary,
    /** Heart rate per five seconds through the session, for the chart. */
    val hrTimeline: List<Double?>,
    /** Optional goal the minutes count toward. */
    val goalId: Long? = null,
)

interface BreathRepository {
    /** Saved patterns, most recently used first. */
    val patterns: Flow<List<SavedBreathPattern>>
    /** Refuses a pattern core Breathing.isAllowed rejects, returning null. */
    suspend fun savePattern(p: SavedBreathPattern): Long?
    suspend fun deletePattern(id: Long)
    fun sessions(limit: Int = 50): Flow<List<BreathSessionRecord>>
    suspend fun session(id: Long): BreathSessionRecord?
    /** Stores the session, adds a timeline moment, writes breath.minutes, and checks the goal when given. */
    suspend fun saveSession(record: BreathSessionRecord): Long
    suspend fun deleteSession(id: Long)
}

enum class HeartLinkState { OFF, NEEDS_PERMISSION, BLUETOOTH_OFF, SCANNING, CONNECTING, CONNECTED, LOST }

/** A heart rate sensor seen nearby. */
data class HeartDevice(val address: String, val name: String, val rssi: Int)

/**
 * A live heart rate link over the standard Bluetooth heart rate service (0x180D): chest straps and
 * watches in broadcast mode. Oura and most watches do not stream live to other apps; Health Connect
 * data arrives late, so it is not used for live feedback.
 */
interface HeartLink {
    val state: StateFlow<HeartLinkState>
    val connected: StateFlow<HeartDevice?>
    /** Every reading since connect, newest last. Cleared on a new connect. */
    val readings: StateFlow<List<HeartReading>>
    /** Devices seen in the current scan. */
    val nearby: StateFlow<List<HeartDevice>>
    /** The last device used, remembered across launches. */
    val remembered: Flow<HeartDevice?>
    fun hasPermissions(): Boolean
    fun startScan()
    fun stopScan()
    fun connect(device: HeartDevice)
    /** Reconnects to the remembered device if there is one. */
    fun connectRemembered()
    fun disconnect()
}

/** Live state of the running breath session, owned by a foreground service so it survives the screen turning off. */
data class BreathLive(
    val running: Boolean,
    val paused: Boolean,
    val name: String,
    val pattern: BreathPattern,
    val guide: BreathGuide,
    val startUtcMs: Long,
    val elapsedS: Double,
    val plannedS: Double?,
    val point: BreathPoint,
    val bio: BioSnapshot?,
    /** Heart rate per second over the last two minutes, for the live line. */
    val recentHr: List<Double?>,
)

interface BreathSessionController {
    /** Null when no session is running. */
    val live: StateFlow<BreathLive?>
    /** The last finished session's id, set when it is saved. */
    val lastSavedId: StateFlow<Long?>
    fun start(pattern: SavedBreathPattern, goalId: Long? = null)
    fun pause()
    fun resume()
    /** Ends and saves. Sessions under 30 seconds are discarded. */
    fun finish()
    fun cancel()
}

// Experiments

data class ExperimentRecord(
    val spec: ExperimentSpec,
    val name: String,
    /** Local hour for the daily notice of today's condition, or null for none. */
    val reminderHour: Int? = 8,
    val endedOn: LocalDate? = null,
)

data class ExperimentView(
    val record: ExperimentRecord,
    val today: Condition?,
    /** Days so far, the target, and adherence answers given. */
    val dayIndex: Int,
    val adhered: Map<LocalDate, Boolean>,
    val result: ExperimentResult,
    val sentence: String,
)

interface ExperimentRepository {
    /** Running first, then finished, newest first. */
    fun views(today: LocalDate): Flow<List<ExperimentView>>
    fun view(id: Long, today: LocalDate): Flow<ExperimentView?>
    suspend fun start(record: ExperimentRecord): Long
    suspend fun setAdhered(id: Long, date: LocalDate, adhered: Boolean?)
    suspend fun end(id: Long, on: LocalDate)
    suspend fun delete(id: Long)
}

// Assistant

object AssistantFeatures {
    const val CARD = "card_interpretation"
    const val PHOTO = "photo_questions"
    const val CHECKIN = "transcript_ratings"
    const val NARRATIVE = "weekly_narrative"
    val ALL = listOf(CARD, PHOTO, CHECKIN, NARRATIVE)
}

/** Exactly what a call will send, shown to the person before anything leaves the phone. */
data class AssistantPreview(
    val feature: String,
    val title: String,
    /** Plain lines, one per item sent: "The card: The Moon, reversed", "One photo, about 400 KB". */
    val sends: List<String>,
    /** What is never sent for this feature: "No health measurements." */
    val neverSends: List<String>,
)

sealed interface AssistantResult {
    data class Ok(val text: String) : AssistantResult
    data class Questions(val questions: List<String>) : AssistantResult
    data class Ratings(val energy: Double, val optimism: Double, val tension: Double, val warmth: Double, val reflection: String) : AssistantResult
    /** A plain sentence for the person, never a stack trace. */
    data class Failed(val message: String) : AssistantResult
}

data class AssistantCallLog(val id: Long, val feature: String, val atUtcMs: Long, val sentSummary: String, val response: String?, val ok: Boolean)

interface AssistantRepository {
    /** Master and per feature switches. Everything is off until the person turns it on. */
    val enabled: Flow<Map<String, Boolean>>
    val masterOn: Flow<Boolean>
    suspend fun setFeature(feature: String, on: Boolean)
    /** True once an API key is stored. The key itself is never exposed back to the UI. */
    val hasKey: Flow<Boolean>
    suspend fun setKey(key: String?)
    /** True when the master switch and this feature are on and a key is stored. */
    fun available(feature: String): Flow<Boolean>
    val log: Flow<List<AssistantCallLog>>

    fun previewCard(pull: TarotPull, includeNote: Boolean, dayTier: String?): AssistantPreview
    suspend fun interpretCard(pull: TarotPull, includeNote: Boolean, dayTier: String?): AssistantResult

    fun previewPhoto(photo: Photo, sizeBytes: Int, caption: String?): AssistantPreview
    suspend fun askAboutPhoto(photo: Photo, jpeg: ByteArray, caption: String?): AssistantResult

    fun previewCheckin(transcript: String): AssistantPreview
    /** Ratings are stored as assist.* metrics for the day of the check-in. */
    suspend fun rateCheckin(checkinStartUtcMs: Long, zoneOffsetMin: Int, transcript: String): AssistantResult

    /** The week's narrative is written from finding sentences and goal summaries only, never raw data. */
    fun previewNarrative(findings: List<String>, goals: List<String>): AssistantPreview
    suspend fun weeklyNarrative(findings: List<String>, goals: List<String>): AssistantResult
}

// Backup, export, delete

data class BackupSummary(val goals: Int, val journalEntries: Int, val photos: Int, val events: Int, val samples: Int, val breathSessions: Int, val experiments: Int)

sealed interface DataResult {
    data class Done(val summary: BackupSummary?, val message: String) : DataResult
    data class Failed(val message: String) : DataResult
}

interface DataVault {
    /** Writes an encrypted backup of everything (photos optional) to [out]. Secrets such as keys and tokens are never included. */
    suspend fun backup(out: java.io.OutputStream, password: CharArray, includePhotos: Boolean): DataResult
    /** Reads a backup and replaces the data on this phone with it. */
    suspend fun restore(input: java.io.InputStream, password: CharArray): DataResult
    /** A zip of plain CSV files (daily metrics, events, goals and checks, breath sessions) for spreadsheets. */
    suspend fun exportCsv(out: java.io.OutputStream): DataResult
    /** Removes every record, photo, setting, key, and token from this phone. */
    suspend fun deleteEverything(): DataResult
}
