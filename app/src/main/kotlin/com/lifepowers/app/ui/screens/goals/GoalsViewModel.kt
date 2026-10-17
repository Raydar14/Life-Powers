package com.lifepowers.app.ui.screens.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifepowers.app.domain.GoalRepository
import com.lifepowers.app.domain.GoalView
import com.lifepowers.core.Cadence
import com.lifepowers.core.PeriodState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

@HiltViewModel
class GoalsViewModel @Inject constructor(private val goalRepository: GoalRepository) : ViewModel() {

    private val today: LocalDate = LocalDate.now()
    private val filter = MutableStateFlow<Cadence?>(null)

    val state: StateFlow<GoalsUiState> = combine(goalRepository.views(today), filter) { views, f ->
        GoalsUiState(today = today, filter = f, all = views.filterNot { it.goal.archived }, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GoalsUiState(today = today))

    fun setFilter(cadence: Cadence?) {
        filter.value = cadence
    }

    fun check(goalId: Long) {
        viewModelScope.launch { goalRepository.check(goalId, today) }
    }
}
