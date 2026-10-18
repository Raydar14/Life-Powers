package com.lifepowers.app.breath

import com.lifepowers.app.domain.HeartDevice
import com.lifepowers.core.BreathPhase
import com.lifepowers.core.BreathPoint
import com.lifepowers.core.HeartReading
import kotlin.math.roundToLong

/** Small pure pieces of the breath session and heart link, kept apart so they can be tested on the JVM. */
object BreathMath {
    /** Sessions shorter than this are discarded rather than saved. */
    const val MIN_SAVE_S = 30.0

    /** Readings older than this, counted back from the newest, are dropped. */
    const val READINGS_WINDOW_MS = 3 * 60 * 60 * 1000L

    const val FALLBACK_SENSOR_NAME = "Heart rate sensor"

    /** The spoken cue for a phase. */
    fun spokenWord(phase: BreathPhase): String = when (phase) {
        BreathPhase.INHALE -> "in"
        BreathPhase.EXHALE -> "out"
        BreathPhase.HOLD_IN, BreathPhase.HOLD_OUT -> "hold"
    }

    /** Minutes between two moments, rounded to one decimal, never negative. */
    fun minutes(startUtcMs: Long, endUtcMs: Long): Double =
        ((endUtcMs - startUtcMs).coerceAtLeast(0) / 60_000.0 * 10).roundToLong() / 10.0

    /** Wait before reconnect attempt [attempt] (1 based): 1, 2, then 4 seconds. */
    fun backoffMs(attempt: Int): Long = 1000L shl (attempt - 1).coerceIn(0, 5)

    /** Adds or refreshes [device] by address, strongest signal first. */
    fun mergeNearby(list: List<HeartDevice>, device: HeartDevice): List<HeartDevice> =
        (list.filter { it.address != device.address } + device).sortedByDescending { it.rssi }

    /** The name to show for a scanned device. */
    fun displayName(advertised: String?, system: String?): String =
        advertised?.trim()?.takeIf { it.isNotEmpty() } ?: system?.trim()?.takeIf { it.isNotEmpty() } ?: FALLBACK_SENSOR_NAME

    /** Appends [reading] and keeps only the last [windowMs] before it. */
    fun appendCapped(list: List<HeartReading>, reading: HeartReading, windowMs: Long = READINGS_WINDOW_MS): List<HeartReading> {
        val cutoff = reading.utcMs - windowMs
        val firstKept = list.indexOfFirst { it.utcMs >= cutoff }
        val kept = when {
            firstKept < 0 -> emptyList()
            firstKept == 0 -> list
            else -> list.subList(firstKept, list.size)
        }
        return kept + reading
    }

    /**
     * Runtime permissions a heart rate link needs on [sdkInt]: nearby devices on Android 12 and later,
     * fine location before that, since scanning needed it.
     */
    fun requiredPermissions(sdkInt: Int): List<String> =
        if (sdkInt >= 31) listOf("android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT")
        else listOf("android.permission.ACCESS_FINE_LOCATION")
}

/**
 * Notices when the breath moves into a new phase. Keyed on cycle and phase so a short hold that falls
 * between two ticks still yields a change at the next phase. The first point always counts as a change.
 */
class PhaseTracker {
    private var last: Long? = null

    /** The new phase when [point] starts one, else null. */
    fun update(point: BreathPoint): BreathPhase? {
        val key = point.cycle.toLong() * 4 + point.phase.ordinal
        if (key == last) return null
        last = key
        return point.phase
    }

    /** Forgets the last phase, so the next point counts as a change. */
    fun reset() {
        last = null
    }
}

/** Session time that stops while paused. All times are from one monotonic clock, in milliseconds. */
class SessionClock(private val startMs: Long) {
    private var pausedTotalMs = 0L
    private var pausedAtMs: Long? = null

    val paused: Boolean get() = pausedAtMs != null

    fun pause(nowMs: Long) {
        if (pausedAtMs == null) pausedAtMs = nowMs
    }

    fun resume(nowMs: Long) {
        val at = pausedAtMs ?: return
        pausedTotalMs += (nowMs - at).coerceAtLeast(0)
        pausedAtMs = null
    }

    fun elapsedMs(nowMs: Long): Long {
        val end = pausedAtMs ?: nowMs
        return (end - startMs - pausedTotalMs).coerceAtLeast(0)
    }
}
