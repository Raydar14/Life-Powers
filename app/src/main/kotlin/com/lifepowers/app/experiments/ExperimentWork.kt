package com.lifepowers.app.experiments

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lifepowers.app.data.personal.ExperimentDao
import com.lifepowers.app.notify.Notices
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Scheduling for the hourly experiment check. */
object ExperimentWork {
    const val UNIQUE_PERIODIC = "experiment_reminder_periodic"
    const val UNIQUE_NOW = "experiment_reminder_now"
    const val ROUTE = "experiments"

    /** Call once at app start, and again whenever an experiment starts. Keeps work already scheduled. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<ExperimentReminderWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** One check right away, so a fresh experiment started in its reminder hour still gets today's notice. */
    fun checkNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<ExperimentReminderWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NOW, ExistingWorkPolicy.REPLACE, request)
    }
}

/**
 * Remembers which notices went out, per experiment: the last date a day notice was posted, and
 * whether the completion notice has been posted.
 */
class ExperimentNoticeMarks(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun notifiedOn(id: Long, date: LocalDate): Boolean = prefs.getString(dayKey(id), null) == date.toString()

    fun markNotified(id: Long, date: LocalDate) {
        prefs.edit().putString(dayKey(id), date.toString()).apply()
    }

    fun completeNotified(id: Long): Boolean = prefs.getBoolean(doneKey(id), false)

    fun markComplete(id: Long) {
        prefs.edit().putBoolean(doneKey(id), true).apply()
    }

    /** Forgets an experiment, so a later one that reuses the id starts clean. */
    fun clear(id: Long) {
        prefs.edit().remove(dayKey(id)).remove(doneKey(id)).apply()
    }

    private fun dayKey(id: Long) = "day_$id"
    private fun doneKey(id: Long) = "done_$id"

    companion object {
        const val FILE = "experiment_notices"
    }
}

/**
 * Runs every hour. For each running experiment whose reminder hour has come, posts which kind of
 * day today is, once per day. When an experiment has passed its target days, posts once that it is
 * complete. Never ends an experiment by itself.
 */
@HiltWorker
class ExperimentReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dao: ExperimentDao,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val marks = ExperimentNoticeMarks(applicationContext)
        val running = try {
            dao.runningOnce()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "could not read experiments", e)
            return Result.success()
        }
        for (row in running) {
            try {
                val record = ExperimentMath.toRecord(row)
                val id = record.spec.id
                val notice = ExperimentMath.noticeFor(
                    record = record,
                    today = today,
                    hour = now.hour,
                    notifiedToday = marks.notifiedOn(id, today),
                    completeNotified = marks.completeNotified(id),
                ) ?: continue
                val text = when (notice) {
                    is ExperimentNotice.Today -> notice.text
                    ExperimentNotice.Complete -> ExperimentMath.COMPLETE_TEXT
                }
                // With notices off nothing is marked, so the notice still arrives once they are allowed.
                if (!Notices.canPost(applicationContext)) continue
                Notices.post(
                    context = applicationContext,
                    channel = Notices.CHANNEL_REMINDERS,
                    id = ExperimentMath.noticeId(id),
                    title = record.name.ifBlank { "Your experiment" },
                    text = text,
                    route = ExperimentWork.ROUTE,
                )
                when (notice) {
                    is ExperimentNotice.Today -> marks.markNotified(id, today)
                    ExperimentNotice.Complete -> marks.markComplete(id)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "experiment notice failed", e)
            }
        }
        return Result.success()
    }

    private companion object {
        const val TAG = "Experiments"
    }
}
