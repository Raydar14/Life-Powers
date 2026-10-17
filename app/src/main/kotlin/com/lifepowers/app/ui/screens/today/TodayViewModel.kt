package com.lifepowers.app.ui.screens.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifepowers.app.domain.CheckinRecord
import com.lifepowers.app.domain.CheckinRepository
import com.lifepowers.app.domain.DailyMetricPoint
import com.lifepowers.app.domain.EventKinds
import com.lifepowers.app.domain.EventRepository
import com.lifepowers.app.domain.ForecastRepository
import com.lifepowers.app.domain.ForecastSnapshot
import com.lifepowers.app.domain.GoalRepository
import com.lifepowers.app.domain.GoalView
import com.lifepowers.app.domain.MetricRepository
import com.lifepowers.app.domain.PhaseForDay
import com.lifepowers.app.domain.ProfileRepository
import com.lifepowers.app.domain.SkyToday
import com.lifepowers.app.domain.UserProfileState
import com.lifepowers.app.domain.WorldRepository
import com.lifepowers.app.ui.components.Fmt
import com.lifepowers.core.Agg
import com.lifepowers.core.Cadence
import com.lifepowers.core.Component
import com.lifepowers.core.Metrics
import com.lifepowers.core.Tier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class TodayUiState(
    val today: LocalDate = LocalDate.now(),
    val displayName: String? = null,
    val dayTier: Tier = Tier.UNKNOWN,
    val dayReasons: List<String> = emptyList(),
    /** Seven days ending today, oldest first. */
    val week: List<Pair<LocalDate, Tier>> = emptyList(),
    val componentTiers: Map<Component, Tier> = emptyMap(),
    val readiness: Double? = null,
    val hrv: Double? = null,
    val hrvZ: Double? = null,
    val glucoseOvernight: Double? = null,
    val glucoseMax: Double? = null,
    val systolic: Double? = null,
    val diastolic: Double? = null,
    val phase: PhaseForDay? = null,
    val cravingCount: Int = 0,
    val latestCheckin: CheckinRecord? = null,
    /** Today's sky and weather, null until the world repository answers. */
    val sky: SkyToday? = null,
    /** Habits checked today and habits in all, null until goals have loaded. */
    val habitsDone: Int? = null,
    val habitsTotal: Int? = null,
    val loaded: Boolean = false,
) {
    fun tier(component: Component): Tier = componentTiers[component] ?: Tier.UNKNOWN

    val dayTitle: String
        get() = when (dayTier) {
            Tier.BEST -> "One of your best days"
            Tier.GOOD -> "A good day ahead"
            Tier.NOT_GREAT -> "A softer day"
            Tier.LIKELY_HARD -> "Go gently today"
            Tier.UNKNOWN -> "Not enough yet to say"
        }

    val daySentence: String
        get() {
            if (dayTier == Tier.UNKNOWN) {
                return "A forecast needs about three inputs with a baseline behind them. Keep syncing and checking in, and this card will fill in."
            }
            val parts = dayReasons.map { Fmt.reason(it) }.distinct().take(3)
            return if (parts.isEmpty()) "Built from what has synced so far." else "Built mostly from ${Fmt.joinSentence(parts)}."
        }

    /** The gap rule from section 5.4, read off the latest check-in. */
    val gapMessage: String?
        get() {
            val record = latestCheckin ?: return null
            val indices = record.indices ?: return null
            val valence = record.words?.valence ?: return null
            val flat = (indices.flatness ?: 0.0) > 1.5
            val tense = (indices.tension ?: 0.0) > 1.5
            if (valence < 0.5 || (!flat && !tense)) return null
            val how = when {
                flat && tense -> "flatter and tenser"
                flat -> "flatter and slower"
                else -> "tenser and quicker"
            }
            return "You described things as fine. Your voice has been $how than usual. Worth a moment?"
        }

    val mindSummary: String
        get() {
            val record = latestCheckin ?: return "No check-in yet. A minute of talking starts your baseline."
            val indices = record.indices
            if (indices == null || (indices.energy == null && indices.flatness == null && indices.tension == null)) {
                return "Your voice baseline is still forming. Keep talking once a day."
            }
            val energy = indices.energy
            return when {
                energy != null && energy > 0.5 -> "Last time you sounded brighter than usual."
                energy != null && energy < -0.5 -> "Last time you sounded a little quieter than usual."
                else -> "Last time you sounded about like yourself."
            }
        }
}

private data class Base(
    val profile: UserProfileState,
    val snapshots: List<ForecastSnapshot>,
    val tiers: Map<LocalDate, Tier>,
    val points: List<DailyMetricPoint>,
    val phase: PhaseForDay?,
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    profileRepository: ProfileRepository,
    forecastRepository: ForecastRepository,
    metricRepository: MetricRepository,
    eventRepository: EventRepository,
    checkinRepository: CheckinRepository,
    worldRepository: WorldRepository,
    goalRepository: GoalRepository,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()

    private val phaseFlow: Flow<PhaseForDay?> = flow { emit(runCatching { profileRepository.phaseFor(today) }.getOrNull()) }

    private val base: Flow<Base> = combine(
        profileRepository.profile,
        forecastRepository.forDay(today),
        forecastRepository.dayTiers(today.minusDays(6), today),
        metricRepository.dailyBetween(today.minusDays(2), today),
        phaseFlow,
    ) { profile, snapshots, tiers, points, phase -> Base(profile, snapshots, tiers, points, phase) }

    /** Re-counts cravings whenever an event in the last month changes. */
    private val cravingCount: Flow<Int> = eventRepository
        .between(Fmt.startOfDayMs(today.minusDays(30)), Fmt.endOfDayMs(today.plusDays(1)))
        .map { eventRepository.countOfKind(EventKinds.CRAVING) }

    /** Emits null first so Today never waits on the astronomy. */
    private val skyFlow: Flow<SkyToday?> = worldRepository.skyToday(today)
        .map<SkyToday, SkyToday?> { it }
        .onStart { emit(null) }
        .catch { emit(null) }

    private val goalsFlow: Flow<List<GoalView>?> = goalRepository.views(today)
        .map<List<GoalView>, List<GoalView>?> { it }
        .onStart { emit(null) }
        .catch { emit(null) }

    val state: StateFlow<TodayUiState> = combine(base, cravingCount, checkinRepository.recent(3), skyFlow, goalsFlow) { b, cravings, checkins, sky, goals ->
        val habits = goals?.filter { it.goal.isHabit && !it.goal.archived }
        val latest = latestByKey(b.points)
        fun value(metric: String, agg: Agg): Double? = latest[metric to agg]?.value
        TodayUiState(
            today = today,
            displayName = b.profile.displayName?.takeIf { it.isNotBlank() },
            dayTier = b.snapshots.firstOrNull { it.component == Component.DAY }?.tier ?: Tier.UNKNOWN,
            dayReasons = b.snapshots.firstOrNull { it.component == Component.DAY }?.reasons ?: emptyList(),
            week = (6 downTo 0).map { back -> val d = today.minusDays(back.toLong()); d to (b.tiers[d] ?: Tier.UNKNOWN) },
            componentTiers = b.snapshots.associate { it.component to it.tier },
            readiness = value(Metrics.SCORE_READINESS, Agg.LAST),
            hrv = value(Metrics.HRV_NIGHT_AVG, Agg.LAST),
            hrvZ = latest[Metrics.HRV_NIGHT_AVG to Agg.LAST]?.z30,
            glucoseOvernight = value(Metrics.GLUCOSE_OVERNIGHT_MEAN, Agg.LAST),
            glucoseMax = value(Metrics.GLUCOSE_SAMPLE, Agg.MAX),
            systolic = value(Metrics.BP_SYSTOLIC, Agg.MEAN),
            diastolic = value(Metrics.BP_DIASTOLIC, Agg.MEAN),
            phase = b.phase,
            cravingCount = cravings,
            latestCheckin = checkins.maxByOrNull { it.startUtcMs },
            sky = sky,
            habitsDone = habits?.count { doneToday(it) },
            habitsTotal = habits?.size,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TodayUiState())

    /** Checked today, or a daily goal already met today (automatic checks count). */
    private fun doneToday(view: GoalView): Boolean =
        view.checksToday.isNotEmpty() || (view.goal.cadence == Cadence.DAILY && view.current.fraction >= 1.0)

    private fun latestByKey(points: List<DailyMetricPoint>): Map<Pair<String, Agg>, DailyMetricPoint> {
        val out = HashMap<Pair<String, Agg>, DailyMetricPoint>()
        for (p in points) {
            val key = p.metric to p.agg
            val existing = out[key]
            if (existing == null || p.localDate.isAfter(existing.localDate)) out[key] = p
        }
        return out
    }
}
