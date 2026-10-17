package com.lifepowers.app.patterns

import android.content.Context
import com.lifepowers.app.data.Dates
import com.lifepowers.app.data.personal.GoalDao
import com.lifepowers.app.data.personal.HabitCheckDao
import com.lifepowers.app.data.personal.toDomain
import com.lifepowers.app.domain.EventKinds
import com.lifepowers.app.domain.EventRepository
import com.lifepowers.app.domain.MetricRepository
import com.lifepowers.app.domain.PatternCoverage
import com.lifepowers.app.domain.PatternRepository
import com.lifepowers.app.domain.nowZoneOffsetMin
import com.lifepowers.core.DaySeries
import com.lifepowers.core.Insight
import com.lifepowers.core.Metrics
import com.lifepowers.core.Patterns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds daily series from the last 180 days of metrics, behavior events, and habit checks, runs the
 * core patterns engine, and keeps the findings in filesDir/patterns.json so they show at once on the
 * next start.
 */
@Singleton
class PatternRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metrics: MetricRepository,
    private val events: EventRepository,
    private val goalDao: GoalDao,
    private val checkDao: HabitCheckDao,
    private val json: Json,
) : PatternRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val _insights = MutableStateFlow<List<Insight>>(emptyList())
    private val _coverage = MutableStateFlow(PatternCoverage(0, emptyMap()))
    private val _computed = MutableStateFlow<Long?>(null)

    override val insights: Flow<List<Insight>> = _insights.asStateFlow()
    override val coverage: Flow<PatternCoverage> = _coverage.asStateFlow()
    override val computedUtcMs: Flow<Long?> = _computed.asStateFlow()

    private val file: File get() = File(context.filesDir, FILE_NAME)

    init {
        scope.launch { load() }
    }

    private suspend fun load(): Unit = mutex.withLock {
        if (_computed.value != null) return@withLock
        val stored = runCatching { json.decodeFromString(PatternsFile.serializer(), file.readText()) }.getOrNull() ?: return@withLock
        publish(stored)
    }

    private fun publish(f: PatternsFile) {
        _insights.value = f.insights.mapNotNull { it.toCore() }
        _coverage.value = PatternCoverage(f.daysWithData, f.behaviorCounts)
        _computed.value = f.computedUtcMs.takeIf { it > 0 }
    }

    override suspend fun recompute() {
        mutex.withLock {
            val result = build()
            withContext(Dispatchers.IO) { write(result) }
            publish(result)
        }
    }

    private suspend fun build(): PatternsFile {
        val today = LocalDate.now()
        val windowStart = today.minusDays((WINDOW_DAYS - 1).toLong())

        val metricKeys = (Patterns.DEFAULT_INPUTS + Patterns.DEFAULT_OUTCOMES).distinct()
        val metricValues: Map<String, Map<LocalDate, Double>> = metricKeys.associateWith { key ->
            runCatching {
                metrics.series(key, Metrics.spec(key).aggs.first(), WINDOW_DAYS, today)
                    .filter { !it.value.isNaN() && !it.value.isInfinite() }
                    .associate { it.localDate to it.value }
            }.getOrDefault(emptyMap())
        }
        val covered = metricValues.values.flatMap { it.keys }.toSet()
        val range = PatternSeries.coveredRange(metricValues.values, today)

        // Local days on which each event kind happened, by the event's own zone.
        val zone = nowZoneOffsetMin()
        val fromUtc = Dates.startOfDayMs(windowStart.minusDays(1), zone)
        val toUtc = Dates.startOfDayMs(today.plusDays(2), zone) - 1
        val eventDays: Map<String, Set<LocalDate>> = events.between(fromUtc, toUtc).first()
            .map { it.kind to Dates.localDate(it.startUtcMs, it.zoneOffsetMin) }
            .filter { (_, d) -> !d.isBefore(windowStart) && !d.isAfter(today) }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.toSet() }

        // Days each active habit was checked.
        val habits = goalDao.activeOnce().map { it.toDomain() }.filter { it.isHabit }
        val checks = checkDao.betweenOnce(Dates.iso(windowStart), Dates.iso(today)).map { it.toDomain() }.groupBy { it.goalId }
        val habitDays: Map<String, Set<LocalDate>> = habits.associate { g ->
            HABIT_PREFIX + g.name.trim() to checks[g.id].orEmpty().map { it.localDate }.toSet()
        }

        val inputs = ArrayList<DaySeries>()
        Patterns.DEFAULT_INPUTS.forEach { key ->
            val values = metricValues[key].orEmpty()
            if (values.isNotEmpty()) inputs += DaySeries(key, values, binary = false, higherIsBetter = Metrics.spec(key).higherIsBetter)
        }
        INPUT_KINDS.forEach { kind -> PatternSeries.binary(kind, eventDays[kind].orEmpty(), range)?.let { inputs += it } }
        habitDays.forEach { (key, days) -> PatternSeries.binary(key, days, range)?.let { inputs += it } }

        val outcomes = ArrayList<DaySeries>()
        Patterns.DEFAULT_OUTCOMES.forEach { key ->
            val values = metricValues[key].orEmpty()
            if (values.isNotEmpty()) outcomes += DaySeries(key, values, binary = false, higherIsBetter = Metrics.spec(key).higherIsBetter)
        }
        val symptomKinds = eventDays.filter { (kind, days) -> kind.startsWith(SYMPTOM_PREFIX) && days.size >= PatternSeries.MIN_ON_DAYS }.keys
        (OUTCOME_KINDS + symptomKinds.sorted()).forEach { kind ->
            PatternSeries.binary(kind, eventDays[kind].orEmpty(), range, higherIsBetter = false)?.let { outcomes += it }
        }

        val found = if (inputs.isEmpty() || outcomes.isEmpty()) emptyList()
        else withContext(Dispatchers.Default) { Patterns.run(inputs, outcomes) }

        val counts = eventDays.mapValues { it.value.size } + habitDays.mapValues { it.value.size }
        return PatternsFile(
            computedUtcMs = System.currentTimeMillis(),
            daysWithData = covered.size,
            behaviorCounts = counts.filterValues { it > 0 },
            insights = found
                .filter { it.effect.isFinite() && it.pValue.isFinite() && it.qValue.isFinite() }
                .map { InsightDto.from(it) },
        )
    }

    private fun write(f: PatternsFile) {
        val tmp = File(context.filesDir, "$FILE_NAME.tmp")
        tmp.writeText(json.encodeToString(PatternsFile.serializer(), f))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    companion object {
        const val FILE_NAME = "patterns.json"
        const val WINDOW_DAYS = 180
        const val HABIT_PREFIX = "habit:"
        private const val SYMPTOM_PREFIX = "symptom."

        val INPUT_KINDS = listOf(
            EventKinds.ALCOHOL, EventKinds.CAFFEINE, EventKinds.SCREENS_IN_BED, EventKinds.STRESS_EVENT,
            EventKinds.CRAVING, EventKinds.URGE_TO_BINGE, EventKinds.BINGE, EventKinds.HABIT_SESSION,
        )
        val OUTCOME_KINDS = listOf(EventKinds.CRAVING, EventKinds.URGE_TO_BINGE, EventKinds.BINGE)
    }
}
