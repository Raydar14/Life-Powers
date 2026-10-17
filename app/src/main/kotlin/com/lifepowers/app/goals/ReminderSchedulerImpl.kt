package com.lifepowers.app.goals

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.lifepowers.app.data.personal.GoalDao
import com.lifepowers.app.data.personal.toDomain
import com.lifepowers.app.domain.ReminderScheduler
import com.lifepowers.core.Cadence
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Arms one inexact alarm per goal at its next reminder time, plus one daily alarm at 18:00 for the
 * period closing notices. Inexact alarms need no special permission; a few minutes late is fine.
 * The set of armed goal ids is kept in plain preferences so alarms of goals that lost their
 * reminders can be cancelled.
 */
@Singleton
class ReminderSchedulerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val goalDao: GoalDao,
) : ReminderScheduler {

    private val mutex = Mutex()
    private val alarms: AlarmManager? get() = context.getSystemService(AlarmManager::class.java)
    private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override suspend fun rescheduleAll(): Unit = rescheduleAfter(System.currentTimeMillis())

    /** Re-arms everything for times strictly after [afterUtcMs]. */
    suspend fun rescheduleAfter(afterUtcMs: Long): Unit = mutex.withLock {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(afterUtcMs), zone)
        val goals = goalDao.activeOnce().map { it.toDomain() }
        val previously = prefs.getStringSet(KEY_ARMED, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

        val armed = mutableSetOf<Long>()
        for (goal in goals) {
            val next = GoalMath.nextReminder(goal.reminderTimes, goal.reminderDays, now) ?: continue
            set(next.atZone(zone).toInstant().toEpochMilli(), reminderIntent(goal.id))
            armed += goal.id
        }
        (previously - armed).forEach { cancel(reminderIntent(it)) }
        prefs.edit().putStringSet(KEY_ARMED, armed.map { it.toString() }.toSet()).apply()

        val wantsClosing = goals.any { it.closingNotice && it.cadence != Cadence.DAILY }
        if (wantsClosing) {
            val at = GoalMath.nextDaily(CLOSING_TIME, now)
            set(at.atZone(zone).toInstant().toEpochMilli(), closingIntent())
        } else {
            cancel(closingIntent())
        }
    }

    private fun set(triggerAtUtcMs: Long, intent: PendingIntent) {
        val am = alarms ?: return
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtUtcMs, intent)
    }

    private fun cancel(intent: PendingIntent) {
        alarms?.cancel(intent)
        intent.cancel()
    }

    private fun reminderIntent(goalId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_REMINDER)
            .setData(Uri.parse("lifepowers://reminder/$goalId"))
            .putExtra(EXTRA_GOAL_ID, goalId)
        return PendingIntent.getBroadcast(context, requestCode(goalId), intent, FLAGS)
    }

    private fun closingIntent(): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_CLOSING)
            .setData(Uri.parse("lifepowers://closing"))
        return PendingIntent.getBroadcast(context, CLOSING_REQUEST, intent, FLAGS)
    }

    companion object {
        const val ACTION_REMINDER = "com.lifepowers.app.action.GOAL_REMINDER"
        const val ACTION_CLOSING = "com.lifepowers.app.action.GOAL_CLOSING"
        const val EXTRA_GOAL_ID = "goal_id"
        val CLOSING_TIME: LocalTime = LocalTime.of(18, 0)

        private const val PREFS = "goal_reminders"
        private const val KEY_ARMED = "armed_goal_ids"
        private const val CLOSING_REQUEST = 7_000_000
        private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        fun requestCode(goalId: Long): Int = 7_100_000 + (goalId % 1_000_000).toInt()
    }
}
