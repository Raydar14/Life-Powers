package com.lifepowers.app.breath

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** A short buzz at each change of the breath. Quietly does nothing on phones without a vibrator. */
@Singleton
class VibrationGuide @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val vibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Exception) {
            null
        }
    }

    fun pulse(ms: Long = CUE_MS) {
        val v = vibrator ?: return
        try {
            if (v.hasVibrator()) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: SecurityException) {
        } catch (_: IllegalArgumentException) {
        }
    }

    fun stop() {
        try {
            vibrator?.cancel()
        } catch (_: SecurityException) {
        }
    }

    companion object {
        const val CUE_MS = 40L
        const val END_MS = 120L
    }
}
