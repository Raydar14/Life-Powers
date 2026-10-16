package com.lifepowers.app.ui.screens.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifepowers.app.domain.CheckinRecord
import com.lifepowers.app.voice.CheckinSessionController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Thin wrapper over the voice controller so the screen only sees flows and plain calls. */
@HiltViewModel
class CheckinViewModel @Inject constructor(private val controller: CheckinSessionController) : ViewModel() {

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
}
