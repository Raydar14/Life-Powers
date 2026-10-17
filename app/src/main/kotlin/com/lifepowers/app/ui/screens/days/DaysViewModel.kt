package com.lifepowers.app.ui.screens.days

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifepowers.app.domain.BehaviorEvent
import com.lifepowers.app.domain.DailyMetricPoint
import com.lifepowers.app.domain.EventRepository
import com.lifepowers.app.domain.ForecastRepository
import com.lifepowers.app.domain.MetricRepository
import com.lifepowers.app.ui.components.Fmt
import com.lifepowers.core.Tier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class DaysUiState(
    val today: LocalDate = LocalDate.now(),
    val gridStart: LocalDate = LocalDate.now(),
    /** Fourteen days, Monday of last week through Sunday of this week. */
    val days: List<LocalDate> = emptyList(),
    val tiers: Map<LocalDate, Tier> = emptyMap(),
    val selected: LocalDate = LocalDate.now(),
    val selectedPoints: List<DailyMetricPoint> = emptyList(),
    val selectedEvents: List<BehaviorEvent> = emptyList(),
    val loaded: Boolean = false,
)

@HiltViewModel
class DaysViewModel @Inject constructor(
    forecastRepository: ForecastRepository,
    metricRepository: MetricRepository,
    eventRepository: EventRepository,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()
    private val gridStart: LocalDate = today.minusDays(7).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    private val gridEnd: LocalDate = gridStart.plusDays(13)
    private val days: List<LocalDate> = (0 until 14).map { gridStart.plusDays(it.toLong()) }

    private val selected = MutableStateFlow(today)

    val state: StateFlow<DaysUiState> = combine(
        forecastRepository.dayTiers(gridStart, gridEnd),
        metricRepository.dailyBetween(gridStart, gridEnd),
        eventRepository.between(Fmt.startOfDayMs(gridStart), Fmt.endOfDayMs(gridEnd)),
        selected,
    ) { tiers, points, events, sel ->
        DaysUiState(
            today = today,
            gridStart = gridStart,
            days = days,
            tiers = tiers,
            selected = sel,
            selectedPoints = points.filter { it.localDate == sel }.sortedWith(compareBy({ it.metric }, { it.agg.ordinal })),
            selectedEvents = events.filter { Fmt.localDateOf(it.startUtcMs, it.zoneOffsetMin) == sel }.sortedBy { it.startUtcMs },
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DaysUiState(today = today, gridStart = gridStart, days = days))

    fun select(date: LocalDate) {
        if (!date.isAfter(today)) selected.value = date
    }
}
