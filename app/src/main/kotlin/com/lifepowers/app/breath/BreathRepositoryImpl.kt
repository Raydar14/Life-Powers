package com.lifepowers.app.breath

import com.lifepowers.app.data.Dates
import com.lifepowers.app.data.personal.BreathDao
import com.lifepowers.app.data.personal.GoalDao
import com.lifepowers.app.data.personal.toDomain
import com.lifepowers.app.domain.BehaviorEvent
import com.lifepowers.app.domain.BreathRepository
import com.lifepowers.app.domain.BreathSessionRecord
import com.lifepowers.app.domain.EventKinds
import com.lifepowers.app.domain.EventRepository
import com.lifepowers.app.domain.GoalRepository
import com.lifepowers.app.domain.MetricRepository
import com.lifepowers.app.domain.SavedBreathPattern
import com.lifepowers.core.Breathing
import com.lifepowers.core.Metrics
import com.lifepowers.core.Sample
import com.lifepowers.core.TargetKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Saved breathing patterns and finished sessions. Each session keeps one breath_session moment on the
 * timeline in the main database (the row remembers its id), a breath.minutes sample, and a goal check
 * when the session counted toward a goal.
 */
@Singleton
class BreathRepositoryImpl @Inject constructor(
    private val dao: BreathDao,
    private val goalDao: GoalDao,
    private val goals: GoalRepository,
    private val events: EventRepository,
    private val metrics: MetricRepository,
) : BreathRepository {

    override val patterns: Flow<List<SavedBreathPattern>> = dao.patterns().map { rows -> rows.map { it.toDomain() } }

    override suspend fun savePattern(p: SavedBreathPattern): Long? {
        if (!Breathing.isAllowed(p.pattern)) return null
        return dao.upsertPattern(p.toEntity())
    }

    override suspend fun deletePattern(id: Long) = dao.deletePattern(id)

    override fun sessions(limit: Int): Flow<List<BreathSessionRecord>> =
        dao.sessions(limit.coerceAtLeast(1)).map { rows -> rows.map { it.toDomain() } }

    override suspend fun session(id: Long): BreathSessionRecord? = dao.sessionOnce(id)?.toDomain()

    override suspend fun saveSession(record: BreathSessionRecord): Long {
        val row = record.copy(id = 0).toEntity(eventId = null)
        val id = dao.insertSession(row)

        val minutes = BreathMath.minutes(record.startUtcMs, record.endUtcMs)
        val eventId = events.add(
            BehaviorEvent(
                kind = EventKinds.BREATH_SESSION,
                startUtcMs = record.startUtcMs,
                endUtcMs = record.endUtcMs,
                zoneOffsetMin = record.zoneOffsetMin,
                quantity = minutes,
                unit = "min",
                note = "${record.patternName}, ${record.pattern.describe()}",
                source = SOURCE,
            ),
        )
        dao.updateSession(row.copy(id = id, eventId = eventId))

        val date = Dates.localDate(record.startUtcMs, record.zoneOffsetMin)
        metrics.upsertSamples(
            listOf(Sample(Metrics.BREATH_MINUTES, minutes, record.startUtcMs, record.endUtcMs, record.zoneOffsetMin, SOURCE)),
        )
        metrics.rebuildDaily(date, date)

        val goal = record.goalId?.let { goalDao.byIdOnce(it) }?.toDomain()
        if (goal != null) {
            goals.check(goal.id, date, if (goal.kind == TargetKind.MINUTES) minutes else 1.0, SOURCE)
        }
        record.patternId?.let { dao.markUsed(it, record.endUtcMs) }
        return id
    }

    override suspend fun deleteSession(id: Long) {
        val row = dao.sessionOnce(id) ?: return
        row.eventId?.let { events.delete(it) }
        dao.deleteSession(id)
        // The minutes leave the day too. A goal check stays, like a habit check after its timer is gone.
        val date = Dates.localDate(row.startUtc, row.zoneOffsetMin)
        metrics.deleteSamples(SOURCE, row.startUtc)
        metrics.rebuildDaily(date, date)
    }

    private companion object {
        const val SOURCE = "breath"
    }
}
