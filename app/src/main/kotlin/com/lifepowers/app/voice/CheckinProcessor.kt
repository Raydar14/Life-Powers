package com.lifepowers.app.voice

import com.lifepowers.app.domain.CheckinRecord
import com.lifepowers.core.AcousticFeatures
import com.lifepowers.core.Acoustics
import com.lifepowers.core.Baseline
import com.lifepowers.core.Lexicon
import com.lifepowers.core.SymptomParser
import com.lifepowers.core.VoiceIndexer
import com.lifepowers.core.VoiceIndices
import com.lifepowers.core.WordsScores
import kotlin.math.abs

/**
 * Turns one recording into a [CheckinRecord]. Pure JVM so it can be tested without Android.
 *
 * Voice indices need a personal baseline of at least [MIN_BASELINE] good recordings (design 5.2) and a
 * good current recording; otherwise all three are null. A crisis match keeps the record but marks it
 * not scorable (design 5.6); the controller decides what to show and writes no metric samples.
 */
class CheckinProcessor {
    data class Input(
        val pcm: FloatArray?,
        val sampleRate: Int,
        val transcript: String?,
        val promptId: String,
        val startUtcMs: Long,
        val zoneOffsetMin: Int,
        /** Wall clock length of the session, used when there is no audio to measure. */
        val durationS: Double,
        /** Features of previous quality ok check-ins, oldest first or any order. */
        val baseline: List<AcousticFeatures>,
    )

    fun process(input: Input): CheckinRecord {
        val transcript = input.transcript?.trim()?.takeIf { it.isNotEmpty() }
        val pcm = input.pcm?.takeIf { it.isNotEmpty() && !isSilent(it) }
        val features = pcm?.let { Acoustics.analyze(it, input.sampleRate) }
        val words: WordsScores? = transcript?.let { Lexicon.score(it) }
        val symptoms = transcript?.let { SymptomParser.parse(it) } ?: emptyList()
        val crisis = CrisisPhrases.matches(transcript)

        val featuresOk = features?.qualityOk == true
        val indices = if (featuresOk && input.baseline.size >= MIN_BASELINE) indices(features!!, input.baseline) else VoiceIndices(null, null, null)

        val qualityNote = when {
            crisis -> "crisis"
            features == null -> "no_audio"
            else -> features.qualityNote
        }
        return CheckinRecord(
            startUtcMs = input.startUtcMs,
            zoneOffsetMin = input.zoneOffsetMin,
            durationS = features?.durationS ?: input.durationS,
            promptId = input.promptId,
            transcript = transcript,
            features = features,
            words = words,
            indices = indices,
            symptoms = symptoms,
            qualityOk = featuresOk && !crisis,
            qualityNote = qualityNote,
            crisisFlag = crisis,
        )
    }

    private fun indices(f: AcousticFeatures, baseline: List<AcousticFeatures>): VoiceIndices =
        VoiceIndexer.indices(
            zLoudness = Baseline.z(f.loudnessMeanDb, baseline.map { it.loudnessMeanDb }),
            zRate = Baseline.z(f.speechRateHz, baseline.map { it.speechRateHz }),
            zF0Range = Baseline.z(f.f0RangeHz, baseline.map { it.f0RangeHz }),
            zF0Mean = Baseline.z(f.f0MeanHz, baseline.map { it.f0MeanHz }),
            zPauseMean = Baseline.z(f.pauseMeanS, baseline.map { it.pauseMeanS }),
            zLoudStd = Baseline.z(f.loudnessStdDb, baseline.map { it.loudnessStdDb }),
        )

    /** True when the capture never rose above roughly -60 dBFS, which happens when another client holds the mic; treated as no audio. */
    private fun isSilent(pcm: FloatArray): Boolean {
        var peak = 0f
        for (v in pcm) { val a = abs(v); if (a > peak) peak = a }
        return peak < SILENCE_PEAK
    }

    companion object {
        const val MIN_BASELINE = 14
        const val SILENCE_PEAK = 0.001f
    }
}
