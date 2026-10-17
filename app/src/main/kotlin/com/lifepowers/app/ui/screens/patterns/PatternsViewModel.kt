package com.lifepowers.app.ui.screens.patterns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifepowers.app.domain.PatternRepository
import com.lifepowers.core.Insight
import com.lifepowers.core.Patterns
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PatternsUiState(
    val insights: List<Insight> = emptyList(),
    val daysWithData: Int = 0,
    val computedUtcMs: Long? = null,
    val looking: Boolean = false,
    val loaded: Boolean = false,
) {
    /** Patterns need about two weeks of days before anything can stand out. */
    val enoughDays: Boolean get() = daysWithData >= Patterns.MIN_N
}

@HiltViewModel
class PatternsViewModel @Inject constructor(private val patternRepository: PatternRepository) : ViewModel() {

    private val looking = MutableStateFlow(false)

    val state: StateFlow<PatternsUiState> = combine(
        patternRepository.insights,
        patternRepository.coverage,
        patternRepository.computedUtcMs,
        looking,
    ) { insights, coverage, computed, busy ->
        PatternsUiState(
            insights = insights,
            daysWithData = coverage.daysWithData,
            computedUtcMs = computed,
            looking = busy,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PatternsUiState())

    fun recompute() {
        if (looking.value) return
        looking.value = true
        viewModelScope.launch {
            try {
                patternRepository.recompute()
            } finally {
                looking.value = false
            }
        }
    }
}
