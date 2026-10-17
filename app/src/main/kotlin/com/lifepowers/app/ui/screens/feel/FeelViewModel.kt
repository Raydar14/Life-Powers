package com.lifepowers.app.ui.screens.feel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifepowers.app.domain.MetricRepository
import com.lifepowers.app.domain.nowZoneOffsetMin
import com.lifepowers.core.FeelWord
import com.lifepowers.core.FeelWords
import com.lifepowers.core.Metrics
import com.lifepowers.core.Sample
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Keys for the tint, which Metrics does not name yet. Unknown keys fall back to a plain daily LAST. */
const val FEEL_TINT_HUE = "feel.tint_hue"
const val FEEL_TINT_LIGHT = "feel.tint_light"

data class FeelUiState(
    val words: List<FeelWord> = FeelWords.builtin,
    val picked: List<FeelWord> = emptyList(),
    val tempo: FeelWords.Tempo? = null,
    /** Dot position as fractions of the field, null until touched. */
    val tintX: Float? = null,
    val tintY: Float? = null,
    val saving: Boolean = false,
) {
    val canSave: Boolean get() = picked.isNotEmpty() || tempo != null || tintX != null
}

@HiltViewModel
class FeelViewModel @Inject constructor(private val metricRepository: MetricRepository) : ViewModel() {

    private val _state = MutableStateFlow(FeelUiState())
    val state: StateFlow<FeelUiState> = _state.asStateFlow()

    /** Up to two words; a third replaces the oldest. */
    fun toggle(word: FeelWord) {
        _state.value = _state.value.let { s ->
            val next = if (s.picked.any { it.word == word.word }) s.picked.filter { it.word != word.word } else s.picked + word
            s.copy(picked = next.takeLast(2))
        }
    }

    fun addOwnWord(text: String) {
        val clean = text.trim().lowercase()
        if (clean.isEmpty()) return
        val s = _state.value
        val existing = s.words.firstOrNull { it.word == clean }
        val word = existing ?: FeelWords.newUserWord(clean)
        val words = if (existing == null) listOf(word) + s.words else s.words
        _state.value = s.copy(words = words, picked = (s.picked.filter { it.word != clean } + word).takeLast(2))
    }

    fun setTempo(tempo: FeelWords.Tempo) {
        _state.value = _state.value.copy(tempo = if (_state.value.tempo == tempo) null else tempo)
    }

    fun setTint(x: Float, y: Float) {
        _state.value = _state.value.copy(tintX = x.coerceIn(0f, 1f), tintY = y.coerceIn(0f, 1f))
    }

    /** Writes feel.energy, feel.ease, feel.tempo and the tint as samples with source "feel". */
    fun save(onDone: () -> Unit) {
        val s = _state.value
        val now = System.currentTimeMillis()
        val offset = nowZoneOffsetMin()
        val samples = ArrayList<Sample>()
        FeelWords.coordinates(s.picked)?.let { (energy, ease) ->
            samples.add(Sample(Metrics.FEEL_ENERGY, energy, now, null, offset, "feel"))
            samples.add(Sample(Metrics.FEEL_EASE, ease, now, null, offset, "feel"))
        }
        s.tempo?.let { samples.add(Sample(Metrics.FEEL_TEMPO, it.value.toDouble(), now, null, offset, "feel")) }
        if (s.tintX != null && s.tintY != null) {
            samples.add(Sample(FEEL_TINT_HUE, s.tintX.toDouble() * 360.0, now, null, offset, "feel"))
            samples.add(Sample(FEEL_TINT_LIGHT, s.tintY.toDouble(), now, null, offset, "feel"))
        }
        if (samples.isEmpty()) { onDone(); return }
        _state.value = s.copy(saving = true)
        viewModelScope.launch {
            runCatching { metricRepository.upsertSamples(samples) }
            _state.value = _state.value.copy(saving = false)
            onDone()
        }
    }
}
