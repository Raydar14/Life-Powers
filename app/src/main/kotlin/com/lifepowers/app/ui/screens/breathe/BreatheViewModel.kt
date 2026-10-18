package com.lifepowers.app.ui.screens.breathe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifepowers.app.domain.BreathRepository
import com.lifepowers.app.domain.BreathSessionController
import com.lifepowers.app.domain.HeartDevice
import com.lifepowers.app.domain.HeartLink
import com.lifepowers.app.domain.HeartLinkState
import com.lifepowers.app.domain.SavedBreathPattern
import com.lifepowers.core.Breathing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the heart sensor card and the find sheet show. */
data class HeartUi(
    val state: HeartLinkState = HeartLinkState.OFF,
    val connected: HeartDevice? = null,
    val bpm: Double? = null,
    val nearby: List<HeartDevice> = emptyList(),
    val remembered: HeartDevice? = null,
)

/** The session already running, for the card that leads back to it. */
data class RunningUi(val name: String, val paused: Boolean, val describe: String)

data class BreatheUiState(
    val heart: HeartUi = HeartUi(),
    val patterns: List<SavedBreathPattern> = emptyList(),
    val running: RunningUi? = null,
    val loaded: Boolean = false,
)

@HiltViewModel
class BreatheViewModel @Inject constructor(
    private val breathRepository: BreathRepository,
    private val heartLink: HeartLink,
    private val controller: BreathSessionController,
) : ViewModel() {

    private val lastBpm = heartLink.readings.map { it.lastOrNull()?.bpm }.distinctUntilChanged()

    private val heart = combine(heartLink.state, heartLink.connected, lastBpm, heartLink.nearby, heartLink.remembered) { state, device, bpm, nearby, remembered ->
        HeartUi(
            state = state,
            connected = device,
            bpm = if (state == HeartLinkState.CONNECTED) bpm else null,
            nearby = nearby.sortedByDescending { it.rssi },
            remembered = remembered,
        )
    }

    /** Only whether a session runs, and which, matter here, not every tick. */
    private val running = controller.live
        .map { live -> live?.let { RunningUi(name = it.name, paused = it.paused, describe = it.pattern.describe()) } }
        .distinctUntilChanged()

    val state: StateFlow<BreatheUiState> = combine(heart, breathRepository.patterns, running) { h, patterns, run ->
        BreatheUiState(heart = h, patterns = patterns, running = run, loaded = true)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        BreatheUiState(running = controller.live.value?.let { RunningUi(it.name, it.paused, it.pattern.describe()) }),
    )

    fun hasPermissions(): Boolean = heartLink.hasPermissions()

    fun startScan() = heartLink.startScan()

    /** Stops a scan in progress; leaves a connection alone. */
    fun stopScan() {
        if (heartLink.state.value == HeartLinkState.SCANNING) heartLink.stopScan()
    }

    fun connect(device: HeartDevice) {
        heartLink.stopScan()
        heartLink.connect(device)
    }

    fun connectRemembered() = heartLink.connectRemembered()

    fun disconnect() = heartLink.disconnect()

    /** Starts [pattern] unless a session already runs. Returns true when the session screen should open. */
    fun start(pattern: SavedBreathPattern): Boolean {
        if (controller.live.value != null) return false
        if (!Breathing.isAllowed(pattern.pattern)) return false
        controller.start(pattern)
        return true
    }

    fun startPreset(preset: Breathing.Preset): Boolean =
        start(SavedBreathPattern(id = 0, name = preset.name, pattern = preset.pattern))

    fun deletePattern(id: Long) {
        viewModelScope.launch { breathRepository.deletePattern(id) }
    }
}
