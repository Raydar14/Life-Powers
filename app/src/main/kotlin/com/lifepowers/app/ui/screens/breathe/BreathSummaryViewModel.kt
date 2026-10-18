package com.lifepowers.app.ui.screens.breathe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifepowers.app.domain.BreathGuide
import com.lifepowers.app.domain.BreathRepository
import com.lifepowers.app.domain.BreathSessionController
import com.lifepowers.app.domain.BreathSessionRecord
import com.lifepowers.app.domain.SavedBreathPattern
import com.lifepowers.core.Breathing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BreathSummaryUiState(
    val loaded: Boolean = false,
    val record: BreathSessionRecord? = null,
    val sessionRunning: Boolean = false,
    val busy: Boolean = false,
) {
    val canBreatheAgain: Boolean
        get() = record != null && !sessionRunning && !busy && Breathing.isAllowed(record.pattern)
}

private data class SummaryCore(val loaded: Boolean = false, val record: BreathSessionRecord? = null, val busy: Boolean = false)

@HiltViewModel
class BreathSummaryViewModel @Inject constructor(
    private val breathRepository: BreathRepository,
    private val controller: BreathSessionController,
) : ViewModel() {

    private val core = MutableStateFlow(SummaryCore())

    val state: StateFlow<BreathSummaryUiState> = combine(core, controller.live.map { it != null }.distinctUntilChanged()) { c, running ->
        BreathSummaryUiState(loaded = c.loaded, record = c.record, sessionRunning = running, busy = c.busy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BreathSummaryUiState(sessionRunning = controller.live.value != null))

    private val _started = MutableStateFlow(false)
    val started: StateFlow<Boolean> = _started.asStateFlow()

    private var loadedId: Long? = null

    fun load(sessionId: Long) {
        if (loadedId == sessionId) return
        loadedId = sessionId
        viewModelScope.launch {
            val record = if (sessionId > 0) breathRepository.session(sessionId) else null
            core.value = SummaryCore(loaded = true, record = record)
        }
    }

    /** Starts the same pattern again, with the saved pattern's guide and length when it is still saved. */
    fun breatheAgain() {
        if (!state.value.canBreatheAgain) return
        val record = core.value.record ?: return
        core.update { it.copy(busy = true) }
        viewModelScope.launch {
            val saved = record.patternId?.let { id -> breathRepository.patterns.first().firstOrNull { it.id == id } }
            val toStart = if (saved != null && saved.pattern == record.pattern) {
                saved
            } else {
                SavedBreathPattern(
                    id = 0,
                    name = record.patternName,
                    pattern = record.pattern,
                    guide = saved?.guide ?: BreathGuide(),
                    sessionMin = saved?.sessionMin ?: 5,
                )
            }
            if (controller.live.value == null) {
                controller.start(toStart, record.goalId)
                _started.value = true
            }
            core.update { it.copy(busy = false) }
        }
    }

    fun consumeStarted() {
        _started.value = false
    }
}
