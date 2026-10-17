package com.lifepowers.app.ui.screens.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifepowers.app.domain.AssistantFeatures
import com.lifepowers.app.domain.AssistantPreview
import com.lifepowers.app.domain.AssistantRepository
import com.lifepowers.app.domain.AssistantResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NarrativeUiState(
    /** Set while the preview dialog is open. */
    val preview: AssistantPreview? = null,
    val sending: Boolean = false,
    val text: String? = null,
    val error: String? = null,
)

@HiltViewModel
class AssistantNarrativeViewModel @Inject constructor(private val repository: AssistantRepository) : ViewModel() {

    val available: StateFlow<Boolean> = repository.available(AssistantFeatures.NARRATIVE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _state = MutableStateFlow(NarrativeUiState())
    val state: StateFlow<NarrativeUiState> = _state.asStateFlow()

    /** Exactly what the preview showed, so the call sends the same lines. */
    private var pending: Pair<List<String>, List<String>>? = null

    fun ask(findings: List<String>, goals: List<String>) {
        if (_state.value.sending) return
        pending = findings to goals
        _state.update { it.copy(preview = repository.previewNarrative(findings, goals), error = null) }
    }

    fun cancel() {
        if (_state.value.sending) return
        pending = null
        _state.update { it.copy(preview = null) }
    }

    fun send() {
        val (findings, goals) = pending ?: return
        if (_state.value.sending) return
        viewModelScope.launch {
            _state.update { it.copy(sending = true) }
            val result = try {
                repository.weeklyNarrative(findings, goals)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AssistantResult.Failed("Could not reach the assistant. Check your connection.")
            }
            pending = null
            _state.update {
                when (result) {
                    is AssistantResult.Ok -> it.copy(preview = null, sending = false, text = result.text, error = null)
                    is AssistantResult.Failed -> it.copy(preview = null, sending = false, error = result.message)
                    else -> it.copy(preview = null, sending = false, error = "The assistant's answer could not be read. Try again.")
                }
            }
        }
    }
}
