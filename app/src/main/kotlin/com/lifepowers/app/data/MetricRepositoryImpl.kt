package com.lifepowers.app.data

import androidx.room.withTransaction
import com.lifepowers.app.domain.DailyMetricPoint
import com.lifepowers.app.domain.MetricRepository
import com.lifepowers.app.domain.nowZoneOffsetMin
import com.lifepowers.core.Agg
import com.lifepowers.core.Baseline
import com.lifepowers.core.Rollup
import com.lifepowers.core.Sample
import com.lifepowers.core.SourcePriority
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetricRepositoryImpl @Inject constructor(
    private val db: LifePowersDatabase,
    private val samples: MetricSampleDao,
    private val daily: DailyMetricDao,
) : MetricRepository {

    override suspend fun upsertSamples(samples: List<Sample>) {
        if (samples.isEmpty()) return
        this.samples.upsertAll(samples.map { it.toEntity() })
    }

    /**
     * Rebuilds daily_metric for [from]..[to]. Samples are read from a UTC window one day wider on
     * each side so night ending metrics and zone offsets cannot drop a sample at the edge.
     * z30 is computed against the 30 stored days before each date, which includes the freshly
     * written rows of this range.
     */
    override suspend fun rebuildDaily(from: LocalDate, to: LocalDate) {
        if (to.isBefore(from)) return
        val zone = nowZoneOffsetMin()
        val fromUtc = Dates.startOfDayMs(from.minusDays(1), zone)
        val toUtc = Dates.startOfDayMs(to.plusDays(2), zone) - 1
        val raw = samples.canonicalBetween(fromUtc, toUtc).map { it.toSample() }
        // The same night or step count often arrives from two sources; keep the best one per day.
        val rolled = Rollup.daily(SourcePriority.select(raw)).filter { !it.localDate.isBefore(from) && !it.localDate.isAfter(to) }
        val fromIso = Dates.iso(from)
        val toIso = Dates.iso(to)

        db.withTransaction {
            daily.deleteBetween(fromIso, toIso)
            if (rolled.isEmpty()) return@withTransaction
            daily.upsertAll(rolled.map { DailyMetricEntity(Dates.iso(it.localDate), it.metric, it.agg.name, it.value, it.n, null) })

            val withZ = ArrayList<DailyMetricEntity>(rolled.size)
            rolled.groupBy { it.metric to it.agg }.forEach { (key, rows) ->
                val (metric, agg) = key
                val history = daily.series(metric, agg.name, Dates.iso(from.minusDays(30)), toIso)
                    .associate { it.localDate to it.value }
                rows.forEach { row ->
                    val window = (1..30).mapNotNull { back -> history[Dates.iso(row.localDate.minusDays(back.toLong()))] }
                    val z = Baseline.z(row.value, window)
                    withZ += DailyMetricEntity(Dates.iso(row.localDate), metric, agg.name, row.value, row.n, z)
                }
            }
            daily.upsertAll(withZ)
        }
    }

    override fun dailyBetween(from: LocalDate, to: LocalDate): Flow<List<DailyMetricPoint>> =
        daily.between(Dates.iso(from), Dates.iso(to)).map { rows -> rows.mapNotNull { it.toPoint() } }

    override suspend fun series(metric: String, agg: Agg, lastDays: Int, endInclusive: LocalDate): List<DailyMetricPoint> {
        if (lastDays <= 0) return emptyList()
        val start = endInclusive.minusDays((lastDays - 1).toLong())
        return daily.series(metric, agg.name, Dates.iso(start), Dates.iso(endInclusive)).mapNotNull { it.toPoint() }
    }

    override suspend fun samplesBetween(metric: String, fromUtcMs: Long, toUtcMs: Long): List<Sample> =
        samples.forMetricBetween(metric, fromUtcMs, toUtcMs).map { it.toSample() }
}

fun Sample.toEntity() = MetricSampleEntity(
    metric = metric,
    value = value,
    startUtc = startUtcMs,
    endUtc = endUtcMs,
    zoneOffsetMin = zoneOffsetMin,
    source = source,
    isCanonical = true,
)

fun MetricSampleEntity.toSample() = Sample(metric, value, startUtc, endUtc, zoneOffsetMin, source)

fun DailyMetricEntity.toPoint(): DailyMetricPoint? {
    val date = Dates.parseIso(localDate) ?: return null
    val aggEnum = runCatching { Agg.valueOf(agg) }.getOrNull() ?: return null
    return DailyMetricPoint(date, metric, aggEnum, value, n, z30)
}
