package com.lifepowers.app.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale

/**
 * Wraps [SpeechRecognizer] as a single flow of transcript events for one check-in.
 *
 * The platform recognizer finalizes a segment at every pause of a second or two, which is far shorter
 * than a 30 to 60 s monologue. So after each final segment we append it to the committed text and
 * start listening again until [stopListening] is called; [Event.Final] carries everything heard.
 * Every SpeechRecognizer call runs on the main thread as the framework requires.
 */
class SpeechToText(private val context: Context) {

    sealed interface Event {
        /** Committed segments plus the current in flight hypothesis. */
        data class Partial(val text: String) : Event
        /** Recognizer loudness, normalized to 0..1, for a meter when AudioRecord cannot run. */
        data class Level(val level: Float) : Event
        /** Whole transcript; the flow completes right after. Empty when nothing was recognized. */
        data class Final(val text: String) : Event
    }

    class RecognitionException(message: String, val code: Int) : Exception(message)

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var session: Session? = null

    /**
     * Posts stopListening to the main thread. The flow then emits [Event.Final] once the recognizer
     * returns its last segment, or after [STOP_TIMEOUT_MS] with whatever was heard.
     */
    fun stopListening() {
        val s = session ?: return
        s.stopRequested = true
        main.post {
            try { s.recognizer?.stopListening() } catch (e: RuntimeException) { s.finish() }
            main.postDelayed({ s.finish() }, STOP_TIMEOUT_MS)
        }
    }

    fun listen(): Flow<Event> = callbackFlow {
        val s = Session(this)
        session = s
        main.post { s.begin() }
        awaitClose {
            if (session === s) session = null
            main.removeCallbacksAndMessages(null)
            main.post { s.release() }
        }
    }.buffer(Channel.UNLIMITED)

    private inner class Session(private val out: ProducerScope<Event>) : RecognitionListener {
        @Volatile var recognizer: SpeechRecognizer? = null
        @Volatile var stopRequested = false
        private val committed = StringBuilder()
        private var inFlight = ""
        private var consecutiveErrors = 0
        private var finished = false

        private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            // Hints only; most engines shorten them, hence the restart loop.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
        }

        /** Main thread. */
        fun begin() {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                fail("Speech recognition is not available on this device.", SpeechRecognizer.ERROR_CLIENT)
                return
            }
            val r = try { create() } catch (e: RuntimeException) { null }
            if (r == null) {
                fail("Speech recognizer could not be created.", SpeechRecognizer.ERROR_CLIENT)
                return
            }
            r.setRecognitionListener(this)
            recognizer = r
            startSegment()
        }

        /** Main thread. */
        fun release() {
            val r = recognizer
            recognizer = null
            try { r?.cancel() } catch (e: RuntimeException) { }
            try { r?.destroy() } catch (e: RuntimeException) { }
        }

        private fun startSegment() {
            if (finished || stopRequested) return
            try {
                recognizer?.startListening(intent)
            } catch (e: RuntimeException) {
                fail("Speech recognizer could not start: ${e.message}", SpeechRecognizer.ERROR_CLIENT)
            }
        }

        private fun joined(extra: String): String {
            val sb = StringBuilder(committed)
            if (extra.isNotBlank()) { if (sb.isNotEmpty()) sb.append(' '); sb.append(extra.trim()) }
            return sb.toString()
        }

        private fun commit(text: String) {
            if (text.isBlank()) return
            if (committed.isNotEmpty()) committed.append(' ')
            committed.append(text.trim())
        }

        fun finish() {
            if (finished) return
            finished = true
            out.trySend(Event.Final(joined(inFlight)))
            out.close()
        }

        private fun fail(message: String, code: Int) {
            if (finished) return
            if (committed.isNotEmpty() || inFlight.isNotBlank()) { finish(); return }
            finished = true
            out.close(RecognitionException(message, code))
        }

        override fun onReadyForSpeech(params: Bundle?) { }
        override fun onBeginningOfSpeech() { }
        override fun onBufferReceived(buffer: ByteArray?) { }
        override fun onEndOfSpeech() { }
        override fun onEvent(eventType: Int, params: Bundle?) { }

        override fun onRmsChanged(rmsdB: Float) {
            if (!finished) out.trySend(Event.Level(((rmsdB + 2f) / 12f).coerceIn(0f, 1f)))
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (finished) return
            val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
            if (text.isBlank()) return
            inFlight = text
            out.trySend(Event.Partial(joined(inFlight)))
        }

        override fun onResults(results: Bundle?) {
            if (finished) return
            consecutiveErrors = 0
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: inFlight
            inFlight = ""
            commit(text)
            out.trySend(Event.Partial(joined("")))
            if (stopRequested) finish() else startSegment()
        }

        override fun onError(error: Int) {
            if (finished) return
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    // Silence between thoughts: keep listening unless the person has stopped.
                    commit(inFlight); inFlight = ""
                    when {
                        stopRequested -> finish()
                        ++consecutiveErrors > MAX_SILENT_RESTARTS -> fail("No speech was recognized.", error)
                        else -> startSegment()
                    }
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> {
                    when {
                        stopRequested -> finish()
                        ++consecutiveErrors > 3 -> fail(describe(error), error)
                        else -> main.postDelayed({ startSegment() }, 300L)
                    }
                }
                else -> fail(describe(error), error)
            }
        }
    }

    /** Must run on the main thread. */
    private fun create(): SpeechRecognizer =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }

    companion object {
        private const val MAX_SILENT_RESTARTS = 20
        private const val STOP_TIMEOUT_MS = 2500L

        fun isAvailable(context: Context): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

        fun describe(code: Int): String = when (code) {
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout while recognizing speech."
            SpeechRecognizer.ERROR_NETWORK -> "Network error while recognizing speech."
            SpeechRecognizer.ERROR_AUDIO -> "Microphone error."
            SpeechRecognizer.ERROR_SERVER -> "Speech service error."
            SpeechRecognizer.ERROR_CLIENT -> "Speech recognizer client error."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard."
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech was recognized."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is missing."
            12 -> "Language not supported by the on device recognizer." // ERROR_LANGUAGE_NOT_SUPPORTED, API 31
            13 -> "Language pack not downloaded for on device recognition." // ERROR_LANGUAGE_UNAVAILABLE, API 31
            14 -> "Speech service disconnected." // ERROR_SERVER_DISCONNECTED, API 31
            15 -> "Too many speech requests." // ERROR_TOO_MANY_REQUESTS, API 31
            else -> "Speech recognition failed (code $code)."
        }
    }
}
