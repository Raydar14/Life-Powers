package com.lifepowers.core

import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AcousticsTest {
    private val sr = 16_000

    /** A 200 Hz tone pulsed on and off like syllables, with silent gaps, over 24 seconds. */
    private fun syntheticSpeech(f0: Double = 200.0, seconds: Double = 24.0): FloatArray {
        val n = (sr * seconds).toInt()
        val out = FloatArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / sr
            val syllable = ((t * 4) % 1.0) < 0.6
            val pause = (t % 3.0) > 2.6
            val env = if (syllable && !pause) 1.0 else 0.0
            out[i] = (env * 0.3 * sin(2 * PI * f0 * t) + 0.001 * sin(2 * PI * 50 * t)).toFloat()
        }
        return out
    }

    @Test fun pitchOfToneIsRecovered() {
        val pcm = syntheticSpeech(200.0)
        val f = Acoustics.analyze(pcm, sr)
        assertTrue(f.qualityOk, "quality note ${f.qualityNote}")
        assertTrue(kotlin.math.abs(f.f0MeanHz - 200.0) < 8.0, "f0 was ${f.f0MeanHz}")
        assertTrue(f.voicedFraction > 0.7, "voiced ${f.voicedFraction}")
        assertTrue(f.pauseCount >= 3, "pauses ${f.pauseCount}")
        assertTrue(f.speechRateHz in 2.0..6.0, "rate ${f.speechRateHz}")
    }

    @Test fun shortClipIsRejected() {
        val f = Acoustics.analyze(syntheticSpeech(200.0, 2.0), sr)
        assertFalse(f.qualityOk)
        assertEquals("too_short", f.qualityNote)
    }

    @Test fun indicesHaveExpectedSigns() {
        val v = VoiceIndexer.indices(zLoudness = -1.0, zRate = -1.0, zF0Range = -1.0, zF0Mean = 0.0, zPauseMean = 1.0, zLoudStd = -1.0)
        assertTrue(v.energy!! < 0)
        assertTrue(v.flatness!! > 0)
    }

    @Test fun gapRuleNeedsTwoOfThree() {
        val fine = WordsVoiceGap.Day(0.3, 0.2, 0.1)
        val off = WordsVoiceGap.Day(0.3, 1.8, 0.1)
        assertFalse(WordsVoiceGap.fires(listOf(fine, fine, off)))
        assertTrue(WordsVoiceGap.fires(listOf(fine, off, off)))
        assertFalse(WordsVoiceGap.fires(listOf(WordsVoiceGap.Day(-1.0, 1.8, 0.1), off, fine)))
    }
}
