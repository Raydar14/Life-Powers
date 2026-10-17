package com.lifepowers.app.goals

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lifepowers.app.notify.Notices
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Ends a timer when its planned length is up and says how much was logged. */
@HiltWorker
class TimerDoneWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val timer: TimerControllerImpl,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val sessionId = inputData.getLong(KEY_SESSION_ID, -1L)
        if (sessionId <= 0) return Result.success()
        val done = timer.finishPlanned(sessionId) ?: return Result.success()
        val end = done.endUtcMs ?: System.currentTimeMillis()
        val minutes = GoalMath.formatAmount(GoalMath.minutes(done.startUtcMs, end))
        val unit = if (minutes == "1") "minute" else "minutes"
        Notices.post(
            applicationContext,
            Notices.CHANNEL_REMINDERS,
            Notices.Ids.TIMER,
            "Time is up",
            "$minutes $unit logged. Nicely done.",
            route = TimerControllerImpl.ROUTE,
        )
        return Result.success()
    }

    companion object {
        const val UNIQUE = "timer_done"
        const val KEY_SESSION_ID = "session_id"
    }
}
