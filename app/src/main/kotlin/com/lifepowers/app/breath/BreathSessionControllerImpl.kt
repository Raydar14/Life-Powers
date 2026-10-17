package com.lifepowers.app.breath

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.lifepowers.app.domain.BreathLive
import com.lifepowers.app.domain.BreathRepository
import com.lifepowers.app.domain.BreathSessionController
import com.lifepowers.app.domain.BreathSessionRecord
import com.lifepowers.app.domain.HeartLink
import com.lifepowers.app.domain.HeartLinkState
import com.lifepowers.app.domain.SavedBreathPattern
import com.lifepowers.app.domain.nowZoneOffsetMin
import com.lifepowers.core.BioSnapshot
import com.lifepowers.core.Biofeedback
import com.lifepowers.core.Breathing
import com.lifepowers.core.HeartReading
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs the breath session. A singleton owns the clock, so the session outlives the screen; the
 * [BreathSessionService] only keeps the process in the foreground while it runs. Every 50 ms the
 * point in the pattern is worked out from elapsed time (paused time left out) and cues play on each
 * phase change; once a second the heart numbers are refreshed from the heart link's readings.
 * A planned session finishes on its own with one soft tone. Sessions under 30 seconds are not saved.
 */
@Singleton
class BreathSessionControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: BreathRepository,
    private val heartLink: HeartLink,
    private val guides: GuidePlayer,
) : BreathSessionController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()

    private val _live = MutableStateFlow<BreathLive?>(null)
    override val live: StateFlow<BreathLive?> = _live.asStateFlow()

    private val _lastSavedId = MutableStateFlow<Long?>(null)
    override val lastSavedId: StateFlow<Long?> = _lastSavedId.asStateFlow()

    // Guarded by lock.
    private var session: Running? = null
    private var ticker: Job? = null

    private class Running(
        val saved: SavedBreathPattern,
        val goalId: Long?,
        val startUtcMs: Long,
        val zoneOffsetMin: Int,
        val clock: SessionClock,
    ) {
        val plannedS: Double? = saved.sessionMin?.takeIf { it > 0 }?.let { it * 60.0 }
        val phases = PhaseTracker()
        var sensor: String? = null
        var bio: BioSnapshot? = null
        var recentHr: List<Double?> = emptyList()
        var lastBioUtcMs = 0L
    }

    override fun start(pattern: SavedBreathPattern, goalId: Long?) {
        if (!Breathing.isAllowed(pattern.pattern)) return
        // A second start ends the first session as if it were finished.
        end(save = true, completed = false)

        val s = Running(
            saved = pattern,
            goalId = goalId,
            startUtcMs = System.currentTimeMillis(),
            zoneOffsetMin = nowZoneOffsetMin(),
            clock = SessionClock(SystemClock.elapsedRealtime()),
        )
        guides.begin(pattern.guide)
        synchronized(lock) {
            _lastSavedId.value = null
            session = s
            publishLocked(s, SystemClock.elapsedRealtime())
            ticker?.cancel()
            ticker = scope.launch {
                while (isActive) {
                    tick()
                    delay(TICK_MS)
                }
            }
        }
        val link = heartLink.state.value
        if (heartLink.connected.value == null && link != HeartLinkState.CONNECTING && link != HeartLinkState.CONNECTED) {
            heartLink.connectRemembered()
        }
        startService()
    }

    override fun pause() {
        synchronized(lock) {
            val s = session ?: return
            if (s.clock.paused) return
            s.clock.pause(SystemClock.elapsedRealtime())
            publishLocked(s, SystemClock.elapsedRealtime())
        }
        guides.pause()
    }

    override fun resume() {
        synchronized(lock) {
            val s = session ?: return
            if (!s.clock.paused) return
            s.clock.resume(SystemClock.elapsedRealtime())
            // The phase in progress cues again, for the time left in it.
            s.phases.reset()
            publishLocked(s, SystemClock.elapsedRealtime())
        }
    }

    override fun finish() = end(save = true, completed = false)

    override fun cancel() = end(save = false, completed = false)

    private fun tick() {
        var cue: (() -> Unit)? = null
        val due = synchronized(lock) {
            val s = session ?: return
            val nowMono = SystemClock.elapsedRealtime()
            val nowUtc = System.currentTimeMillis()
            val elapsedS = s.clock.elapsedMs(nowMono) / 1000.0
            val planned = s.plannedS
            if (planned != null && elapsedS >= planned) {
                true
            } else {
                if (!s.clock.paused) {
                    val point = Breathing.pointAt(s.saved.pattern, elapsedS)
                    val guide = s.saved.guide
                    s.phases.update(point)?.let { phase -> cue = { guides.cue(phase, point.phaseRemainingS, guide) } }
                }
                if (nowUtc - s.lastBioUtcMs >= BIO_EVERY_MS) refreshBioLocked(s, nowUtc)
                publishLocked(s, nowMono)
                false
            }
        }
        // Tones are synthesized here, so cues play outside the lock.
        cue?.invoke()
        if (due) end(save = true, completed = true)
    }

    private fun refreshBioLocked(s: Running, nowUtc: Long) {
        s.lastBioUtcMs = nowUtc
        heartLink.connected.value?.let { s.sensor = it.name }
        val readings = sessionReadings(s.startUtcMs, nowUtc)
        if (readings.isEmpty()) {
            s.bio = null
            s.recentHr = emptyList()
            return
        }
        // A sensor that went quiet shows no live numbers rather than an old heart rate.
        val fresh = nowUtc - readings.last().utcMs <= STALE_MS
        s.bio = if (fresh) Biofeedback.snapshot(readings, nowUtc, s.saved.pattern) else null
        s.recentHr = BreathCodec.gapsAsNull(Biofeedback.hrSeries(readings, nowUtc - RECENT_MS, nowUtc))
    }

    private fun publishLocked(s: Running, nowMono: Long) {
        val elapsedS = s.clock.elapsedMs(nowMono) / 1000.0
        _live.value = BreathLive(
            running = true,
            paused = s.clock.paused,
            name = s.saved.name,
            pattern = s.saved.pattern,
            guide = s.saved.guide,
            startUtcMs = s.startUtcMs,
            elapsedS = elapsedS,
            plannedS = s.plannedS,
            point = Breathing.pointAt(s.saved.pattern, elapsedS),
            bio = s.bio,
            recentHr = s.recentHr,
        )
    }

    /**
     * Ends the running session, if any. [completed] means the planned length was reached.
     *
     * The UI reads [lastSavedId] the moment [live] turns null, so the id is set first. While a session
     * is being saved, [live] holds a last frame with running false; it is cleared only if no new
     * session has replaced it in the meantime.
     */
    private fun end(save: Boolean, completed: Boolean) {
        val (s, elapsedS, last) = synchronized(lock) {
            val cur = session ?: return
            session = null
            ticker?.cancel()
            ticker = null
            val e = cur.clock.elapsedMs(SystemClock.elapsedRealtime()) / 1000.0
            val frame = _live.value?.copy(running = false, elapsedS = e, point = Breathing.pointAt(cur.saved.pattern, e))
            _live.value = frame
            Triple(cur, e, frame)
        }
        if (completed) guides.complete(s.saved.guide) else guides.stop()
        stopService()

        if (!save || elapsedS < BreathMath.MIN_SAVE_S) {
            settle(last, null)
            return
        }
        val record = record(s, System.currentTimeMillis())
        scope.launch {
            val id = runCatching { repository.saveSession(record) }.getOrNull()
            settle(last, id)
        }
    }

    /** Publishes the saved id, then clears [live], unless a newer session already took its place. */
    private fun settle(last: BreathLive?, savedId: Long?) {
        synchronized(lock) {
            if (session != null || _live.value !== last) return
            _lastSavedId.value = savedId
            _live.value = null
        }
    }

    private fun record(s: Running, endUtcMs: Long): BreathSessionRecord {
        val pattern = s.saved.pattern
        val readings = sessionReadings(s.startUtcMs, endUtcMs)
        val timeline = if (readings.isEmpty()) {
            emptyList()
        } else {
            BreathCodec.timeline(Biofeedback.hrSeries(readings, s.startUtcMs, endUtcMs))
        }
        val sensor = if (readings.isEmpty()) null else s.sensor ?: heartLink.connected.value?.name
        return BreathSessionRecord(
            patternId = s.saved.id.takeIf { it > 0 },
            patternName = s.saved.name,
            pattern = pattern,
            startUtcMs = s.startUtcMs,
            endUtcMs = endUtcMs,
            zoneOffsetMin = s.zoneOffsetMin,
            sensor = sensor,
            summary = Biofeedback.summary(readings, s.startUtcMs, endUtcMs, pattern),
            hrTimeline = timeline,
            goalId = s.goalId,
        )
    }

    private fun sessionReadings(fromUtcMs: Long, toUtcMs: Long): List<HeartReading> =
        heartLink.readings.value.filter { it.utcMs in fromUtcMs..toUtcMs }

    private fun startService() {
        try {
            ContextCompat.startForegroundService(context, Intent(context, BreathSessionService::class.java))
        } catch (_: IllegalStateException) {
            // Not allowed from the background; the session still runs while the app is open.
        } catch (_: SecurityException) {
        }
    }

    private fun stopService() {
        try {
            context.stopService(Intent(context, BreathSessionService::class.java))
        } catch (_: IllegalStateException) {
        } catch (_: SecurityException) {
        }
    }

    private companion object {
        const val TICK_MS = 50L
        const val BIO_EVERY_MS = 1000L
        const val RECENT_MS = 120_000L
        const val STALE_MS = 15_000L
    }
}
