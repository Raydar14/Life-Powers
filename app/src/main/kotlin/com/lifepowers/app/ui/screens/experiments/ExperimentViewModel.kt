package com.lifepowers.app.ui.screens.experiments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifepowers.app.domain.ExperimentRepository
import com.lifepowers.app.domain.ExperimentView
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ExperimentUiState(
    val today: LocalDate = LocalDate.now(),
    val view: ExperimentView? = null,
    val loaded: Boolean = false,
    val deleted: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExperimentViewModel @Inject constructor(private val repository: ExperimentRepository) : ViewModel() {

    private val experimentId = MutableStateFlow<Long?>(null)
    private val deleted = MutableStateFlow(false)

    val state: StateFlow<ExperimentUiState> = combine(experimentId.filterNotNull(), experimentDays()) { id, today -> id to today }
        .flatMapLatest { (id, today) ->
            combine(repository.view(id, today), deleted) { view, gone ->
                ExperimentUiState(today = today, view = view, loaded = true, deleted = gone)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExperimentUiState())

    fun setExperiment(id: Long) {
        experimentId.value = id
    }

    fun setAdhered(date: LocalDate, adhered: Boolean?) {
        val id = experimentId.value ?: return
        viewModelScope.launch { repository.setAdhered(id, date, adhered) }
    }

    fun end() {
        val id = experimentId.value ?: return
        viewModelScope.launch { repository.end(id, LocalDate.now()) }
    }

    fun delete() {
        val id = experimentId.value ?: return
        viewModelScope.launch {
            repository.delete(id)
            deleted.value = true
        }
    }
}
