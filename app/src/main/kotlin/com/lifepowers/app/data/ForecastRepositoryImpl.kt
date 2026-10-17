package com.lifepowers.app.data

import androidx.room.withTransaction
import com.lifepowers.app.domain.ForecastRepository
import com.lifepowers.app.domain.ForecastSnapshot
import com.lifepowers.app.domain.ProfileRepository
import com.lifepowers.app.domain.SkyTierSource
import com.lifepowers.app.world.WorldPrefs
import com.lifepowers.core.Component
import com.lifepowers.core.Forecast
import com.lifepowers.core.ForecastInput
import com.lifepowers.core.ForecastResult
import com.lifepowers.core.Metrics
import com.lifepowers.core.Phase
import com.lifepowers.core.Tier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForecastRepositoryImpl @Inject constructor(
    private val db: LifePowersDatabase,
    private val forecasts: ForecastDao,
    private val daily: DailyMetricDao,
    private val profiles: ProfileRepository,
    private val json: CheckinJson,
    private val skyTiers: SkyTierSource,
    private val worldPrefs: WorldPrefs,
) : ForecastRepository {

    override fun forDay(date: LocalDate): Flow<List<ForecastSnapshot>> =
        forecasts.forDay(Dates.iso(date)).map { rows -> rows.mapNotNull { it.toSnapshot() } }

    override fun dayTiers(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Tier>> =
        forecasts.componentBetween(Component.DAY.name, Dates.iso(from), Dates.iso(to)).map { rows ->
            val out = LinkedHashMap<LocalDate, Tier>()
            rows.forEach { row ->
                val d = Dates.parseIso(row.localDate) ?: return@forEach
                out[d] = enumOrNull<Tier>(row.tier) ?: Tier.UNKNOWN
            }
            out
        }

    /**
     * Each component takes the z30 of its catalog metrics for the date, oriented so positive is
     * good. The hormonal component is a single calendar input from the phase label. Yesterday's
     * stored tier for the same component damps the dead band. The sky component carries the sky
     * panel's own tier and only counts toward the day when transits are on.
     */
    override suspend fun recompute(date: LocalDate) {
        val iso = Dates.iso(date)
        val yesterday = Dates.iso(date.minusDays(1))
        val byKey = daily.forDay(iso).associateBy { it.metric to it.agg }

        val results = ArrayList<ForecastResult>()
        Forecast.componentInputs.forEach { (component, weights) ->
            val inputs = weights.mapNotNull { (metric, weight) ->
                val agg = Metrics.spec(metric).aggs.firstOrNull() ?: return@mapNotNull null
                val z = byKey[metric to agg.name]?.z30 ?: return@mapNotNull null
                val oriented = Forecast.orient(metric, z) ?: return@mapNotNull null
                ForecastInput(metric, oriented, weight)
            }
            results += Forecast.compute(component, inputs, previousTier(yesterday, component))
        }

        val phase = runCatching { profiles.phaseFor(date).phase }.getOrNull()
        val hormonalInputs = when (phase) {
            Phase.LUTEAL -> listOf(ForecastInput("phase", -0.3))
            Phase.FOLLICULAR -> listOf(ForecastInput("phase", 0.3))
            else -> emptyList()
        }
        results += Forecast.compute(Component.HORMONAL, hormonalInputs, previousTier(yesterday, Component.HORMONAL))

        skyResult(date)?.let { results += it }

        results += Forecast.day(results, previousTier(yesterday, Component.DAY))

        val rows = results.map {
            ForecastEntity(iso, it.component.name, it.tier.name, it.score, it.inputsUsed, json.encodeStrings(it.reasons))
        }
        db.withTransaction {
            forecasts.deleteForDay(iso)
            forecasts.upsertAll(rows)
        }
    }

    /** The sky tier with a score only when transits are on, so [Forecast.day] ignores it otherwise. */
    private suspend fun skyResult(date: LocalDate): ForecastResult? = runCatching {
        val (tier, reasons) = skyTiers.tierFor(date)
        val score = if (worldPrefs.skySettings.first().transitsOn) skyScore(tier) else null
        ForecastResult(Component.SKY, tier, score, if (score != null) 1 else 0, reasons)
    }.getOrNull()

    private fun skyScore(tier: Tier): Double? = when (tier) {
        Tier.BEST -> 1.0
        Tier.GOOD -> 0.4
        Tier.NOT_GREAT -> -0.4
        Tier.LIKELY_HARD -> -1.0
        Tier.UNKNOWN -> null
    }

    private suspend fun previousTier(isoDate: String, component: Component): Tier? =
        forecasts.get(isoDate, component.name)?.let { enumOrNull<Tier>(it.tier) }

    private fun ForecastEntity.toSnapshot(): ForecastSnapshot? {
        val d = Dates.parseIso(localDate) ?: return null
        val c = enumOrNull<Component>(component) ?: return null
        return ForecastSnapshot(d, c, enumOrNull<Tier>(tier) ?: Tier.UNKNOWN, score, inputsUsed, json.decodeStrings(reasonsJson))
    }
}
