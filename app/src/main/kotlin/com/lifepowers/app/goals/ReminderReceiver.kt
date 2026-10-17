package com.lifepowers.app.goals

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Receives goal reminder and period closing alarms. Not exported. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != ReminderSchedulerImpl.ACTION_REMINDER && action != ReminderSchedulerImpl.ACTION_CLOSING) return
        val firedAt = System.currentTimeMillis()
        val pending = goAsync()
        val poster = goalsEntryPoint(context).reminderPoster()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (action == ReminderSchedulerImpl.ACTION_REMINDER) {
                    val goalId = intent.getLongExtra(ReminderSchedulerImpl.EXTRA_GOAL_ID, -1L)
                    if (goalId > 0) poster.onReminder(goalId, firedAt)
                } else {
                    poster.onClosing(firedAt)
                }
            } catch (_: Exception) {
                // A reminder that cannot be built is skipped; the next one is armed at app start.
            } finally {
                pending.finish()
            }
        }
    }
}
