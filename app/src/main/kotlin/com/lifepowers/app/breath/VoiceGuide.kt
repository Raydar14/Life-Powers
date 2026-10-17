package com.lifepowers.app.breath

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import com.lifepowers.core.BreathPhase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Speaks "in", "hold", or "out" at each change with the phone's own text to speech. Starts the engine
 * on [prepare]; until it is ready, only the latest word waits and is spoken once the engine is up.
 * Any failure means the voice simply stays quiet.
 */
@Singleton
class VoiceGuide @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val main = Handler(Looper.getMainLooper())

    // Touched only on the main thread.
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    fun prepare() {
        main.post {
            if (tts == null) {
                // A stopped engine's late init must not mark a newer engine ready, or the newer one is
                // spoken to before it is up and then shut down by the failed language check.
                var created: TextToSpeech? = null
                created = try {
                    TextToSpeech(context) { status -> main.post { if (tts === created) onInit(status) } }
                } catch (_: Exception) {
                    null
                }
                tts = created
            }
        }
    }

    fun onPhase(phase: BreathPhase) {
        val word = BreathMath.spokenWord(phase)
        main.post {
            if (ready) {
                speak(word)
            } else if (tts != null) {
                pending = word
            }
        }
    }

    /** Stops speaking and shuts the engine down. */
    fun stop() {
        main.post {
            val t = tts
            tts = null
            ready = false
            pending = null
            runCatching {
                t?.stop()
                t?.shutdown()
            }
        }
    }

    private fun onInit(status: Int) {
        val t = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            stop()
            return
        }
        val english = listOf(Locale.getDefault(), Locale.US, Locale.UK, Locale.ENGLISH).filter { it.language == "en" }
        val chosen = english.firstOrNull { locale ->
            runCatching { t.setLanguage(locale) >= TextToSpeech.LANG_AVAILABLE }.getOrDefault(false)
        }
        if (chosen == null) {
            stop()
            return
        }
        runCatching {
            t.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            t.setSpeechRate(0.85f)
        }
        ready = true
        pending?.let { speak(it) }
        pending = null
    }

    private fun speak(word: String) {
        runCatching { tts?.speak(word, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE) }
    }

    private companion object {
        const val UTTERANCE = "breath_cue"
    }
}
