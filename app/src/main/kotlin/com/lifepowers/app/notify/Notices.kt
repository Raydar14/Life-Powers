package com.lifepowers.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lifepowers.app.MainActivity
import com.lifepowers.app.R

/**
 * Quiet notices. Every channel is low or default importance, never alarming. Words only; the small
 * icon is the launcher mark because Android requires one.
 */
object Notices {
    const val CHANNEL_REMINDERS = "reminders"
    const val CHANNEL_TIMER = "timer"
    const val CHANNEL_SKY = "sky"
    const val CHANNEL_GROUND = "ground"

    /** Extra on the launch intent naming the route to open, such as "goals" or "sky". */
    const val EXTRA_ROUTE = "route"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_REMINDERS, "Goal reminders", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_TIMER, "Habit timer", NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null) },
                NotificationChannel(CHANNEL_SKY, "Sky notices", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_GROUND, "Ground notices", NotificationManager.IMPORTANCE_LOW),
            )
        )
    }

    fun canPost(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun openIntent(context: Context, route: String?, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            // SINGLE_TOP so a running MainActivity gets onNewIntent instead of being finished and
            // re-created by CLEAR_TOP, which would drop the back stack and any unsaved screen.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            route?.let { putExtra(EXTRA_ROUTE, it) }
        }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Posts or replaces notice [id]. Does nothing when notifications are not allowed. */
    fun post(context: Context, channel: String, id: Int, title: String, text: String, route: String? = null, ongoing: Boolean = false) {
        if (!canPost(context)) return
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openIntent(context, route, id))
            .setAutoCancel(!ongoing)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setPriority(if (channel == CHANNEL_TIMER || channel == CHANNEL_GROUND) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) {
            // Permission was revoked between the check and the post.
        }
    }

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)

    /** Stable id ranges so notices from different features never replace each other. */
    object Ids {
        const val TIMER = 1000
        const val BREATH = 1001
        fun reminder(goalId: Long) = 2000 + (goalId % 10_000).toInt()
        fun closing(goalId: Long) = 20_000 + (goalId % 10_000).toInt()
        const val SKY_EVENING = 40_000
        const val AURORA = 40_001
        fun ground(id: String) = 50_000 + (id.hashCode() and 0x7fff)
    }
}
