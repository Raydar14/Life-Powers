package com.lifepowers.app.ui.screens.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifepowers.app.domain.GoalRepository
import com.lifepowers.app.domain.GoalView
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class HabitUiState(
    val today: LocalDate = LocalDate.now(),
    val view: GoalView? = null,
    val loaded: Boolean = false,
)

@HiltViewModel
class HabitViewModel @Inject constructor(private val goalRepository: GoalRepository) : ViewModel() {

    private val today: LocalDate = LocalDate.now()
    private val goalId = MutableStateFlow<Long?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<HabitUiState> = goalId
        .filterNotNull()
        .flatMapLatest { id -> goalRepository.view(id, today) }
        .map { HabitUiState(today = today, view = it, loaded = true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HabitUiState(today = today))

    fun setGoal(id: Long) {
        goalId.value = id
    }

    fun check() {
        val id = goalId.value ?: return
        viewModelScope.launch { goalRepository.check(id, today) }
    }

    fun uncheck(checkId: Long) {
        viewModelScope.launch { goalRepository.uncheck(checkId) }
    }
}
