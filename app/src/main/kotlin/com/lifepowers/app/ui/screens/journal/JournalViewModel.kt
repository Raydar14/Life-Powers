package com.lifepowers.app.ui.screens.journal

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifepowers.app.domain.BehaviorEvent
import com.lifepowers.app.domain.EventKinds
import com.lifepowers.app.domain.EventRepository
import com.lifepowers.app.domain.JournalEntry
import com.lifepowers.app.domain.JournalRepository
import com.lifepowers.app.domain.Photo
import com.lifepowers.app.domain.nowZoneOffsetMin
import com.lifepowers.app.ui.components.Fmt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class JournalUiState(
    val today: LocalDate = LocalDate.now(),
    val events: List<BehaviorEvent> = emptyList(),
    val cravingCount: Int = 0,
    /** Entries from the last 30 days, newest first. */
    val entries: List<JournalEntry> = emptyList(),
    val loaded: Boolean = false,
)

@HiltViewModel
class JournalViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val journalRepository: JournalRepository,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()
    private val thumbs = PhotoThumbs(journalRepository, targetPx = 200)

    private val recentEntries = journalRepository
        .entries(today.minusDays(29), today)
        .catch { emit(emptyList()) }

    val state: StateFlow<JournalUiState> = combine(
        eventRepository.between(Fmt.startOfDayMs(today), Fmt.endOfDayMs(today)),
        recentEntries,
    ) { events, entries ->
        JournalUiState(
            today = today,
            // Timeline moments the repository keeps for entries and pulls are shown as entries, not here.
            events = events
                .filterNot { (it.kind == EventKinds.JOURNAL || it.kind == EventKinds.TAROT_PULL) && it.source != "manual" }
                .sortedByDescending { it.startUtcMs },
            cravingCount = eventRepository.countOfKind(EventKinds.CRAVING),
            entries = entries.sortedWith(compareByDescending<JournalEntry> { it.localDate }.thenByDescending { it.createdUtcMs }),
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), JournalUiState())

    /** A small decoded thumbnail for a list tile, cached for this screen. */
    suspend fun thumb(photo: Photo): ImageBitmap? = thumbs.load(photo)

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
