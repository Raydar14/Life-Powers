package com.lifepowers.app.goals

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Alarms do not survive a reboot or an app update, and local reminder times move when the clock or
 * zone changes, so all of these re-arm every reminder. A running timer's quiet notice is restored too.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in ACTIONS) return
        val pending = goAsync()
        val entry = goalsEntryPoint(context)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                runCatching { entry.reminderScheduler().rescheduleAll() }
                runCatching { entry.timerController().restoreNotice() }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
        )
    }
}
