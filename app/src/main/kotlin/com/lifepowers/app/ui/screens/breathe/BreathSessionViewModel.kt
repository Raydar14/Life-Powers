package com.lifepowers.app.ui.screens.breathe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifepowers.app.domain.BreathLive
import com.lifepowers.app.domain.BreathSessionController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** How the session screen ends: the saved session's id, or null when nothing was saved. */
data class SessionOutcome(val savedId: Long?)

/** Sessions shorter than this are discarded by the controller, so there is nothing to wait for. */
private const val MIN_SAVED_S = 30.0

@HiltViewModel
class BreathSessionViewModel @Inject constructor(
    private val controller: BreathSessionController,
) : ViewModel() {

    val live: StateFlow<BreathLive?> = controller.live

    private val _outcome = MutableStateFlow<SessionOutcome?>(null)
    val outcome: StateFlow<SessionOutcome?> = _outcome.asStateFlow()

    private val _finishing = MutableStateFlow(false)
    val finishing: StateFlow<Boolean> = _finishing.asStateFlow()

    /** The saved id when the screen opened, so a new save can be told apart. */
    private val savedAtOpen: Long? = controller.lastSavedId.value
    private var ending = false

    init {
        viewModelScope.launch {
            // The controller's service may take a moment to publish the session it was just asked to start.
            val first = withTimeoutOrNull(4_000L) { controller.live.filterNotNull().first() }
            if (first == null) {
                if (!ending) _outcome.value = SessionOutcome(null)
                return@launch
            }
            // The session can also end on its own: the planned length runs out, or it is stopped from the notification.
            controller.live.first { it == null }
            if (ending) return@launch
            val id = withTimeoutOrNull(3_000L) { controller.lastSavedId.first { it != null && it != savedAtOpen } }
            _outcome.value = SessionOutcome(id)
        }
    }

    fun pause() = controller.pause()

    fun resume() = controller.resume()

    /** Ends and saves, then waits for the saved id to appear. */
    fun finish() {
        if (ending) return
        ending = true
        _finishing.value = true
        val before = controller.lastSavedId.value
        val tooShort = (controller.live.value?.elapsedS ?: 0.0) < MIN_SAVED_S
        controller.finish()
        viewModelScope.launch {
            val id = if (tooShort) null else withTimeoutOrNull(8_000L) { controller.lastSavedId.first { it != null && it != before } }
            _outcome.value = SessionOutcome(id)
        }
    }

    fun cancel() {
        if (ending) return
        ending = true
        controller.cancel()
        _outcome.value = SessionOutcome(null)
    }
}
