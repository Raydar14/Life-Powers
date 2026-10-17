package com.lifepowers.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifepowers.app.domain.LocationMode
import com.lifepowers.app.domain.LocationSetting
import com.lifepowers.app.domain.ProfileRepository
import com.lifepowers.app.domain.SettingsRepository
import com.lifepowers.app.domain.SkySettings
import com.lifepowers.app.domain.SyncManager
import com.lifepowers.app.domain.SyncStatus
import com.lifepowers.app.domain.UserProfileState
import com.lifepowers.app.domain.WorldRepository
import com.lifepowers.core.OpenMeteo
import com.lifepowers.core.HormonalModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
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
    private val worldRepository: WorldRepository,
) : ViewModel() {

    /** Where the app thinks you are, and the sky notice settings. */
    val location: StateFlow<LocationSetting> = worldRepository.location
        .catch { emit(LocationSetting()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LocationSetting())

    val skySettings: StateFlow<SkySettings> = worldRepository.skySettings
        .catch { emit(SkySettings()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SkySettings())

    private val _cityResults = MutableStateFlow<List<OpenMeteo.Place>>(emptyList())
    val cityResults: StateFlow<List<OpenMeteo.Place>> = _cityResults.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _locationNote = MutableStateFlow<String?>(null)
    /** A one line result of the last location choice or city search. */
    val locationNote: StateFlow<String?> = _locationNote.asStateFlow()

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

    // Location and world

    /**
     * Precise or about 10 km, after the permission answer. Saves the mode, reads the device once, then
     * refreshes weather and the ground. [granted] false leaves the setting as it was.
     */
    fun chooseDeviceLocation(mode: LocationMode, granted: Boolean, downgraded: Boolean = false) {
        if (!granted) {
            _locationNote.value = "Without location permission the app cannot read your place. A city you type works just as well."
            return
        }
        _cityResults.value = emptyList()
        viewModelScope.launch {
            quietly { worldRepository.setLocation(LocationSetting(mode = mode, updatedUtcMs = System.currentTimeMillis())) }
            val read = quietly { worldRepository.refreshDeviceLocation() } ?: false
            _locationNote.value = when {
                !read -> "The phone has not shared a place yet. The app will try again on the next refresh."
                downgraded -> "The phone shared an approximate place, so the app uses about 10 km."
                else -> null
            }
            quietly { worldRepository.refresh() }
        }
    }

    fun clearLocation() {
        _cityResults.value = emptyList()
        _locationNote.value = null
        viewModelScope.launch {
            quietly { worldRepository.setLocation(LocationSetting(mode = LocationMode.NONE, updatedUtcMs = System.currentTimeMillis())) }
        }
    }

    fun searchCity(name: String) {
        val query = name.trim()
        if (query.isEmpty() || _searching.value) return
        _searching.value = true
        _locationNote.value = null
        viewModelScope.launch {
            val found = quietly { worldRepository.searchCity(query) }
            _cityResults.value = found ?: emptyList()
            _locationNote.value = when {
                found == null -> "The city search could not be reached. Check the connection and try again."
                found.isEmpty() -> "No places found by that name."
                else -> null
            }
            _searching.value = false
        }
    }

    fun chooseCity(place: OpenMeteo.Place) {
        _cityResults.value = emptyList()
        _locationNote.value = null
        viewModelScope.launch {
            quietly {
                worldRepository.setLocation(
                    LocationSetting(
                        mode = LocationMode.CITY,
                        label = placeLabel(place),
                        point = place.point,
                        updatedUtcMs = System.currentTimeMillis(),
                    )
                )
            }
            quietly { worldRepository.refresh() }
        }
    }

    fun saveSky(change: (SkySettings) -> SkySettings) {
        val next = change(skySettings.value)
        viewModelScope.launch { quietly { worldRepository.saveSkySettings(next) } }
    }

    /** Runs [block], returning null on any failure except cancellation. */
    private suspend fun <T> quietly(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        null
    }

    companion object {
        /** "Portland, Oregon" from a geocoding result. */
        fun placeLabel(place: OpenMeteo.Place): String =
            listOfNotNull(place.name, (place.region ?: place.country)?.takeIf { it.isNotBlank() && it != place.name }).joinToString(", ")
    }
}
