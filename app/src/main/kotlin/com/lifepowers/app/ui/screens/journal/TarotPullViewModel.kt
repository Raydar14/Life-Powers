package com.lifepowers.app.ui.screens.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifepowers.app.domain.JournalRepository
import com.lifepowers.app.domain.TarotPull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** The editable copy of one pull. Assistant fields are carried through untouched. */
data class PullForm(
    val id: Long = 0,
    val entryId: Long? = null,
    val localDate: LocalDate = LocalDate.now(),
    val card: String = "",
    val reversed: Boolean = false,
    val spread: String = "",
    val position: String = "",
    val deck: String = "",
    val note: String = "",
    val interpretation: String? = null,
    val interpretationFromAssistant: Boolean = false,
    val createdUtcMs: Long = System.currentTimeMillis(),
) {
    fun toPull(): TarotPull = TarotPull(
        id = id,
        entryId = entryId,
        localDate = localDate,
        deck = deck.trim().ifBlank { null },
        spread = spread.trim().ifBlank { null },
        position = position.trim().ifBlank { null },
        card = card.trim(),
        reversed = reversed,
        note = note.trim().ifBlank { null },
        interpretation = interpretation,
        interpretationFromAssistant = interpretationFromAssistant,
        createdUtcMs = createdUtcMs,
    )

    companion object {
        fun of(p: TarotPull) = PullForm(
            id = p.id,
            entryId = p.entryId,
            localDate = p.localDate,
            card = p.card,
            reversed = p.reversed,
            spread = p.spread.orEmpty(),
            position = p.position.orEmpty(),
            deck = p.deck.orEmpty(),
            note = p.note.orEmpty(),
            interpretation = p.interpretation,
            interpretationFromAssistant = p.interpretationFromAssistant,
            createdUtcMs = p.createdUtcMs,
        )
    }
}

data class PullUiState(
    val form: PullForm = PullForm(),
    val loaded: Boolean = false,
    val missing: Boolean = false,
    val saving: Boolean = false,
    val notice: String? = null,
    val closed: Boolean = false,
)

@HiltViewModel
class TarotPullViewModel @Inject constructor(private val journalRepository: JournalRepository) : ViewModel() {

    private val _state = MutableStateFlow(PullUiState())
    val state: StateFlow<PullUiState> = _state.asStateFlow()

    private var started = false

    /** Called once per screen. pullId 0 starts a new pull for today, attached to [entryId] when it is not 0. */
    fun load(pullId: Long, entryId: Long) {
        if (started) return
        started = true
        val attachTo = entryId.takeIf { it != 0L }
        viewModelScope.launch {
            if (pullId == 0L) {
                _state.update { it.copy(loaded = true, form = PullForm(entryId = attachTo)) }
                val deck = lastDeck()
                if (deck != null) _state.update { s -> if (s.form.deck.isBlank()) s.copy(form = s.form.copy(deck = deck)) else s }
            } else {
                val found = findPull(pullId, attachTo)
                _state.update {
                    if (found == null) it.copy(loaded = true, missing = true) else it.copy(loaded = true, form = PullForm.of(found))
                }
            }
        }
    }

    /** The deck named on the most recent pull of the last 90 days. */
    private suspend fun lastDeck(): String? {
        val today = LocalDate.now()
        val recent = runCatching { journalRepository.pulls(today.minusDays(90), today).first() }.getOrNull() ?: return null
        return recent
            .filter { !it.deck.isNullOrBlank() }
            .maxWithOrNull(compareBy<TarotPull> { it.localDate }.thenBy { it.createdUtcMs })
            ?.deck
    }

    private suspend fun findPull(pullId: Long, entryId: Long?): TarotPull? {
        if (entryId != null) {
            val fromEntry = runCatching { journalRepository.entry(entryId).first() }.getOrNull()?.pulls?.firstOrNull { it.id == pullId }
            if (fromEntry != null) return fromEntry
        }
        // The contract has no single pull lookup, so search a wide range of dates once.
        val today = LocalDate.now()
        return runCatching { journalRepository.pulls(LocalDate.of(1900, 1, 1), today.plusYears(5)).first() }
            .getOrNull()
            ?.firstOrNull { it.id == pullId }
    }

    fun setCard(card: String) = edit { it.copy(card = card.trim()) }
    fun setReversed(reversed: Boolean) = edit { it.copy(reversed = reversed) }
    fun setSpread(spread: String) = edit { it.copy(spread = spread) }
    fun setPosition(position: String) = edit { it.copy(position = position) }
    fun setDeck(deck: String) = edit { it.copy(deck = deck) }
    fun setNote(note: String) = edit { it.copy(note = note) }

    private fun edit(change: (PullForm) -> PullForm) {
        _state.update { it.copy(form = change(it.form), notice = null) }
    }

    fun save() {
        val s = _state.value
        if (s.saving) return
        if (s.form.card.isBlank()) {
            _state.update { it.copy(notice = "Choose a card first.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val ok = try {
                journalRepository.savePull(_state.value.form.toPull())
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            _state.update {
                if (ok) it.copy(saving = false, closed = true) else it.copy(saving = false, notice = "That did not save just now. Try again in a moment.")
            }
        }
    }

    fun delete() {
        val id = _state.value.form.id
        if (id == 0L) {
            _state.update { it.copy(closed = true) }
            return
        }
        viewModelScope.launch {
            val ok = runCatching { journalRepository.deletePull(id) }.isSuccess
            _state.update { if (ok) it.copy(closed = true) else it.copy(notice = "That did not delete just now. Try again in a moment.") }
        }
    }
}
