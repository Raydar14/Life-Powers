package com.lifepowers.app.ui.screens.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifepowers.app.domain.Goal
import com.lifepowers.app.domain.GoalRepository
import com.lifepowers.app.domain.TimerController
import com.lifepowers.app.domain.TimerSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TimerUiState(
    val running: TimerSession? = null,
    val goals: List<Goal> = emptyList(),
    val starting: Boolean = false,
) {
    fun nameOf(goalId: Long?): String? = if (goalId == null) null else goals.firstOrNull { it.id == goalId }?.name
}

@HiltViewModel
class TimerViewModel @Inject constructor(
    private val timerController: TimerController,
    goalRepository: GoalRepository,
) : ViewModel() {

    private val starting = MutableStateFlow(false)

    val state: StateFlow<TimerUiState> = combine(timerController.running, goalRepository.goals, starting) { running, goals, busy ->
        TimerUiState(running = running, goals = goals.filterNot { it.archived }, starting = busy && running == null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TimerUiState(running = timerController.running.value))

    private val _done = MutableStateFlow(false)
    val done: StateFlow<Boolean> = _done.asStateFlow()

    /** Starts a session unless one is already running, which then shows instead. */
    fun start(goalId: Long?, plannedMin: Int?) {
        if (timerController.running.value != null || starting.value) return
        starting.value = true
        viewModelScope.launch {
            try {
                timerController.start(goalId, plannedMin)
            } finally {
                starting.value = false
            }
        }
    }

    fun stop() {
        viewModelScope.launch {
            timerController.stop()
            _done.value = true
        }
    }

    fun cancel() {
        viewModelScope.launch {
            timerController.cancel()
            _done.value = true
        }
    }
}
