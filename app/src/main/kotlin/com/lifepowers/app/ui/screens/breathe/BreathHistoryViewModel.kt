package com.lifepowers.app.ui.screens.breathe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifepowers.app.domain.BreathRepository
import com.lifepowers.app.domain.BreathSessionRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BreathHistoryUiState(val loaded: Boolean = false, val sessions: List<BreathSessionRecord> = emptyList())

@HiltViewModel
class BreathHistoryViewModel @Inject constructor(private val breathRepository: BreathRepository) : ViewModel() {

    val state: StateFlow<BreathHistoryUiState> = breathRepository.sessions(limit = 200)
        .map { BreathHistoryUiState(loaded = true, sessions = it.sortedByDescending { s -> s.startUtcMs }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BreathHistoryUiState())

    fun delete(id: Long) {
        viewModelScope.launch { breathRepository.deleteSession(id) }
    }
}
