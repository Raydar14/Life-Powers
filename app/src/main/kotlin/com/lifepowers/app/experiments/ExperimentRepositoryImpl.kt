package com.lifepowers.app.experiments

import android.content.Context
import android.util.Log
import com.lifepowers.app.data.personal.ExperimentDao
import com.lifepowers.app.data.personal.ExperimentDayEntity
import com.lifepowers.app.data.personal.ExperimentEntity
import com.lifepowers.app.domain.ExperimentRecord
import com.lifepowers.app.domain.ExperimentRepository
import com.lifepowers.app.domain.ExperimentView
import com.lifepowers.app.domain.MetricRepository
import com.lifepowers.app.notify.Notices
import com.lifepowers.app.ui.screens.patterns.PatternLabels
import com.lifepowers.core.Experiments
import com.lifepowers.core.Metrics
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExperimentRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ExperimentDao,
    private val metrics: MetricRepository,
) : ExperimentRepository {

    /** Running first, then finished, newest first: the order the DAO already returns. */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun views(today: LocalDate): Flow<List<ExperimentView>> =
        combine(dao.all(), dao.days()) { rows, days -> rows to days }
            .mapLatest { (rows, days) ->
                val byExperiment = days.groupBy { it.experimentId }
                rows.mapNotNull { row -> build(row, byExperiment[row.id].orEmpty(), today) }
            }

    override fun view(id: Long, today: LocalDate): Flow<ExperimentView?> =
        views(today).map { list -> list.firstOrNull { it.record.spec.id == id } }

    /**
     * Inserts the experiment and makes sure the hourly check is scheduled. When the record carries
     * an id that is free, that id is kept, so the schedule the person previewed (seeded by the id)
     * is the one they get.
     */
    override suspend fun start(record: ExperimentRecord): Long {
        val wanted = record.spec.id
        val keep = wanted > 0 && dao.byIdOnce(wanted) == null
        val id = dao.upsert(ExperimentMath.toEntity(record.copy(endedOn = null), id = if (keep) wanted else 0L))
        ExperimentNoticeMarks(context).clear(id)
        ExperimentWork.schedule(context)
        ExperimentWork.checkNow(context)
        return id
    }

    override suspend fun setAdhered(id: Long, date: LocalDate, adhered: Boolean?) {
        if (adhered == null) {
            dao.clearDay(id, date.toString())
        } else {
            dao.upsertDay(ExperimentDayEntity(experimentId = id, localDate = date.toString(), adhered = adhered))
        }
    }

    override suspend fun end(id: Long, on: LocalDate) {
        dao.end(id, on.toString())
        Notices.cancel(context, ExperimentMath.noticeId(id))
    }

    override suspend fun delete(id: Long) {
        dao.clearDays(id)
        dao.delete(id)
        ExperimentNoticeMarks(context).clear(id)
        Notices.cancel(context, ExperimentMath.noticeId(id))
    }

    private suspend fun build(row: ExperimentEntity, days: List<ExperimentDayEntity>, today: LocalDate): ExperimentView? {
        val record = try {
            ExperimentMath.toRecord(row)
        } catch (e: Exception) {
            Log.w(TAG, "skipping experiment ${row.id} with a bad start date", e)
            return null
        }
        val spec = record.spec
        val adhered = ExperimentMath.adherence(days)
        val outcome = outcomeSeries(record, today)
        val scoreDay = ExperimentMath.scoreDay(record.endedOn, today)
        val result = Experiments.score(spec, adhered, outcome, scoreDay)
        val sentence = Experiments.sentence(ExperimentMath.forSentence(spec), result) { key -> PatternLabels.label(key) }
        return ExperimentView(
            record = record,
            today = if (record.endedOn != null) null else Experiments.condition(spec, today),
            dayIndex = ExperimentMath.dayIndex(spec, scoreDay),
            adhered = adhered,
            result = result,
            sentence = sentence,
        )
    }

    private suspend fun outcomeSeries(record: ExperimentRecord, today: LocalDate): Map<LocalDate, Double> {
        val spec = record.spec
        val agg = Metrics.spec(spec.outcomeMetric).aggs.first()
        val (lastDays, end) = ExperimentMath.outcomeWindow(spec, record.endedOn, today)
        return try {
            metrics.series(spec.outcomeMetric, agg, lastDays, end).associate { it.localDate to it.value }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "outcome series failed for ${spec.outcomeMetric}", e)
            emptyMap()
        }
    }

    private companion object {
        const val TAG = "Experiments"
    }
}
