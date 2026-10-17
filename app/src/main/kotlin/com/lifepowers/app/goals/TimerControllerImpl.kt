package com.lifepowers.app.goals

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.lifepowers.app.R
import com.lifepowers.app.data.Dates
import com.lifepowers.app.data.personal.GoalDao
import com.lifepowers.app.data.personal.TimerSessionDao
import com.lifepowers.app.data.personal.TimerSessionEntity
import com.lifepowers.app.data.personal.toDomain
import com.lifepowers.app.domain.BehaviorEvent
import com.lifepowers.app.domain.EventKinds
import com.lifepowers.app.domain.EventRepository
import com.lifepowers.app.domain.GoalRepository
import com.lifepowers.app.domain.MetricRepository
import com.lifepowers.app.domain.TimerController
import com.lifepowers.app.domain.TimerSession
import com.lifepowers.app.domain.nowZoneOffsetMin
import com.lifepowers.app.notify.Notices
import com.lifepowers.core.Metrics
import com.lifepowers.core.Sample
import com.lifepowers.core.TargetKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The habit timer. The running session is a timer_session row with no end, so it survives process
 * death; while it runs a quiet ongoing notice shows the elapsed time. Stopping logs the real minutes
 * against the goal, adds a habit_session moment to the timeline, and a habit.minutes sample.
 */
@Singleton
class TimerControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: TimerSessionDao,
    private val goalDao: GoalDao,
    private val goals: GoalRepository,
    private val events: EventRepository,
    private val metrics: MetricRepository,
) : TimerController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val _running = MutableStateFlow<TimerSession?>(null)
    override val running: StateFlow<TimerSession?> = _running.asStateFlow()

    private val loaded: Job = scope.launch {
        val row = runCatching { dao.running() }.getOrNull() ?: return@launch
        _running.value = row.toDomain()
        runCatching { postOngoing(row.toDomain()) }
    }

    override suspend fun start(goalId: Long?, plannedMin: Int?) {
        loaded.join()
        mutex.withLock {
            dao.running()?.let { finish(it, cancelWork = true) }
            val entity = TimerSessionEntity(
                goalId = goalId,
                startUtc = System.currentTimeMillis(),
                endUtc = null,
                plannedMin = plannedMin?.takeIf { it > 0 },
                zoneOffsetMin = nowZoneOffsetMin(),
            )
            val session = entity.copy(id = dao.insert(entity)).toDomain()
            _running.value = session
            postOngoing(session)
            val planned = session.plannedMin
            if (planned != null) enqueueDone(session.id, planned) else cancelDoneWork()
        }
    }

    override suspend fun stop(): TimerSession? {
        loaded.join()
        return mutex.withLock {
            val row = dao.running()
            if (row == null) {
                clear(cancelWork = true)
                null
            } else {
                finish(row, cancelWork = true)
            }
        }
    }

    override suspend fun cancel() {
        loaded.join()
        mutex.withLock {
            dao.running()?.let { dao.delete(it.id) }
            clear(cancelWork = true)
        }
    }

    /**
     * Called by [TimerDoneWorker] when a planned length is up. Ends [sessionId] only if it is still the
     * running session, and leaves the unique work alone since that work is the caller.
     */
    suspend fun finishPlanned(sessionId: Long): TimerSession? {
        loaded.join()
        return mutex.withLock {
            val row = dao.running()
            if (row == null || row.id != sessionId) null else finish(row, cancelWork = false)
        }
    }

    /** Puts the ongoing notice back after a reboot, when a session is still open. */
    suspend fun restoreNotice() {
        loaded.join()
        val row = dao.running() ?: return
        _running.value = row.toDomain()
        postOngoing(row.toDomain())
    }

    private suspend fun finish(row: TimerSessionEntity, cancelWork: Boolean): TimerSession? {
        val end = System.currentTimeMillis()
        clear(cancelWork)
        val rawMinutes = (end - row.startUtc) / 60_000.0
        if (rawMinutes < GoalMath.MIN_SESSION_MIN) {
            dao.delete(row.id)
            return null
        }
        val minutes = GoalMath.minutes(row.startUtc, end)
        val ended = row.copy(endUtc = end)
        dao.update(ended)

        val date = Dates.localDate(row.startUtc, row.zoneOffsetMin)
        val goal = row.goalId?.let { goalDao.byIdOnce(it) }?.toDomain()
        if (goal != null) {
            goals.check(goal.id, date, if (goal.kind == TargetKind.MINUTES) minutes else 1.0, SOURCE)
        }
        events.add(
            BehaviorEvent(
                kind = EventKinds.HABIT_SESSION,
                startUtcMs = row.startUtc,
                endUtcMs = end,
                zoneOffsetMin = row.zoneOffsetMin,
                quantity = minutes,
                unit = "min",
                note = goal?.name,
                source = SOURCE,
            ),
        )
        metrics.upsertSamples(listOf(Sample(Metrics.HABIT_MINUTES, minutes, row.startUtc, end, row.zoneOffsetMin, SOURCE)))
        metrics.rebuildDaily(date, date)
        return ended.toDomain()
    }

    private fun clear(cancelWork: Boolean) {
        _running.value = null
        Notices.cancel(context, Notices.Ids.TIMER)
        if (cancelWork) cancelDoneWork()
    }

    private suspend fun postOngoing(session: TimerSession) {
        if (!Notices.canPost(context)) return
        Notices.ensureChannels(context)
        val title = session.goalId?.let { goalDao.byIdOnce(it) }?.name ?: "Timer"
        val notice = NotificationCompat.Builder(context, Notices.CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText("Running quietly. Tap to return.")
            .setContentIntent(Notices.openIntent(context, ROUTE, Notices.Ids.TIMER))
            .setShowWhen(true)
            .setWhen(session.startUtcMs)
            .setUsesChronometer(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(Notices.Ids.TIMER, notice)
        } catch (_: SecurityException) {
            // Permission was revoked between the check and the post.
        }
    }

    private fun enqueueDone(sessionId: Long, plannedMin: Int) {
        val request = OneTimeWorkRequestBuilder<TimerDoneWorker>()
            .setInitialDelay(plannedMin.toLong(), TimeUnit.MINUTES)
            .setInputData(workDataOf(TimerDoneWorker.KEY_SESSION_ID to sessionId))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(TimerDoneWorker.UNIQUE, ExistingWorkPolicy.REPLACE, request)
    }

    private fun cancelDoneWork() {
        WorkManager.getInstance(context).cancelUniqueWork(TimerDoneWorker.UNIQUE)
    }

    companion object {
        const val SOURCE = "timer"
        const val ROUTE = "timer"
    }
}
