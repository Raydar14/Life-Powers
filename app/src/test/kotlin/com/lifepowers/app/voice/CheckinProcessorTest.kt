package com.lifepowers.app.voice

import com.lifepowers.app.domain.EventKinds
import com.lifepowers.core.AcousticFeatures
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CheckinProcessorTest {
    private val sr = 16_000
    private val processor = CheckinProcessor()

    /** A 200 Hz tone pulsed on and off like syllables, with silent gaps, over 24 seconds. */
    private fun syntheticSpeech(f0: Double = 200.0, seconds: Double = 24.0, amp: Double = 0.3): FloatArray {
        val n = (sr * seconds).toInt()
        val out = FloatArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / sr
            val syllable = ((t * 4) % 1.0) < 0.6
            val pause = (t % 3.0) > 2.6
            val env = if (syllable && !pause) 1.0 else 0.0
            out[i] = (env * amp * sin(2 * PI * f0 * t) + 0.001 * sin(2 * PI * 50 * t)).toFloat()
        }
        return out
    }

    private fun input(
        pcm: FloatArray?,
        transcript: String?,
        baseline: List<AcousticFeatures> = emptyList(),
    ) = CheckinProcessor.Input(
        pcm = pcm,
        sampleRate = sr,
        transcript = transcript,
        promptId = "p1",
        startUtcMs = 1_700_000_000_000L,
        zoneOffsetMin = -300,
        durationS = 24.0,
        baseline = baseline,
    )

    @Test fun wineAndHeadacheTranscriptWithGoodAudio() {
        val transcript = "Honestly fine. Had a glass of wine and was on my phone late. My head is pounding."
        val record = processor.process(input(syntheticSpeech(), transcript))

        val features = assertNotNull(record.features)
        assertTrue(features.qualityOk, "quality note ${features.qualityNote}")
        assertTrue(record.qualityOk)
        assertNull(record.qualityNote)

        val headache = record.symptoms.firstOrNull { it.kind == "headache" }
        assertNotNull(headache, "symptoms were ${record.symptoms}")
        assertEquals(5, headache.severity)

        val suggestions = BehaviorMentions.suggestions(record)
        assertTrue(EventKinds.ALCOHOL in suggestions, "suggestions were $suggestions")
        assertTrue(BehaviorMentions.SCREENS_IN_BED in suggestions)
        assertEquals(1.0, BehaviorMentions.quantityFor(EventKinds.ALCOHOL, transcript))

        assertFalse(record.crisisFlag)
        assertNotNull(record.words)
        // No baseline yet, so no voice indices (design 5.2).
        assertNull(record.indices?.energy)
        assertEquals("p1", record.promptId)
    }

    @Test fun crisisLanguageSetsFlagAndIsNotScored() {
        val record = processor.process(input(syntheticSpeech(), "I want to die"))
        assertTrue(record.crisisFlag)
        assertFalse(record.qualityOk)
        assertEquals("crisis", record.qualityNote)
        assertNotNull(record.transcript)
    }

    @Test fun missingAudioKeepsTranscriptAndMarksNoAudio() {
        val record = processor.process(input(null, "Feeling okay, a bit tired."))
        assertNull(record.features)
        assertFalse(record.qualityOk)
        assertEquals("no_audio", record.qualityNote)
        assertNotNull(record.words)
        assertTrue(record.symptoms.any { it.kind == "fatigue" })
        assertEquals(24.0, record.durationS)
    }

    @Test fun silentCaptureIsTreatedAsNoAudio() {
        val record = processor.process(input(FloatArray(sr * 20), "Fine."))
        assertNull(record.features)
        assertEquals("no_audio", record.qualityNote)
    }

    @Test fun indicesAppearWithABaselineOfFourteen() {
        val base = (0 until 14).map { i ->
            // Slight variation so the robust spread is non zero.
            processor.process(input(syntheticSpeech(f0 = 190.0 + i * 1.5, amp = 0.2 + i * 0.01), "fine")).features!!
        }
        assertTrue(base.all { it.qualityOk })
        val record = processor.process(input(syntheticSpeech(f0 = 240.0, amp = 0.6), "fine", base))
        val indices = assertNotNull(record.indices)
        assertNotNull(indices.energy)
        assertNotNull(indices.tension)
        assertTrue(indices.tension!! > 0, "tension ${indices.tension}")
    }

    @Test fun thirteenGoodRecordingsAreNotEnough() {
        val base = (0 until 13).map { i -> processor.process(input(syntheticSpeech(f0 = 190.0 + i * 1.5), "fine")).features!! }
        val record = processor.process(input(syntheticSpeech(), "fine", base))
        assertNull(record.indices?.energy)
    }
}
