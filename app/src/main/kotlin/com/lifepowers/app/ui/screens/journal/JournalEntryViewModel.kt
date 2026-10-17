package com.lifepowers.app.ui.screens.journal

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifepowers.app.domain.JournalEntry
import com.lifepowers.app.domain.JournalRepository
import com.lifepowers.app.domain.Photo
import com.lifepowers.app.domain.TarotPull
import com.lifepowers.app.domain.nowZoneOffsetMin
import com.lifepowers.app.voice.SpeechToText
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import javax.inject.Inject

/** The editable copy of one entry. Photos and pulls follow the stored entry. */
data class EntryForm(
    val id: Long = 0,
    val localDate: LocalDate = LocalDate.now(),
    val createdUtcMs: Long = System.currentTimeMillis(),
    val zoneOffsetMin: Int = nowZoneOffsetMin(),
    val text: String = "",
    val voiceTranscript: String = "",
    val tags: List<String> = emptyList(),
    val scoreWords: Boolean = false,
    val photos: List<Photo> = emptyList(),
    val pulls: List<TarotPull> = emptyList(),
) {
    /** The parts the person edits here; photos and pulls are saved as they are added. */
    fun content(): List<Any> = listOf(text, voiceTranscript, tags, scoreWords)

    fun hasWords(): Boolean = text.isNotBlank() || voiceTranscript.isNotBlank() || tags.isNotEmpty()

    fun toEntry(): JournalEntry = JournalEntry(
        id = id,
        localDate = localDate,
        createdUtcMs = createdUtcMs,
        zoneOffsetMin = zoneOffsetMin,
        text = text.trimEnd(),
        voiceTranscript = voiceTranscript.trim().ifBlank { null },
        tags = tags,
        scoreWords = scoreWords,
        photos = photos,
        pulls = pulls,
    )
}

data class EntryUiState(
    val form: EntryForm = EntryForm(),
    val loaded: Boolean = false,
    /** An existing id that is not in the journal any more. */
    val missing: Boolean = false,
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val listening: Boolean = false,
    val partial: String = "",
    val photoBusy: Boolean = false,
    val notice: String? = null,
    /** Set once the screen should close. */
    val closed: Boolean = false,
)

@HiltViewModel
class JournalEntryViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val journalRepository: JournalRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(EntryUiState())
    val state: StateFlow<EntryUiState> = _state.asStateFlow()

    private val thumbs = PhotoThumbs(journalRepository, targetPx = 360)
    private val stt = SpeechToText(appContext)
    private val saveMutex = Mutex()

    private var started = false
    private var hydrated = false
    private var observeJob: Job? = null
    private var listenJob: Job? = null

    /** Called once per screen with the route's id. 0 starts a new entry for today. */
    fun load(entryId: Long) {
        if (started) return
        started = true
        if (entryId == 0L) {
            hydrated = true
            _state.update { it.copy(loaded = true) }
        } else {
            observe(entryId)
        }
    }

    private fun observe(id: Long) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            journalRepository.entry(id).collect { entry ->
                if (entry == null) {
                    _state.update { if (it.loaded) it else it.copy(loaded = true, missing = true) }
                    return@collect
                }
                _state.update { s ->
                    if (!hydrated) {
                        s.copy(
                            loaded = true,
                            missing = false,
                            form = EntryForm(
                                id = entry.id,
                                localDate = entry.localDate,
                                createdUtcMs = entry.createdUtcMs,
                                zoneOffsetMin = entry.zoneOffsetMin,
                                text = entry.text,
                                voiceTranscript = entry.voiceTranscript.orEmpty(),
                                tags = entry.tags,
                                scoreWords = entry.scoreWords,
                                photos = entry.photos,
                                pulls = entry.pulls,
                            ),
                        )
                    } else {
                        s.copy(loaded = true, form = s.form.copy(photos = entry.photos, pulls = entry.pulls))
                    }
                }
                hydrated = true
            }
        }
    }

    suspend fun thumb(photo: Photo): ImageBitmap? = thumbs.load(photo)

    // Editing

    fun setText(value: String) = edit { it.copy(text = value) }

    fun setTranscript(value: String) = edit { it.copy(voiceTranscript = value) }

    fun setScoreWords(on: Boolean) = edit { it.copy(scoreWords = on) }

    fun addTags(raw: String) {
        val words = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (words.isEmpty()) return
        edit { f ->
            val next = f.tags.toMutableList()
            for (w in words) if (next.none { it.equals(w, ignoreCase = true) }) next.add(w)
            f.copy(tags = next)
        }
    }

    fun removeTag(tag: String) = edit { f -> f.copy(tags = f.tags.filterNot { it == tag }) }

    private fun edit(change: (EntryForm) -> EntryForm) {
        _state.update { s ->
            val next = change(s.form)
            s.copy(form = next, dirty = s.dirty || next.content() != s.form.content())
        }
    }

    fun clearNotice() = _state.update { it.copy(notice = null) }

    // Saving

    /** Saves the current words. Returns the id, or null when the save failed. */
    private suspend fun saveNow(): Long? = saveMutex.withLock {
        val snapshot = _state.value.form
        val id = try {
            journalRepository.saveEntry(snapshot.toEntry())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(notice = "That did not save just now. Your words are still here.") }
            return@withLock null
        }
        val created = snapshot.id == 0L
        _state.update { s -> s.copy(form = s.form.copy(id = id), dirty = s.form.content() != snapshot.content()) }
        if (created) {
            hydrated = true
            observe(id)
        }
        id
    }

    /** The entry's id, saving a new entry first so photos and pulls have something to belong to. */
    suspend fun ensureSaved(): Long? {
        val id = _state.value.form.id
        return if (id != 0L) id else saveNow()
    }

    fun save() {
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            finishListening()
            val ok = saveNow() != null
            _state.update { it.copy(saving = false, closed = ok) }
        }
    }

    /** Back: keeps what was written, and closes without saving an untouched new page. */
    fun back() {
        if (_state.value.saving) return
        val s = _state.value
        val needsSave = s.listening || (s.dirty && (s.form.id != 0L || s.form.hasWords()))
        if (!needsSave) {
            stt.stopListening()
            _state.update { it.copy(closed = true) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            finishListening()
            val after = _state.value.form
            val ok = if (after.id == 0L && !after.hasWords()) true else saveNow() != null
            _state.update { it.copy(saving = false, closed = ok) }
        }
    }

    fun delete() {
        val id = _state.value.form.id
        stt.stopListening()
        listenJob?.cancel()
        if (id == 0L) {
            _state.update { it.copy(closed = true) }
            return
        }
        viewModelScope.launch {
            observeJob?.cancel()
            val ok = runCatching { journalRepository.deleteEntry(id) }.isSuccess
            _state.update { if (ok) it.copy(closed = true) else it.copy(notice = "That did not delete just now. Try again in a moment.") }
        }
    }

    // Voice note

    fun startListening() {
        if (listenJob?.isActive == true) return
        _state.update { it.copy(listening = true, partial = "", notice = null) }
        listenJob = viewModelScope.launch {
            try {
                stt.listen().collect { event ->
                    when (event) {
                        is SpeechToText.Event.Partial -> _state.update { it.copy(partial = event.text) }
                        is SpeechToText.Event.Level -> Unit
                        is SpeechToText.Event.Final -> appendTranscript(event.text)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SpeechToText.RecognitionException) {
                _state.update { it.copy(notice = e.message ?: "Nothing was heard. Try again when you are ready.") }
            } catch (e: Exception) {
                _state.update { it.copy(notice = "Listening stopped. Try again when you are ready.") }
            } finally {
                _state.update { it.copy(listening = false, partial = "") }
            }
        }
    }

    fun stopListening() {
        stt.stopListening()
    }

    fun micDenied() {
        _state.update { it.copy(notice = "The microphone is off for this app. Writing works just the same.") }
    }

    /** Stops a running voice note and waits briefly for its last words. */
    private suspend fun finishListening() {
        val job = listenJob ?: return
        if (!job.isActive) return
        stt.stopListening()
        withTimeoutOrNull(4000L) { job.join() }
        if (job.isActive) job.cancel()
    }

    private fun appendTranscript(text: String) {
        val heard = text.trim()
        if (heard.isEmpty()) {
            _state.update { it.copy(notice = "Nothing was heard. Try again when you are ready.") }
            return
        }
        edit { f ->
            val joined = if (f.voiceTranscript.isBlank()) heard else f.voiceTranscript.trimEnd() + "\n" + heard
            f.copy(voiceTranscript = joined)
        }
    }

    // Photos

    fun addPhotoFromUri(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(photoBusy = true, notice = null) }
            val read: Pair<ByteArray, String>? = withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = appContext.contentResolver
                    val mime = resolver.getType(uri) ?: "image/jpeg"
                    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes == null || bytes.isEmpty()) null else bytes to mime
                }.getOrNull()
            }
            if (read == null) {
                _state.update { it.copy(photoBusy = false, notice = "That photo could not be read.") }
                return@launch
            }
            attach(read.first, read.second)
        }
    }

    fun addPhotoFromBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            _state.update { it.copy(photoBusy = true, notice = null) }
            val bytes: ByteArray? = withContext(Dispatchers.Default) {
                runCatching {
                    ByteArrayOutputStream().use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        out.toByteArray()
                    }
                }.getOrNull()
            }
            if (bytes == null || bytes.isEmpty()) {
                _state.update { it.copy(photoBusy = false, notice = "That photo could not be kept.") }
                return@launch
            }
            attach(bytes, "image/jpeg")
        }
    }

    fun cameraUnavailable() {
        _state.update { it.copy(notice = "No camera app answered. Adding a photo from the gallery still works.") }
    }

    private suspend fun attach(bytes: ByteArray, mime: String) {
        val id = ensureSaved()
        if (id == null) {
            _state.update { it.copy(photoBusy = false) }
            return
        }
        val photo: Photo? = try {
            journalRepository.addPhoto(id, bytes, mime)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        _state.update { s ->
            if (photo == null) {
                s.copy(photoBusy = false, notice = "That photo could not be kept.")
            } else {
                val photos = if (s.form.photos.any { it.id == photo.id }) s.form.photos else s.form.photos + photo
                s.copy(photoBusy = false, form = s.form.copy(photos = photos))
            }
        }
    }

    fun removePhoto(photo: Photo) {
        viewModelScope.launch {
            val ok = runCatching { journalRepository.deletePhoto(photo.id) }.isSuccess
            if (ok) {
                thumbs.forget(photo.id)
                _state.update { s -> s.copy(form = s.form.copy(photos = s.form.photos.filterNot { it.id == photo.id })) }
            } else {
                _state.update { it.copy(notice = "That photo could not be removed just now.") }
            }
        }
    }

    override fun onCleared() {
        stt.stopListening()
        super.onCleared()
    }
}
