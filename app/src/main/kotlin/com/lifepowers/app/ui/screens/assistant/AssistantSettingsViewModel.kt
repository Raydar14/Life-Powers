package com.lifepowers.app.ui.screens.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifepowers.app.domain.AssistantFeatures
import com.lifepowers.app.domain.AssistantRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AssistantSettingsState(
    val loaded: Boolean = false,
    val master: Boolean = false,
    val features: Map<String, Boolean> = AssistantFeatures.ALL.associateWith { false },
    val hasKey: Boolean = false,
    val savingKey: Boolean = false,
    val notice: String? = null,
)

/** Backs the assistant section of Settings. The key goes straight to the encrypted store and is never read back. */
@HiltViewModel
class AssistantSettingsViewModel @Inject constructor(private val repository: AssistantRepository) : ViewModel() {

    private val savingKey = MutableStateFlow(false)
    private val notice = MutableStateFlow<String?>(null)

    val state: StateFlow<AssistantSettingsState> = combine(
        repository.masterOn,
        repository.enabled,
        repository.hasKey,
        savingKey,
        notice,
    ) { master, features, hasKey, saving, note ->
        AssistantSettingsState(loaded = true, master = master, features = features, hasKey = hasKey, savingKey = saving, notice = note)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssistantSettingsState())

    fun setMaster(on: Boolean) {
        viewModelScope.launch { runCatching { repository.setFeature(MASTER, on) } }
    }

    fun setFeature(feature: String, on: Boolean) {
        viewModelScope.launch { runCatching { repository.setFeature(feature, on) } }
    }

    /** Saves the key and reports whether it was kept, so the screen can clear its field. */
    fun saveKey(key: String, onDone: (Boolean) -> Unit) {
        val trimmed = key.trim()
        if (trimmed.isEmpty() || savingKey.value) return
        viewModelScope.launch {
            savingKey.value = true
            notice.value = null
            val ok = try {
                repository.setKey(trimmed)
                repository.hasKey.first()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            savingKey.value = false
            notice.value = if (ok) "Your key is saved on this phone." else "The key could not be saved just now. Try again in a moment."
            onDone(ok)
        }
    }

    fun removeKey() {
        viewModelScope.launch {
            notice.value = null
            val ok = runCatching { repository.setKey(null) }.isSuccess && !repository.hasKey.first()
            notice.value = if (ok) "Your key is removed from this phone." else "The key could not be removed just now. Try again in a moment."
        }
    }

    companion object {
        const val MASTER = "master"
    }
}
