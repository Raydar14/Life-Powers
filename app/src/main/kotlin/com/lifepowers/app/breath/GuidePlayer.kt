package com.lifepowers.app.breath

import com.lifepowers.app.domain.BreathGuide
import com.lifepowers.core.BreathPhase
import javax.inject.Inject
import javax.inject.Singleton

/** Plays the cues a session's [BreathGuide] asks for. The visual guide is drawn by the UI from the live state. */
@Singleton
class GuidePlayer @Inject constructor(
    private val tones: ToneGuide,
    private val voice: VoiceGuide,
    private val vibration: VibrationGuide,
) {
    /** Warms up what needs time to start, such as the voice engine. */
    fun begin(guide: BreathGuide) {
        if (guide.voice) voice.prepare()
    }

    /** A new phase began with [remainingS] seconds left in it. */
    fun cue(phase: BreathPhase, remainingS: Double, guide: BreathGuide) {
        if (guide.tones) tones.onPhase(phase, remainingS)
        if (guide.voice) voice.onPhase(phase)
        if (guide.vibration) vibration.pulse(VibrationGuide.CUE_MS)
    }

    /** Pausing: cut the tone short, keep everything ready. */
    fun pause() {
        tones.silence()
    }

    /** A planned session reached its end: one soft tone, a longer buzz when buzzing is on, then all stops. */
    fun complete(guide: BreathGuide) {
        voice.stop()
        tones.completion()
        if (guide.vibration) vibration.pulse(VibrationGuide.END_MS)
    }

    fun stop() {
        tones.stop()
        voice.stop()
        vibration.stop()
    }
}
