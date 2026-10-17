package com.lifepowers.app.goals

import com.lifepowers.app.data.Dates
import com.lifepowers.app.data.personal.GoalDao
import com.lifepowers.app.data.personal.HabitCheckDao
import com.lifepowers.app.data.personal.HabitCheckEntity
import com.lifepowers.app.data.personal.JournalDao
import com.lifepowers.app.data.personal.toDomain
import com.lifepowers.app.data.personal.toEntity
import com.lifepowers.app.domain.AutoSources
import com.lifepowers.app.domain.CheckinRepository
import com.lifepowers.app.domain.EventKinds
import com.lifepowers.app.domain.EventRepository
import com.lifepowers.app.domain.Goal
import com.lifepowers.app.domain.GoalRepository
import com.lifepowers.app.domain.GoalView
import com.lifepowers.app.domain.HabitCheck
import com.lifepowers.app.domain.MetricRepository
import com.lifepowers.app.domain.ReminderScheduler
import com.lifepowers.app.domain.nowZoneOffsetMin
import com.lifepowers.core.Goals
import com.lifepowers.core.Metrics
import com.lifepowers.core.TargetKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Goals and habit checks from the personal database, with progress built by core [Goals]. Goals
 * linked to a metric read their daily values from the main database.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class GoalRepositoryImpl @Inject constructor(
    private val goalDao: GoalDao,
    private val checkDao: HabitCheckDao,
    private val journalDao: JournalDao,
    private val metrics: MetricRepository,
    private val events: EventRepository,
    private val checkins: CheckinRepository,
    private val reminders: ReminderScheduler,
) : GoalRepository {

    override val goals: Flow<List<Goal>> = goalDao.active().map { rows -> rows.map { it.toDomain() } }

    override fun views(today: LocalDate): Flow<List<GoalView>> =
        goalDao.active().flatMapLatest { rows ->
            val list = rows.map { it.toDomain() }
            if (list.isEmpty()) return@flatMapLatest flowOf(emptyList())
            val from = list.minOf { GoalMath.historyStart(it.cadence, today) }
            checkDao.between(Dates.iso(from), Dates.iso(today)).mapLatest { checkRows ->
                val byGoal = checkRows.map { it.toDomain() }.groupBy { it.goalId }
                list.map { buildView(it, byGoal[it.id].orEmpty(), today) }
            }
        }.flowOn(Dispatchers.Default)

    override fun view(goalId: Long, today: LocalDate): Flow<GoalView?> =
        goalDao.byId(goalId).flatMapLatest { row ->
            val goal = row?.toDomain() ?: return@flatMapLatest flowOf(null)
            val from = GoalMath.historyStart(goal.cadence, today)
            checkDao.forGoal(goalId, Dates.iso(from), Dates.iso(today)).mapLatest { checkRows ->
                buildView(goal, checkRows.map { it.toDomain() }, today)
            }
        }.flowOn(Dispatchers.Default)

    private suspend fun buildView(goal: Goal, checks: List<HabitCheck>, today: LocalDate): GoalView {
        val spec = GoalMath.spec(goal)
        val pairs = checks.map { it.localDate to it.amount }
        val metricDays = goal.metric?.let { metricDays(it, GoalMath.daysBetween(GoalMath.historyStart(goal.cadence, today), today), today) }
            ?: emptyMap()
        val history = Goals.history(spec, today, GoalMath.historyCount(goal.cadence), pairs, metricDays)
        val current = history.last()
        return GoalView(
            goal = goal,
            history = history,
            current = current,
            currentRun = Goals.currentRun(history, today),
            bestRun = Goals.bestRun(history),
            grid = Goals.dotGrid(spec, today, pairs, metricDays, GoalMath.GRID_DAYS),
            closingLine = Goals.closingLine(spec, current, today),
            checksToday = checks.filter { it.localDate == today },
        )
    }

    private suspend fun metricDays(metric: String, days: Int, today: LocalDate): Map<LocalDate, Double> =
        try {
            metrics.series(metric, Metrics.spec(metric).aggs.first(), days, today).associate { it.localDate to it.value }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyMap()
        }

    override suspend fun save(goal: Goal): Long {
        val id = goalDao.upsert(goal.toEntity())
        rearm()
        return id
    }

    override suspend fun archive(goalId: Long) {
        goalDao.archive(goalId)
        rearm()
    }

    override suspend fun delete(goalId: Long) {
        checkDao.deleteForGoal(goalId)
        goalDao.delete(goalId)
        rearm()
    }

    private suspend fun rearm() {
        try {
            reminders.rescheduleAll()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The goal is saved; alarms are re-armed again at the next app start.
        }
    }

    override suspend fun check(goalId: Long, date: LocalDate, amount: Double, source: String, note: String?): Long =
        checkDao.insert(HabitCheck(goalId = goalId, localDate = date, amount = amount, source = source, note = note).toEntity())

    override suspend fun uncheck(checkId: Long) = checkDao.delete(checkId)

    override suspend fun autoCheck(date: LocalDate) {
        val auto = goalDao.activeOnce().map { it.toDomain() }.filter { it.autoSource != null }
        if (auto.isEmpty()) return
        val iso = Dates.iso(date)
        var journaled: Boolean? = null
        for (goal in auto) {
            val amount: Double? = when (goal.autoSource) {
                AutoSources.METRIC -> {
                    val metric = goal.metric
                    val value = metric?.let { dailyValue(it, date) }
                    when {
                        value == null -> null
                        !Goals.qualifies(GoalMath.spec(goal), value) -> null
                        goal.kind == TargetKind.MINUTES -> value
                        else -> 1.0
                    }
                }
                AutoSources.JOURNAL -> {
                    val j = journaled ?: journaledOn(date).also { journaled = it }
                    if (j) 1.0 else null
                }
                AutoSources.PHONE_DOWN -> {
                    val limit = goal.threshold
                    val screen = dailyValue(Metrics.SCREEN_TOTAL_MIN, date)
                    if (limit != null && screen != null && screen <= limit) 1.0 else null
                }
                else -> continue
            }
            checkDao.deleteAuto(goal.id, iso)
            if (amount != null) {
                checkDao.insert(
                    HabitCheckEntity(
                        goalId = goal.id,
                        localDate = iso,
                        amount = amount,
                        source = SOURCE_AUTO,
                        note = null,
                        createdUtc = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    private suspend fun dailyValue(metric: String, date: LocalDate): Double? =
        metrics.series(metric, Metrics.spec(metric).aggs.first(), 1, date).firstOrNull()?.value

    /** Any journal entry, journal moment, or voice check-in on [date]. */
    private suspend fun journaledOn(date: LocalDate): Boolean {
        if (journalDao.countOn(Dates.iso(date)) > 0) return true
        val zone = nowZoneOffsetMin()
        val from = Dates.startOfDayMs(date.minusDays(1), zone)
        val to = Dates.startOfDayMs(date.plusDays(2), zone) - 1
        val journalEvent = events.between(from, to).first().any {
            it.kind == EventKinds.JOURNAL && Dates.localDate(it.startUtcMs, it.zoneOffsetMin) == date
        }
        if (journalEvent) return true
        return checkins.recent(50).first().any { Dates.localDate(it.startUtcMs, it.zoneOffsetMin) == date }
    }

    companion object {
        const val SOURCE_AUTO = "auto"
    }
}
