package com.lifepowers.app.ui.screens.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifepowers.app.domain.GoalRepository
import com.lifepowers.app.domain.GoalView
import com.lifepowers.core.Cadence
import com.lifepowers.core.PeriodState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class GoalsUiState(
    val today: LocalDate = LocalDate.now(),
    /** Null shows every cadence. */
    val filter: Cadence? = null,
    val all: List<GoalView> = emptyList(),
    val loaded: Boolean = false,
) {
    val shown: List<GoalView> get() = if (filter == null) all else all.filter { it.goal.cadence == filter }

    /** Goals whose open period is met or mostly met. */
    val onTrack: Int get() = all.count { it.current.state == PeriodState.MET || it.current.state == PeriodState.MOSTLY }
}

/**
 * The local date, read again every minute, so a goal screen left open overnight moves to the new day
 * instead of showing and checking yesterday.
 */
internal fun localDays(): Flow<LocalDate> = flow {
    while (true) {
        emit(LocalDate.now())
        delay(60_000L)
    }
}.distinctUntilChanged()

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GoalsViewModel @Inject constructor(private val goalRepository: GoalRepository) : ViewModel() {

    private val filter = MutableStateFlow<Cadence?>(null)

    val state: StateFlow<GoalsUiState> = localDays()
        .flatMapLatest { today ->
            combine(goalRepository.views(today), filter) { views, f ->
                GoalsUiState(today = today, filter = f, all = views.filterNot { it.goal.archived }, loaded = true)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GoalsUiState(today = LocalDate.now()))

    fun setFilter(cadence: Cadence?) {
        filter.value = cadence
    }

    fun check(goalId: Long) {
        viewModelScope.launch { goalRepository.check(goalId, LocalDate.now()) }
    }
}
