package com.lifepowers.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifepowers.app.domain.ProfileRepository
import com.lifepowers.app.domain.SettingsRepository
import com.lifepowers.app.domain.SyncManager
import com.lifepowers.app.domain.SyncStatus
import com.lifepowers.app.domain.UserProfileState
import com.lifepowers.core.HormonalModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val assistantOn: Boolean = false,
    val keepAudio: Boolean = false,
    val ouraToken: String? = null,
    val sync: List<SyncStatus> = emptyList(),
    val profile: UserProfileState = UserProfileState(),
    val loaded: Boolean = false,
) {
    val setupLeft: Int
        get() {
            var n = 0
            if (!profile.onboardingDone) n++
            if (ouraToken.isNullOrBlank()) n++
            if (profile.displayName.isNullOrBlank()) n++
            return n
        }
    val modelName: String get() = when (profile.hormonalModel) {
        HormonalModel.NATURAL_CYCLE -> "natural cycle"
        HormonalModel.NATURAL_CYCLE_LOW_TRUST -> "natural cycle, low trust"
        HormonalModel.PACK_RHYTHM -> "pack rhythm"
        HormonalModel.SUPPRESSED_UNCERTAIN -> "suppressed"
        HormonalModel.NO_CYCLE -> "no cycle"
        HormonalModel.DOSING_RHYTHM -> "dosing rhythm"
        HormonalModel.GESTATIONAL -> "gestational"
    }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val syncManager: SyncManager,
    profileRepository: ProfileRepository,
) : ViewModel() {

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _healthConnect = MutableStateFlow<String?>(null)
    /** A one line result from the last Health Connect permission request. */
    val healthConnectNote: StateFlow<String?> = _healthConnect.asStateFlow()

    val state: StateFlow<SettingsUiState> = combine(
        settingsRepository.assistantMaster,
        settingsRepository.keepAudio,
        settingsRepository.ouraToken,
        syncManager.status,
        profileRepository.profile,
    ) { assistant, keep, token, sync, profile ->
        SettingsUiState(assistantOn = assistant, keepAudio = keep, ouraToken = token, sync = sync, profile = profile, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun setAssistant(on: Boolean) { viewModelScope.launch { settingsRepository.setAssistantMaster(on) } }
    fun setKeepAudio(on: Boolean) { viewModelScope.launch { settingsRepository.setKeepAudio(on) } }

    fun saveOuraToken(token: String) {
        viewModelScope.launch { settingsRepository.setOuraToken(token.trim().takeIf { it.isNotEmpty() }) }
    }

    fun syncNow() {
        if (_syncing.value) return
        _syncing.value = true
        viewModelScope.launch {
            runCatching { syncManager.syncNow() }
            _syncing.value = false
        }
    }

    fun healthConnectResult(granted: Set<String>, requested: Int) {
        _healthConnect.value = when {
            granted.isEmpty() -> "Nothing granted. You can try again any time."
            granted.size >= requested -> "Connected. ${granted.size} kinds of data allowed."
            else -> "Partly connected. ${granted.size} of $requested allowed."
        }
    }

    fun healthConnectUnavailable() {
        _healthConnect.value = "Health Connect is not available on this phone yet."
    }
}
