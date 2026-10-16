package com.lifepowers.app.ui.screens.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifepowers.app.domain.BehaviorEvent
import com.lifepowers.app.domain.EventKinds
import com.lifepowers.app.domain.EventRepository
import com.lifepowers.app.domain.nowZoneOffsetMin
import com.lifepowers.app.ui.components.Fmt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class JournalUiState(
    val today: LocalDate = LocalDate.now(),
    val events: List<BehaviorEvent> = emptyList(),
    val cravingCount: Int = 0,
    val loaded: Boolean = false,
)

@HiltViewModel
class JournalViewModel @Inject constructor(private val eventRepository: EventRepository) : ViewModel() {

    private val today: LocalDate = LocalDate.now()

    val state: StateFlow<JournalUiState> = eventRepository
        .between(Fmt.startOfDayMs(today), Fmt.endOfDayMs(today))
        .map { events ->
            JournalUiState(
                today = today,
                events = events.sortedByDescending { it.startUtcMs },
                cravingCount = eventRepository.countOfKind(EventKinds.CRAVING),
                loaded = true,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), JournalUiState())

    fun add(kind: String, quantity: Double? = null, note: String? = null, unit: String? = null) {
        viewModelScope.launch {
            eventRepository.add(
                BehaviorEvent(
                    kind = kind,
                    startUtcMs = System.currentTimeMillis(),
                    zoneOffsetMin = nowZoneOffsetMin(),
                    quantity = quantity,
                    unit = unit,
                    note = note?.trim()?.takeIf { it.isNotEmpty() },
                    source = "manual",
                ),
            )
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { eventRepository.delete(id) }
    }
}
