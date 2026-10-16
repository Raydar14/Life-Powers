package com.lifepowers.app.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Mono 16 kHz PCM 16 bit capture from the VOICE_RECOGNITION source into memory, capped at
 * [maxSeconds]. All reads happen on a dedicated thread; [stop] joins that thread and so must be
 * called off the main thread. Needs RECORD_AUDIO already granted.
 */
class AudioRecorder(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE,
    private val maxSeconds: Int = MAX_SECONDS,
) {
    class Result(val pcm: FloatArray, val sampleRate: Int) {
        val durationS: Double get() = pcm.size.toDouble() / sampleRate
    }

    interface Listener {
        /** Called from the recording thread roughly every 100 ms. [level] is RMS in 0..1. */
        fun onProgress(elapsedSeconds: Int, level: Float)
        /** Called once from the recording thread when [maxSeconds] is reached and reading stops. */
        fun onCapReached()
    }

    private val lock = Any()
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    private val chunks = ArrayList<ShortArray>()
    @Volatile private var totalSamples = 0
    @Volatile private var lastResult: Result? = null

    val isRecording: Boolean get() = running

    /**
     * Returns false when the device refused the configuration or another client holds the microphone
     * (getMinBufferSize ERROR_BAD_VALUE, state not initialized, or startRecording throwing).
     */
    @SuppressLint("MissingPermission")
    fun start(listener: Listener?): Boolean {
        synchronized(lock) {
            if (running) return true
            val minBuf = AudioRecord.getMinBufferSize(sampleRate, CHANNEL, ENCODING)
            if (minBuf == AudioRecord.ERROR_BAD_VALUE || minBuf == AudioRecord.ERROR) return false
            val bufferBytes = maxOf(minBuf * 2, sampleRate) // at least half a second of 16 bit mono
            val rec = try {
                AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, sampleRate, CHANNEL, ENCODING, bufferBytes)
            } catch (e: RuntimeException) {
                // IllegalArgumentException for a refused configuration, SecurityException without permission.
                return false
            }
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                rec.release()
                return false
            }
            try {
                rec.startRecording()
            } catch (e: IllegalStateException) {
                rec.release()
                return false
            }
            if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                rec.stop()
                rec.release()
                return false
            }
            chunks.clear()
            totalSamples = 0
            lastResult = null
            record = rec
            running = true
            val t = Thread({ loop(rec, listener) }, "lp-audio-record")
            t.priority = Thread.MAX_PRIORITY
            thread = t
            t.start()
            return true
        }
    }

    private fun loop(rec: AudioRecord, listener: Listener?) {
        val chunk = ShortArray(sampleRate / 10) // 100 ms
        val cap = sampleRate * maxSeconds
        var capHit = false
        while (running) {
            val n = rec.read(chunk, 0, chunk.size)
            if (n <= 0) {
                if (n == AudioRecord.ERROR_INVALID_OPERATION || n == AudioRecord.ERROR_BAD_VALUE || n == AudioRecord.ERROR_DEAD_OBJECT) break
                continue
            }
            val take = minOf(n, cap - totalSamples)
            if (take > 0) {
                synchronized(chunks) { chunks.add(chunk.copyOf(take)) }
                totalSamples += take
            }
            if (listener != null) {
                var sum = 0.0
                for (i in 0 until n) { val v = chunk[i] / 32768.0; sum += v * v }
                val rms = sqrt(sum / n).toFloat()
                listener.onProgress(totalSamples / sampleRate, minOf(1f, rms * 4f))
            }
            if (totalSamples >= cap) { capHit = true; break }
        }
        running = false
        if (capHit) listener?.onCapReached()
    }

    /** Stops capture, joins the reader thread, and returns the samples normalized to -1..1. */
    fun stop(): Result {
        val rec: AudioRecord?
        val t: Thread?
        synchronized(lock) {
            running = false
            rec = record
            t = thread
            record = null
            thread = null
        }
        try { t?.join(2000) } catch (e: InterruptedException) { Thread.currentThread().interrupt() }
        if (rec != null) {
            try { if (rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) rec.stop() } catch (e: IllegalStateException) { }
            rec.release()
        }
        val out = FloatArray(totalSamples)
        var pos = 0
        synchronized(chunks) {
            for (c in chunks) {
                for (s in c) { if (pos < out.size) out[pos++] = s / 32768f }
            }
            chunks.clear()
        }
        val result = Result(if (pos == out.size) out else out.copyOf(pos), sampleRate)
        lastResult = result
        return result
    }

    /** Writes a 16 bit PCM WAV of [result] (default: the last recording). Blocking; call off the main thread. */
    fun writeWav(file: File, result: Result? = lastResult) {
        val r = result ?: return
        val pcmBytes = r.pcm.size * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + pcmBytes)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1) // PCM
        header.putShort(1) // mono
        header.putInt(r.sampleRate)
        header.putInt(r.sampleRate * 2)
        header.putShort(2)
        header.putShort(16)
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(pcmBytes)
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { out ->
            out.write(header.array())
            val buf = ByteBuffer.allocate(8192).order(ByteOrder.LITTLE_ENDIAN)
            for (v in r.pcm) {
                val s = (v.coerceIn(-1f, 1f) * 32767f).toInt()
                buf.putShort(s.toShort())
                if (!buf.hasRemaining()) { out.write(buf.array()); buf.clear() }
            }
            if (buf.position() > 0) out.write(buf.array(), 0, buf.position())
        }
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE = 16_000
        const val MAX_SECONDS = 90
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }
}
