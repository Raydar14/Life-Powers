package com.lifepowers.app.ui.screens.experiments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifepowers.app.domain.ExperimentRepository
import com.lifepowers.app.domain.ExperimentView
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ExperimentsUiState(
    val today: LocalDate = LocalDate.now(),
    /** Not ended yet, including ones past their last day that wait to be ended. */
    val running: List<ExperimentView> = emptyList(),
    val finished: List<ExperimentView> = emptyList(),
    val loaded: Boolean = false,
) {
    val empty: Boolean get() = running.isEmpty() && finished.isEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExperimentsViewModel @Inject constructor(private val repository: ExperimentRepository) : ViewModel() {

    val state: StateFlow<ExperimentsUiState> = experimentDays()
        .flatMapLatest { today ->
            repository.views(today).map { views ->
                val (running, finished) = views.partition { it.record.endedOn == null }
                ExperimentsUiState(today = today, running = running, finished = finished, loaded = true)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExperimentsUiState())

    fun setAdhered(id: Long, date: LocalDate, adhered: Boolean?) {
        viewModelScope.launch { repository.setAdhered(id, date, adhered) }
    }
}
