package com.lifepowers.app.voice

import android.content.Context
import com.lifepowers.app.domain.BehaviorEvent
import com.lifepowers.app.domain.CheckinRecord
import com.lifepowers.app.domain.CheckinRepository
import com.lifepowers.app.domain.EventKinds
import com.lifepowers.app.domain.EventRepository
import com.lifepowers.app.domain.MetricRepository
import com.lifepowers.app.domain.SettingsRepository
import com.lifepowers.app.domain.nowZoneOffsetMin
import com.lifepowers.core.Metrics
import com.lifepowers.core.Sample
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drives one voice check-in from tap to saved record.
 *
 * Microphone strategy (the pragmatic path): [SpeechToText] alone produces the transcript, and acoustic
 * features come from PCM captured by [AudioRecorder], started 300 ms after the recognizer when
 * [allowSharedMic] is true. Many devices give the second client silence or refuse it outright; in that
 * case the record keeps the transcript, features are null, and qualityNote is "no_audio". When speech
 * recognition is unavailable the session records audio only and stores transcript null.
 */
@Singleton
class CheckinSessionController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val checkins: CheckinRepository,
    private val events: EventRepository,
    private val settings: SettingsRepository,
    private val metrics: MetricRepository,
) {
    sealed interface State {
        data object Idle : State
        data class Recording(val seconds: Int, val partialTranscript: String, val level: Float) : State
        data object Processing : State
        data class Done(val record: CheckinRecord, val savedId: Long, val crisisResources: String?) : State
        data class Error(val message: String) : State
    }

    data class Prompt(val id: String, val text: String)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val processor = CheckinProcessor()

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile private var promptIndex: Int = (LocalDate.now().toEpochDay() % PROMPTS.size).toInt()
    private val _currentPrompt = MutableStateFlow(PROMPTS[promptIndex].text)
    val currentPrompt: StateFlow<String> = _currentPrompt.asStateFlow()

    /** Start AudioRecord beside the recognizer. Not yet in SettingsRepository; see app/NEEDS-voice.md. */
    @Volatile var allowSharedMic: Boolean = true

    private var session: Session? = null

    fun nextPrompt() {
        promptIndex = (promptIndex + 1) % PROMPTS.size
        _currentPrompt.value = PROMPTS[promptIndex].text
    }

    /** Caller has already obtained RECORD_AUDIO. No-op while a session is recording or processing. */
    fun start() {
        synchronized(this) {
            val s = _state.value
            if (s is State.Recording || s is State.Processing) return
            session?.cancel()
            _state.value = State.Recording(0, "", 0f)
            session = Session(PROMPTS[promptIndex].id).also { it.begin() }
        }
    }

    /** Ends recording, transcribes, processes, saves, writes metric samples, emits Done. */
    fun stop() {
        val s = synchronized(this) { session } ?: return
        s.stop()
    }

    fun reset() {
        synchronized(this) {
            session?.cancel()
            session = null
            _state.value = State.Idle
        }
    }

    /** Adds symptom events for the kinds the person confirmed; quantity is the parsed severity (default 3). */
    suspend fun confirmSymptoms(record: CheckinRecord, kinds: List<String>) {
        for (kind in kinds.map { it.removePrefix("symptom.") }.distinct()) {
            val parsed = record.symptoms.firstOrNull { it.kind == kind }
            events.add(
                BehaviorEvent(
                    kind = EventKinds.symptom(kind),
                    startUtcMs = record.startUtcMs,
                    zoneOffsetMin = record.zoneOffsetMin,
                    quantity = (parsed?.severity ?: 3).toDouble(),
                    unit = "severity",
                    note = parsed?.phrase,
                    source = SOURCE,
                ),
            )
        }
    }

    /** Adds behavior events (alcohol, caffeine, ...) the person confirmed from [BehaviorMentions.suggestions]. */
    suspend fun logSpokenBehaviors(record: CheckinRecord, kinds: List<String>) {
        for (kind in kinds.distinct()) {
            val quantity = BehaviorMentions.quantityFor(kind, record.transcript)
            val unit = when (kind) {
                EventKinds.ALCOHOL -> "drinks"
                EventKinds.CAFFEINE -> "servings"
                else -> null
            }
            events.add(
                BehaviorEvent(
                    kind = kind,
                    startUtcMs = record.startUtcMs,
                    zoneOffsetMin = record.zoneOffsetMin,
                    quantity = quantity,
                    unit = if (quantity != null) unit else null,
                    note = "spoken in check-in",
                    source = SOURCE,
                ),
            )
        }
    }

    private inner class Session(val promptId: String) : AudioRecorder.Listener {
        val startUtcMs = System.currentTimeMillis()
        val zoneOffsetMin = nowZoneOffsetMin()
        private val recorder = AudioRecorder()
        private val stt = SpeechToText(context)
        private val sessionJob = SupervisorJob(scope.coroutineContext[Job])
        private val sessionScope = CoroutineScope(scope.coroutineContext + sessionJob)
        private val finalTranscript = CompletableDeferred<String?>()
        private val stopped = AtomicBoolean(false)
        @Volatile private var partial = ""
        @Volatile private var recorderLevel = 0f
        @Volatile private var sttLevel = 0f
        @Volatile private var audioStarted = false
        @Volatile private var sttJob: Job? = null
        @Volatile private var tickerJob: Job? = null

        fun begin() {
            sessionScope.launch {
                val sttAvailable = SpeechToText.isAvailable(context)
                if (sttAvailable) {
                    sttJob = launchRecognizer()
                    delay(SHARED_MIC_DELAY_MS)
                } else {
                    finalTranscript.complete(null)
                }
                if (allowSharedMic || !sttAvailable) audioStarted = recorder.start(this@Session)
                tickerJob = launch { tick() }
            }
        }

        private fun launchRecognizer(): Job = sessionScope.launch {
            try {
                stt.listen().collect { ev ->
                    when (ev) {
                        is SpeechToText.Event.Partial -> partial = ev.text
                        is SpeechToText.Event.Level -> sttLevel = ev.level
                        is SpeechToText.Event.Final -> {
                            partial = ev.text
                            finalTranscript.complete(ev.text.trim().ifEmpty { null })
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Recognizer failed before any text; the recording continues without a transcript.
            } finally {
                if (!finalTranscript.isCompleted) finalTranscript.complete(partial.trim().ifEmpty { null })
            }
        }

        private suspend fun tick() {
            while (sessionScope.isActive && !stopped.get()) {
                val elapsed = ((System.currentTimeMillis() - startUtcMs) / 1000).toInt()
                if (!stopped.get()) {
                    _state.value = State.Recording(elapsed, partial, if (audioStarted) recorderLevel else sttLevel)
                }
                if (elapsed >= AudioRecorder.MAX_SECONDS) { stop(); return }
                delay(TICK_MS)
            }
        }

        override fun onProgress(elapsedSeconds: Int, level: Float) { recorderLevel = level }

        override fun onCapReached() { stop() }

        fun stop() {
            if (!stopped.compareAndSet(false, true)) return
            tickerJob?.cancel()
            _state.value = State.Processing
            sessionScope.launch { finishAndSave() }
        }

        fun cancel() {
            stopped.set(true)
            sessionJob.cancel()
            scope.launch { recorder.stop() }
        }

        private suspend fun finishAndSave() {
            try {
                stt.stopListening()
                val audio = if (audioStarted) recorder.stop() else null
                val transcript = withTimeoutOrNull(FINAL_TRANSCRIPT_TIMEOUT_MS) { finalTranscript.await() }
                    ?: partial.trim().ifEmpty { null }
                sttJob?.cancel()
                val durationS = (System.currentTimeMillis() - startUtcMs) / 1000.0

                val baseline = checkins.recent(BASELINE_LOOKBACK).first()
                    .filter { it.qualityOk && it.features != null }
                    .map { it.features!! }

                val pcm = audio?.pcm?.takeIf { it.isNotEmpty() }
                if (pcm == null && transcript == null) {
                    _state.value = State.Error("Nothing was captured. Check the microphone permission and try again.")
                    return
                }
                val record = processor.process(
                    CheckinProcessor.Input(
                        pcm = pcm,
                        sampleRate = audio?.sampleRate ?: AudioRecorder.DEFAULT_SAMPLE_RATE,
                        transcript = transcript,
                        promptId = promptId,
                        startUtcMs = startUtcMs,
                        zoneOffsetMin = zoneOffsetMin,
                        durationS = durationS,
                        baseline = baseline,
                    ),
                )
                val id = checkins.save(record)
                val saved = record.copy(id = id)

                if (audio != null && pcm != null && settings.keepAudio.first()) {
                    try {
                        recorder.writeWav(File(File(context.filesDir, AUDIO_DIR), "checkin_$startUtcMs.wav"), audio)
                    } catch (e: IOException) {
                        // Keeping audio is optional; the record is already saved.
                    }
                }
                if (!saved.crisisFlag) {
                    val samples = samplesFor(saved)
                    if (samples.isNotEmpty()) metrics.upsertSamples(samples)
                }
                _state.value = State.Done(saved, id, if (saved.crisisFlag) CRISIS_RESOURCES else null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = State.Error(e.message ?: "Check-in could not be saved.")
            } finally {
                synchronized(this@CheckinSessionController) { if (session === this) session = null }
            }
        }
    }

    private fun samplesFor(r: CheckinRecord): List<Sample> {
        val out = ArrayList<Sample>()
        fun add(metric: String, value: Double?) {
            if (value != null && !value.isNaN()) out += Sample(metric, value, r.startUtcMs, null, r.zoneOffsetMin, SOURCE)
        }
        r.words?.let { w ->
            add(Metrics.WORDS_VALENCE, w.valence)
            add(Metrics.WORDS_OPTIMISM, w.optimism)
            add(Metrics.WORDS_ANXIETY, w.anxietyMarkers)
            add(Metrics.WORDS_SELF_FOCUS, w.selfFocus)
            add(Metrics.WORDS_ENERGY_SELF_REPORT, w.energySelfReport)
        }
        r.indices?.let { v ->
            add(Metrics.VOICE_ENERGY_INDEX, v.energy)
            add(Metrics.VOICE_FLATNESS_INDEX, v.flatness)
            add(Metrics.VOICE_TENSION_INDEX, v.tension)
        }
        return out
    }

    companion object {
        const val SOURCE = "voice_checkin"
        const val CRISIS_RESOURCES =
            "If you are in danger or thinking about harming yourself, call or text 988 (Suicide and Crisis Lifeline) or text HOME to 741741. This app is not a crisis service."

        val PROMPTS: List<Prompt> = listOf(
            Prompt("p1", "How are you feeling right now?"),
            Prompt("p2", "What is on your mind today?"),
            Prompt("p3", "How did last night go?"),
            Prompt("p4", "What does your body feel like this morning?"),
            Prompt("p5", "What are you looking forward to, or not?"),
            Prompt("p6", "What took the most energy yesterday?"),
            Prompt("p7", "What would make today easier?"),
            Prompt("p8", "Say a few words about how the last day went."),
        )

        /** Suggested behavior event kinds spoken in the transcript, for confirm chips. */
        fun suggestions(record: CheckinRecord): List<String> = BehaviorMentions.suggestions(record)

        private const val SHARED_MIC_DELAY_MS = 300L
        private const val TICK_MS = 250L
        private const val FINAL_TRANSCRIPT_TIMEOUT_MS = 4000L
        private const val BASELINE_LOOKBACK = 60
        private const val AUDIO_DIR = "audio"
    }
}
