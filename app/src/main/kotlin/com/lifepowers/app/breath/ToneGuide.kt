package com.lifepowers.app.breath

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.lifepowers.core.BreathPhase
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Soft synthesized tones: a gentle rise through the in breath, a fall through the out breath, and a
 * quiet tick at the start of a hold. One streaming AudioTrack is fed from a single worker thread in
 * short chunks; a new tone or [stop] bumps the generation so the tone in progress gives way at once.
 */
@Singleton
class ToneGuide @Inject constructor() {
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "breath-tones").apply { isDaemon = true }
    }

    private val generation = AtomicInteger()

    // Touched only on the worker thread.
    private var track: AudioTrack? = null

    /** Starts the tone for [phase], lasting [durationS], the time left in the phase. */
    fun onPhase(phase: BreathPhase, durationS: Double) {
        val samples = when (phase) {
            BreathPhase.INHALE -> ToneSynth.glide(ToneSynth.LOW_HZ, ToneSynth.HIGH_HZ, durationS)
            BreathPhase.EXHALE -> ToneSynth.glide(ToneSynth.HIGH_HZ, ToneSynth.LOW_HZ, durationS)
            BreathPhase.HOLD_IN, BreathPhase.HOLD_OUT -> ToneSynth.tick()
        }
        play(samples, releaseAfter = false)
    }

    /** The single soft tone at the end of a session, after which the audio track is released. */
    fun completion() = play(ToneSynth.completion(), releaseAfter = true)

    /** Cuts any tone short but keeps the track for the next one. */
    fun silence() {
        generation.incrementAndGet()
        submit { t -> pauseAndFlush(t) }
    }

    /** Cuts any tone short and releases the audio track. */
    fun stop() {
        generation.incrementAndGet()
        submit { release() }
    }

    private fun play(samples: ShortArray, releaseAfter: Boolean) {
        if (samples.isEmpty()) return
        val gen = generation.incrementAndGet()
        submit {
            val t = ensureTrack() ?: return@submit
            pauseAndFlush(t)
            try {
                t.play()
            } catch (_: IllegalStateException) {
                release()
                return@submit
            }
            var i = 0
            while (i < samples.size && generation.get() == gen) {
                val n = minOf(CHUNK, samples.size - i)
                val written = t.write(samples, i, n)
                if (written <= 0) break
                i += written
            }
            if (releaseAfter && generation.get() == gen) {
                // In stream mode stop() lets the written audio play out; give it time before letting go.
                runCatching { t.stop() }
                Thread.sleep(TAIL_MS)
                if (generation.get() == gen) release()
            }
        }
    }

    private fun submit(block: (AudioTrack?) -> Unit) {
        try {
            worker.execute { runCatching { block(track) } }
        } catch (_: RejectedExecutionException) {
        }
    }

    private fun pauseAndFlush(t: AudioTrack?) {
        t ?: return
        runCatching {
            t.pause()
            t.flush()
        }
    }

    private fun ensureTrack(): AudioTrack? {
        track?.let { return it }
        val minBytes = AudioTrack.getMinBufferSize(ToneSynth.SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bytes = maxOf(if (minBytes > 0) minBytes else 0, ToneSynth.SAMPLE_RATE * 2 / 10)
        val created = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(ToneSynth.SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bytes)
                .build()
        } catch (_: Exception) {
            null
        }
        if (created != null && created.state != AudioTrack.STATE_INITIALIZED) {
            created.release()
            return null
        }
        track = created
        return created
    }

    private fun release() {
        val t = track ?: return
        track = null
        runCatching {
            t.pause()
            t.flush()
        }
        t.release()
    }

    private companion object {
        /** About 46 ms of audio per write, so a new tone takes over quickly. */
        const val CHUNK = 2048
        const val TAIL_MS = 300L
    }
}
