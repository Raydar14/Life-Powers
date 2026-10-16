package com.lifepowers.core

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Paralinguistic features from mono PCM audio. Deliberately small and dependency free:
 * loudness, pitch by autocorrelation, voiced fraction, pauses, and a speech rate proxy.
 */
data class AcousticFeatures(
    val durationS: Double,
    val f0MeanHz: Double,
    val f0StdHz: Double,
    val f0RangeHz: Double,
    val loudnessMeanDb: Double,
    val loudnessStdDb: Double,
    val voicedFraction: Double,
    val pauseCount: Int,
    val pauseMeanS: Double,
    val speechRateHz: Double,
    val qualityOk: Boolean,
    val qualityNote: String?,
)

object Acoustics {
    const val FRAME_MS = 25
    const val HOP_MS = 10
    const val F0_MIN = 75.0
    const val F0_MAX = 400.0
    const val PAUSE_MIN_MS = 200
    const val MIN_SPEECH_S = 8.0

    fun analyze(pcm: FloatArray, sampleRate: Int): AcousticFeatures {
        val frameLen = sampleRate * FRAME_MS / 1000
        val hop = sampleRate * HOP_MS / 1000
        if (pcm.size < frameLen * 4) return empty(pcm.size.toDouble() / sampleRate, "too_short")

        val nFrames = (pcm.size - frameLen) / hop + 1
        val rmsDb = DoubleArray(nFrames)
        val f0 = DoubleArray(nFrames)
        val voiced = BooleanArray(nFrames)

        for (i in 0 until nFrames) {
            val start = i * hop
            var sum = 0.0
            for (j in 0 until frameLen) { val v = pcm[start + j]; sum += v * v }
            val rms = sqrt(sum / frameLen)
            rmsDb[i] = 20 * ln(maxOf(rms, 1e-6).toDouble()) / ln(10.0)
        }
        val noiseFloor = Baseline.percentile(rmsDb.toList(), 0.1)
        val speechLevel = Baseline.percentile(rmsDb.toList(), 0.9)
        val energyGate = noiseFloor + (speechLevel - noiseFloor) * 0.35

        for (i in 0 until nFrames) {
            if (rmsDb[i] < energyGate) continue
            val start = i * hop
            val (hz, clarity) = pitchAutocorr(pcm, start, frameLen, sampleRate)
            if (hz in F0_MIN..F0_MAX && clarity > 0.6) { f0[i] = hz; voiced[i] = true }
        }

        val voicedIdx = (0 until nFrames).filter { voiced[it] }
        val speechIdx = (0 until nFrames).filter { rmsDb[it] >= energyGate }
        val durationS = pcm.size.toDouble() / sampleRate
        if (speechIdx.isEmpty()) return empty(durationS, "too_short")
        if (speechLevel - noiseFloor < 10.0) return empty(durationS, "noise")

        val f0s = voicedIdx.map { f0[it] }
        val f0Mean = f0s.average()
        val f0Std = sd(f0s)
        val f0Range = Baseline.percentile(f0s, 0.95) - Baseline.percentile(f0s, 0.05)
        val loud = speechIdx.map { rmsDb[it] }

        // Pauses: runs of frames below the gate lasting at least PAUSE_MIN_MS, inside the speech span.
        val firstSpeech = speechIdx.first(); val lastSpeech = speechIdx.last()
        val pauses = ArrayList<Int>()
        var run = 0
        for (i in firstSpeech..lastSpeech) {
            if (rmsDb[i] < energyGate) run++ else { if (run * HOP_MS >= PAUSE_MIN_MS) pauses += run; run = 0 }
        }
        val pauseMean = if (pauses.isEmpty()) 0.0 else pauses.average() * HOP_MS / 1000.0
        // Speaking time is the span from first to last speech frame minus the pauses inside it.
        val speechS = ((lastSpeech - firstSpeech + 1) - pauses.sum()) * HOP_MS / 1000.0
        if (speechS < MIN_SPEECH_S) return empty(durationS, "too_short")

        // Speech rate proxy: syllable nuclei per second of speech. Smooth the loudness envelope,
        // then count peaks that stand at least 3 dB above their surroundings and sit at least 100 ms apart.
        val env = DoubleArray(nFrames) { i ->
            var acc = 0.0; var c = 0
            for (k in maxOf(0, i - 2)..minOf(nFrames - 1, i + 2)) { acc += rmsDb[k]; c++ }
            acc / c
        }
        var peaks = 0
        var lastPeak = -100
        val half = 6
        for (i in half until nFrames - half) {
            if (!voiced[i] || i - lastPeak < 10) continue
            var isMax = true; var floor = Double.MAX_VALUE
            for (k in i - half..i + half) { if (k != i && env[k] > env[i]) { isMax = false; break }; if (env[k] < floor) floor = env[k] }
            if (isMax && env[i] - floor >= 3.0) { peaks++; lastPeak = i }
        }
        val rate = peaks / speechS

        return AcousticFeatures(
            durationS = durationS,
            f0MeanHz = f0Mean, f0StdHz = f0Std, f0RangeHz = f0Range,
            loudnessMeanDb = loud.average(), loudnessStdDb = sd(loud),
            voicedFraction = voicedIdx.size.toDouble() / maxOf(1, speechIdx.size),
            pauseCount = pauses.size, pauseMeanS = pauseMean,
            speechRateHz = rate,
            qualityOk = true, qualityNote = null,
        )
    }

    private fun empty(duration: Double, note: String) = AcousticFeatures(duration, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0.0, 0.0, false, note)

    /** Returns (f0 Hz, clarity 0..1) by normalized autocorrelation over the pitch range. */
    internal fun pitchAutocorr(pcm: FloatArray, start: Int, len: Int, sr: Int): Pair<Double, Double> {
        val minLag = (sr / F0_MAX).toInt()
        val maxLag = minOf((sr / F0_MIN).toInt(), len - 1)
        var mean = 0.0
        for (j in 0 until len) mean += pcm[start + j]
        mean /= len
        var energy = 0.0
        for (j in 0 until len) { val v = pcm[start + j] - mean; energy += v * v }
        if (energy <= 1e-9) return 0.0 to 0.0
        var bestLag = 0; var best = 0.0
        for (lag in minLag..maxLag) {
            var acc = 0.0
            for (j in 0 until len - lag) acc += (pcm[start + j] - mean) * (pcm[start + j + lag] - mean)
            val norm = acc / energy
            if (norm > best) { best = norm; bestLag = lag }
        }
        if (bestLag == 0) return 0.0 to 0.0
        return sr.toDouble() / bestLag to best
    }

    private fun sd(xs: List<Double>): Double {
        if (xs.size < 2) return 0.0
        val m = xs.average()
        return sqrt(xs.sumOf { (it - m) * (it - m) } / (xs.size - 1))
    }
}

/** Composite voice indices from per feature z scores against the person's own baseline. */
data class VoiceIndices(val energy: Double?, val flatness: Double?, val tension: Double?)

object VoiceIndexer {
    /**
     * Each argument is the z score of that feature against the trailing baseline, or null.
     * Signs are chosen so a higher index means more of the named quality.
     */
    fun indices(
        zLoudness: Double?, zRate: Double?, zF0Range: Double?, zF0Mean: Double?,
        zPauseMean: Double?, zLoudStd: Double?,
    ): VoiceIndices {
        val energy = mean(zLoudness, zRate, zF0Range)
        val flatness = mean(zF0Range?.let { -it }, zLoudStd?.let { -it }, zRate?.let { -it }, zPauseMean)
        val tension = mean(zF0Mean, zRate, zPauseMean?.let { -it })
        return VoiceIndices(energy, flatness, tension)
    }

    private fun mean(vararg zs: Double?): Double? {
        val xs = zs.filterNotNull()
        return if (xs.isEmpty()) null else xs.average()
    }
}

/** The gap rule from the design: words say fine, voice says otherwise, on two of the last three check-ins. */
object WordsVoiceGap {
    const val THRESHOLD_SD = 1.5

    data class Day(val wordsValenceZ: Double?, val flatnessZ: Double?, val tensionZ: Double?)

    fun fires(lastThree: List<Day>): Boolean {
        val hits = lastThree.takeLast(3).count { d ->
            val wordsFine = (d.wordsValenceZ ?: return@count false) >= 0.0
            val voiceOff = (d.flatnessZ ?: 0.0) >= THRESHOLD_SD || (d.tensionZ ?: 0.0) >= THRESHOLD_SD
            wordsFine && voiceOff
        }
        return hits >= 2
    }

    fun gap(wordsValenceZ: Double?, flatnessZ: Double?): Double? {
        if (wordsValenceZ == null || flatnessZ == null) return null
        return wordsValenceZ + flatnessZ
    }
}
