package com.lifepowers.app.ui.screens.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifepowers.app.domain.AssistantFeatures
import com.lifepowers.app.domain.AssistantPreview
import com.lifepowers.app.domain.AssistantRepository
import com.lifepowers.app.domain.AssistantResult
import com.lifepowers.app.domain.CheckinRecord
import com.lifepowers.app.voice.CheckinSessionController
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

/** The assistant's richer reading of one saved check-in's words. */
data class RicherReadingState(
    val recordStartUtcMs: Long,
    /** Set while the preview dialog is open. */
    val preview: AssistantPreview? = null,
    val sending: Boolean = false,
    val ratings: AssistantResult.Ratings? = null,
    val error: String? = null,
)

/** Thin wrapper over the voice controller so the screen only sees flows and plain calls. */
@HiltViewModel
class CheckinViewModel @Inject constructor(
    private val controller: CheckinSessionController,
    private val assistantRepository: AssistantRepository,
) : ViewModel() {

    /** True only while the ratings feature is available; the button disappears otherwise. */
    val richerAvailable: StateFlow<Boolean> = assistantRepository.available(AssistantFeatures.CHECKIN)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _richer = MutableStateFlow<RicherReadingState?>(null)
    val richer: StateFlow<RicherReadingState?> = _richer.asStateFlow()

    val state: StateFlow<CheckinSessionController.State> = controller.state
    val prompt: StateFlow<String> = controller.currentPrompt

    private val _symptomsSaved = MutableStateFlow(false)
    val symptomsSaved: StateFlow<Boolean> = _symptomsSaved.asStateFlow()

    private val _behaviorsLogged = MutableStateFlow(false)
    val behaviorsLogged: StateFlow<Boolean> = _behaviorsLogged.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    fun nextPrompt() = controller.nextPrompt()

    fun start() {
        _notice.value = null
        controller.start()
    }

    fun stop() = controller.stop()

    fun reset() {
        _richer.value = null
        _symptomsSaved.value = false
        _behaviorsLogged.value = false
        _notice.value = null
        controller.reset()
    }

    fun permissionDenied() {
        _notice.value = "The microphone is off for this app. Allow it in system settings to check in by voice."
    }

    fun suggestions(record: CheckinRecord): List<String> = CheckinSessionController.suggestions(record)

    fun saveSymptoms(record: CheckinRecord, kinds: List<String>) {
        viewModelScope.launch {
            controller.confirmSymptoms(record, kinds)
            _symptomsSaved.value = true
        }
    }

    fun logBehaviors(record: CheckinRecord, kinds: List<String>) {
        viewModelScope.launch {
            controller.logSpokenBehaviors(record, kinds)
            _behaviorsLogged.value = true
        }
    }

    // Assistant: a richer reading of the words

    /** Opens the preview for this record's transcript. Nothing is sent until [sendRicher]. */
    fun askRicher(record: CheckinRecord) {
        val transcript = record.transcript?.trim().orEmpty()
        if (transcript.isEmpty() || record.crisisFlag || !richerAvailable.value) return
        if (_richer.value?.sending == true) return
        val preview = assistantRepository.previewCheckin(transcript)
        _richer.value = RicherReadingState(recordStartUtcMs = record.startUtcMs, preview = preview)
    }

    fun cancelRicher() {
        val current = _richer.value ?: return
        if (current.sending) return
        _richer.value = if (current.ratings != null) current.copy(preview = null) else null
    }

    fun sendRicher(record: CheckinRecord) {
        val current = _richer.value ?: return
        val transcript = record.transcript?.trim().orEmpty()
        if (current.preview == null || current.sending || transcript.isEmpty()) return
        viewModelScope.launch {
            _richer.update { it?.copy(sending = true) }
            val result = try {
                assistantRepository.rateCheckin(record.startUtcMs, record.zoneOffsetMin, transcript)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AssistantResult.Failed("Could not reach the assistant. Check your connection.")
            }
            _richer.update { state ->
                val base = state ?: RicherReadingState(recordStartUtcMs = record.startUtcMs)
                when (result) {
                    is AssistantResult.Ratings -> base.copy(preview = null, sending = false, ratings = result, error = null)
                    is AssistantResult.Failed -> base.copy(preview = null, sending = false, error = result.message)
                    else -> base.copy(preview = null, sending = false, error = "The assistant's answer could not be read. Try again.")
                }
            }
        }
    }
}
