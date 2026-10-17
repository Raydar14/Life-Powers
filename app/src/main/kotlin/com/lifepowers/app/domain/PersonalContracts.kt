package com.lifepowers.app.domain

import com.lifepowers.core.AuroraChance
import com.lifepowers.core.Cadence
import com.lifepowers.core.Comparison
import com.lifepowers.core.DailyWeather
import com.lifepowers.core.EclipseEvent
import com.lifepowers.core.GeoPoint
import com.lifepowers.core.Insight
import com.lifepowers.core.MoonInfo
import com.lifepowers.core.OpenMeteo
import com.lifepowers.core.Pairing
import com.lifepowers.core.PeriodProgress
import com.lifepowers.core.PlanetSighting
import com.lifepowers.core.ShowerNight
import com.lifepowers.core.Sky
import com.lifepowers.core.TargetKind
import com.lifepowers.core.Tier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/*
 * Phase 2 contracts: goals and habits, the timer, the journal with photos and tarot, earth and sky,
 * and patterns. Implemented in data, world, and goals packages; consumed by view models.
 * Contracts.kt holds the Phase 1 contracts and stays unchanged.
 */

// Goals, habits, timer

/** Where an automatic check comes from, when the data already knows. */
object AutoSources {
    /** The linked metric on the right side of the threshold (steps from the watch, bedtime from Oura). */
    const val METRIC = "metric"
    /** Any journal entry or voice check-in that day. */
    const val JOURNAL = "journal"
    /** Phone use under the threshold minutes that day, from usage stats. */
    const val PHONE_DOWN = "phone_down"
}

data class Goal(
    val id: Long = 0,
    val name: String,
    val cadence: Cadence,
    val kind: TargetKind,
    /** Count, minutes, or qualifying days, see core GoalSpec. Ignored for CHECK. */
    val target: Double = 1.0,
    val metric: String? = null,
    val comparison: Comparison = Comparison.AT_LEAST,
    val threshold: Double? = null,
    /** One of [AutoSources], or null for manual habits. */
    val autoSource: String? = null,
    /** A repeating goal shows in the habit tracker. */
    val isHabit: Boolean = true,
    /** Local times the app reminds. Empty means no reminders. */
    val reminderTimes: List<LocalTime> = emptyList(),
    /** Days the reminders fire on. Empty means every day. */
    val reminderDays: Set<DayOfWeek> = emptySet(),
    /** A gentle notice on the last day of a period that is not yet met. */
    val closingNotice: Boolean = true,
    val archived: Boolean = false,
    val createdUtcMs: Long = System.currentTimeMillis(),
)

data class HabitCheck(
    val id: Long = 0,
    val goalId: Long,
    val localDate: LocalDate,
    /** 1.0 for a plain check, minutes for MINUTES goals. */
    val amount: Double = 1.0,
    /** "manual", "timer", or "auto". */
    val source: String = "manual",
    val note: String? = null,
    val createdUtcMs: Long = System.currentTimeMillis(),
)

data class TimerSession(
    val id: Long = 0,
    val goalId: Long?,
    val startUtcMs: Long,
    val endUtcMs: Long? = null,
    /** Planned length in minutes, or null for open ended. */
    val plannedMin: Int? = null,
    val zoneOffsetMin: Int,
)

/** Everything the Goals screen shows for one goal. */
data class GoalView(
    val goal: Goal,
    /** The last periods, oldest first, ending with the current one. 12 for daily, 8 weekly, 6 monthly, 3 yearly. */
    val history: List<PeriodProgress>,
    val current: PeriodProgress,
    val currentRun: Int,
    val bestRun: Int,
    /** 28 days ending today, oldest first. */
    val grid: List<Pair<LocalDate, Boolean>>,
    val closingLine: String?,
    val checksToday: List<HabitCheck>,
)

interface GoalRepository {
    /** Active goals, newest first. */
    val goals: Flow<List<Goal>>
    fun views(today: LocalDate): Flow<List<GoalView>>
    fun view(goalId: Long, today: LocalDate): Flow<GoalView?>
    suspend fun save(goal: Goal): Long
    suspend fun archive(goalId: Long)
    suspend fun delete(goalId: Long)
    suspend fun check(goalId: Long, date: LocalDate, amount: Double = 1.0, source: String = "manual", note: String? = null): Long
    suspend fun uncheck(checkId: Long)
    /** Writes automatic checks for [date] from daily metrics, journal entries, and usage. Idempotent. */
    suspend fun autoCheck(date: LocalDate)
}

interface TimerController {
    /** The running session, or null. Survives process death (persisted). */
    val running: StateFlow<TimerSession?>
    suspend fun start(goalId: Long?, plannedMin: Int?)
    /** Ends the session, logs its real minutes against the goal, and adds a timeline moment. */
    suspend fun stop(): TimerSession?
    suspend fun cancel()
}

interface ReminderScheduler {
    /** Cancels and re-arms every goal reminder and the period closing notices. */
    suspend fun rescheduleAll()
}

// Journal, photos, tarot

data class Photo(
    val id: Long = 0,
    val entryId: Long,
    /** File name under filesDir/photos, encrypted at rest. */
    val fileName: String,
    val mime: String,
    val caption: String? = null,
    val createdUtcMs: Long = System.currentTimeMillis(),
)

data class TarotPull(
    val id: Long = 0,
    val entryId: Long? = null,
    val localDate: LocalDate,
    val deck: String? = null,
    val spread: String? = null,
    val position: String? = null,
    val card: String,
    val reversed: Boolean = false,
    val note: String? = null,
    val interpretation: String? = null,
    val interpretationFromAssistant: Boolean = false,
    val createdUtcMs: Long = System.currentTimeMillis(),
)

data class JournalEntry(
    val id: Long = 0,
    val localDate: LocalDate,
    val createdUtcMs: Long = System.currentTimeMillis(),
    val zoneOffsetMin: Int,
    val text: String = "",
    val voiceTranscript: String? = null,
    val tags: List<String> = emptyList(),
    /** Run the check-in word markers over this entry. Off by default. */
    val scoreWords: Boolean = false,
    val photos: List<Photo> = emptyList(),
    val pulls: List<TarotPull> = emptyList(),
)

interface JournalRepository {
    fun entries(from: LocalDate, to: LocalDate): Flow<List<JournalEntry>>
    fun entry(id: Long): Flow<JournalEntry?>
    /** Inserts or updates, and keeps one journal moment on the timeline for the entry. */
    suspend fun saveEntry(entry: JournalEntry): Long
    suspend fun deleteEntry(id: Long)
    /** Encrypts and stores the bytes beside the entry. */
    suspend fun addPhoto(entryId: Long, bytes: ByteArray, mime: String, caption: String? = null): Photo
    /** Decrypted bytes, or null if the file is gone. */
    suspend fun loadPhoto(photo: Photo): ByteArray?
    suspend fun deletePhoto(id: Long)
    /** Inserts or updates, and keeps one tarot moment on the timeline for the pull. */
    suspend fun savePull(pull: TarotPull): Long
    suspend fun deletePull(id: Long)
    fun pulls(from: LocalDate, to: LocalDate): Flow<List<TarotPull>>
}

// Earth and sky

enum class LocationMode { PRECISE, APPROXIMATE, CITY, NONE }

data class LocationSetting(
    val mode: LocationMode = LocationMode.NONE,
    /** Display name, such as "Portland, Oregon". */
    val label: String? = null,
    /** Stored as the device gave it (PRECISE), rounded (APPROXIMATE), or the city's point (CITY). */
    val point: GeoPoint? = null,
    val updatedUtcMs: Long = 0,
)

data class SkySettings(
    /** Retrogrades and signs. Off by default. */
    val transitsOn: Boolean = false,
    val notifySky: Boolean = true,
    val notifyAurora: Boolean = true,
    val notifyGround: Boolean = false,
    val groundMinMagnitude: Double = 4.5,
    val groundRadiusKm: Double = 500.0,
    /** Local hour for the evening sky notice. */
    val eveningHour: Int = 19,
)

data class WorldEvent(
    val id: String,
    /** "quake", "volcano", or "aurora". */
    val kind: String,
    val title: String,
    val utcMs: Long,
    val magnitude: Double? = null,
    val distanceKm: Double? = null,
    val ringOfFire: Boolean = false,
    val link: String? = null,
    val notified: Boolean = false,
)

/** Everything the Sky screen shows for one day. */
data class SkyToday(
    val date: LocalDate,
    val location: LocationSetting,
    val moon: MoonInfo,
    val retrogrades: List<Sky.RetrogradeStatus>,
    val planets: List<PlanetSighting>,
    val pairings: List<Pairing>,
    /** The next twelve months. */
    val eclipses: List<EclipseEvent>,
    /** The next sixty days. */
    val showers: List<ShowerNight>,
    val kpNow: Double?,
    val kpTonightMax: Double?,
    val aurora: AuroraChance?,
    val kpNeeded: Double?,
    val weather: DailyWeather?,
    val tier: Tier,
    val tierReasons: List<String>,
    val transitsOn: Boolean,
)

interface WorldRepository {
    val location: Flow<LocationSetting>
    suspend fun setLocation(setting: LocationSetting)
    /** Reads the device location once for PRECISE or APPROXIMATE. Returns false without permission. */
    suspend fun refreshDeviceLocation(): Boolean
    suspend fun searchCity(name: String): List<OpenMeteo.Place>
    val skySettings: Flow<SkySettings>
    suspend fun saveSkySettings(settings: SkySettings)
    fun skyToday(date: LocalDate): Flow<SkyToday>
    fun groundEvents(sinceUtcMs: Long): Flow<List<WorldEvent>>
    /** Network refresh: weather and air (with backfill), quakes, volcano reports, K index. Safe to call often. */
    suspend fun refresh()
}

/** The sky panel's own tier for a day. Implemented by the world package, read by the forecast. */
interface SkyTierSource {
    suspend fun tierFor(date: LocalDate): Pair<Tier, List<String>>
}

// Patterns

data class PatternCoverage(val daysWithData: Int, val behaviorCounts: Map<String, Int>)

interface PatternRepository {
    /** The last computed findings, strongest first, final ones before preliminary ones. */
    val insights: Flow<List<Insight>>
    val coverage: Flow<PatternCoverage>
    /** When the findings were last computed, or null. */
    val computedUtcMs: Flow<Long?>
    /** Rebuilds series from the last 180 days of daily metrics, events, and habit checks, then runs the engine. */
    suspend fun recompute()
}
