package com.lifepowers.app.ui.screens.sky

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifepowers.app.domain.ForecastRepository
import com.lifepowers.app.domain.ForecastSnapshot
import com.lifepowers.app.domain.LocationSetting
import com.lifepowers.app.domain.SkySettings
import com.lifepowers.app.domain.SkyToday
import com.lifepowers.app.domain.WorldEvent
import com.lifepowers.app.domain.WorldRepository
import com.lifepowers.core.Component
import com.lifepowers.core.Tier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class SkyUiState(
    val today: LocalDate = LocalDate.now(),
    /** Null until the world repository has produced the day once. */
    val sky: SkyToday? = null,
    val location: LocationSetting = LocationSetting(),
    /** The ENVIRONMENT forecast tier for today, worn by the pressure tile. */
    val environmentTier: Tier = Tier.UNKNOWN,
    /** Quakes and volcano reports from the last seven days, newest first. */
    val ground: List<WorldEvent> = emptyList(),
    val settings: SkySettings = SkySettings(),
    val loaded: Boolean = false,
)

@HiltViewModel
class SkyViewModel @Inject constructor(
    private val worldRepository: WorldRepository,
    forecastRepository: ForecastRepository,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()
    private val sinceUtcMs: Long = System.currentTimeMillis() - 7L * 86_400_000L

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val skyFlow: Flow<SkyToday?> = worldRepository.skyToday(today)
        .map<SkyToday, SkyToday?> { it }
        .catch { emit(null) }

    private val snapshotsFlow: Flow<List<ForecastSnapshot>> = forecastRepository.forDay(today).catch { emit(emptyList()) }

    private val groundFlow: Flow<List<WorldEvent>> = worldRepository.groundEvents(sinceUtcMs)
        .map { list -> list.filter { it.kind != "aurora" }.sortedByDescending { it.utcMs } }
        .catch { emit(emptyList()) }

    private val settingsFlow: Flow<SkySettings> = worldRepository.skySettings.catch { emit(SkySettings()) }

    private val locationFlow: Flow<LocationSetting> = worldRepository.location.catch { emit(LocationSetting()) }

    val state: StateFlow<SkyUiState> = combine(skyFlow, snapshotsFlow, groundFlow, settingsFlow, locationFlow) { sky, snapshots, ground, settings, location ->
        SkyUiState(
            today = today,
            sky = sky,
            location = sky?.location ?: location,
            environmentTier = snapshots.firstOrNull { it.component == Component.ENVIRONMENT }?.tier ?: Tier.UNKNOWN,
            ground = ground,
            settings = settings,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SkyUiState(today = today))

    init {
        // Opening the tab refreshes at most every 20 minutes; the Refresh link always refreshes.
        val now = System.currentTimeMillis()
        if (now - lastAutoRefreshMs >= AUTO_REFRESH_MS) {
            lastAutoRefreshMs = now
            refresh()
        }
    }

    /** Network refresh of weather, air, quakes, volcano reports and the K index. Failures are quiet. */
    fun refresh() {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            try {
                // Foreground is the reliable moment to read the device location; it is a no-op for a typed city or none.
                worldRepository.refreshDeviceLocation()
                worldRepository.refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // Offline or a feed is down. What is stored still shows.
            } finally {
                _refreshing.value = false
            }
        }
    }

    private companion object {
        const val AUTO_REFRESH_MS = 20 * 60_000L
        @Volatile var lastAutoRefreshMs = 0L
    }
}
