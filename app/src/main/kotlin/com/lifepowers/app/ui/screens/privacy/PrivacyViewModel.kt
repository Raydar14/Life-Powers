package com.lifepowers.app.ui.screens.privacy

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifepowers.app.domain.AssistantCallLog
import com.lifepowers.app.domain.AssistantRepository
import com.lifepowers.app.domain.DataResult
import com.lifepowers.app.domain.DataVault
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the data card shows: the running job, or the last result. */
data class PrivacyUiState(
    /** A short sentence while work runs, such as "Making the backup". */
    val busy: String? = null,
    val result: String? = null,
    val resultOk: Boolean = true,
    /** True after delete everything finished, so the screen can offer to start again. */
    val deleted: Boolean = false,
    /** A backup file chosen for restore, waiting for the person to confirm the replace. */
    val pendingRestore: Uri? = null,
)

@HiltViewModel
class PrivacyViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vault: DataVault,
    assistant: AssistantRepository,
) : ViewModel() {

    /** Every assistant call made from this phone, newest first. */
    val log: StateFlow<List<AssistantCallLog>> = assistant.log
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _state = MutableStateFlow(PrivacyUiState())
    val state: StateFlow<PrivacyUiState> = _state.asStateFlow()

    /*
     * Passwords wait here while the system file picker is open, so a rotation or a recreated activity
     * does not lose them. They are wiped as soon as they are used or abandoned.
     */
    private var stagedBackupPassword: CharArray? = null
    private var stagedIncludePhotos: Boolean = false
    private var stagedRestorePassword: CharArray? = null

    fun stageBackup(password: String, includePhotos: Boolean) {
        wipe(stagedBackupPassword)
        stagedBackupPassword = password.toCharArray()
        stagedIncludePhotos = includePhotos
    }

    /** The answer from the save picker. Null means the person backed out. */
    fun backupTo(uri: Uri?) {
        val password = stagedBackupPassword
        stagedBackupPassword = null
        if (uri == null || password == null) {
            wipe(password)
            return
        }
        val photos = stagedIncludePhotos
        launchJob(label = if (photos) "Making the backup, photos too" else "Making the backup", target = uri, cleanup = { wipe(password) }) {
            val out = context.contentResolver.openOutputStream(uri) ?: return@launchJob DataResult.Failed(CANNOT_WRITE)
            out.use { vault.backup(it, password, photos) }
        }
    }

    fun stageRestore(password: String) {
        wipe(stagedRestorePassword)
        stagedRestorePassword = password.toCharArray()
    }

    /** The answer from the open picker. A file opens the confirm dialog; null clears everything. */
    fun restoreChosen(uri: Uri?) {
        if (uri == null) {
            cancelRestore()
            return
        }
        _state.value = _state.value.copy(pendingRestore = uri)
    }

    fun cancelRestore() {
        wipe(stagedRestorePassword)
        stagedRestorePassword = null
        _state.value = _state.value.copy(pendingRestore = null)
    }

    fun confirmRestore() {
        val uri = _state.value.pendingRestore
        val password = stagedRestorePassword
        stagedRestorePassword = null
        _state.value = _state.value.copy(pendingRestore = null)
        if (uri == null || password == null) {
            wipe(password)
            if (uri != null) _state.value = _state.value.copy(result = "Type the backup's password first.", resultOk = false)
            return
        }
        launchJob(label = "Restoring the backup", cleanup = { wipe(password) }) {
            val input = context.contentResolver.openInputStream(uri) ?: return@launchJob DataResult.Failed("The phone did not open that file.")
            input.use { vault.restore(it, password) }
        }
    }

    fun exportTo(uri: Uri?) {
        if (uri == null) return
        launchJob(label = "Writing the spreadsheet files", target = uri) {
            val out = context.contentResolver.openOutputStream(uri) ?: return@launchJob DataResult.Failed(CANNOT_WRITE)
            out.use { vault.exportCsv(it) }
        }
    }

    fun deleteEverything() {
        launchJob(label = "Deleting everything", markDeleted = true) { vault.deleteEverything() }
    }

    fun dismissResult() {
        _state.value = _state.value.copy(result = null)
    }

    /**
     * Runs one job on the IO dispatcher. A failed save removes the half written file at [target], so a
     * broken backup is never left lying around.
     */
    private fun launchJob(
        label: String,
        target: Uri? = null,
        markDeleted: Boolean = false,
        cleanup: () -> Unit = {},
        work: suspend () -> DataResult,
    ) {
        if (_state.value.busy != null) {
            cleanup()
            return
        }
        _state.value = _state.value.copy(busy = label, result = null, deleted = false)
        viewModelScope.launch(Dispatchers.IO) {
            val result = try {
                work()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DataResult.Failed("That did not work. Nothing was changed on this phone.")
            } finally {
                cleanup()
            }
            if (result is DataResult.Failed && target != null) {
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, target) }
            }
            _state.value = when (result) {
                is DataResult.Done -> _state.value.copy(busy = null, result = result.message, resultOk = true, deleted = markDeleted)
                is DataResult.Failed -> _state.value.copy(busy = null, result = result.message, resultOk = false)
            }
        }
    }

    private fun wipe(chars: CharArray?) {
        chars?.fill('\u0000')
    }

    override fun onCleared() {
        wipe(stagedBackupPassword)
        wipe(stagedRestorePassword)
        stagedBackupPassword = null
        stagedRestorePassword = null
    }

    private companion object {
        const val CANNOT_WRITE = "The phone did not open that place for writing. Try another folder."
    }
}
