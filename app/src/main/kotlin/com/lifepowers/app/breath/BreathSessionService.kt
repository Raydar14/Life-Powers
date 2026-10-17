package com.lifepowers.app.breath

import android.Manifest
import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.lifepowers.app.R
import com.lifepowers.app.notify.Notices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the process in the foreground while a breath session runs, so tones, voice, and the heart
 * link carry on with the screen off. It holds no session state: [BreathSessionControllerImpl] starts
 * and stops it, and it stops itself when the session is gone. Shows a quiet ongoing notice that opens
 * the session screen.
 */
class BreathSessionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val entry by lazy { breathEntryPoint(this) }
    private var watching: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val live = entry.controller().live.value
        // startForeground must come first, even when the session already ended, or the system stops the app.
        if (!goForeground(live?.name, entry.heartLink().connected.value != null)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (live == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        acquireWakeLock(live.plannedS)
        if (watching == null) {
            watching = scope.launch {
                launch {
                    entry.controller().live.first { it == null }
                    stopSelf()
                }
                launch {
                    // A sensor connecting or dropping mid session updates the foreground types.
                    entry.heartLink().connected.map { it != null }.distinctUntilChanged().drop(1).collect { connected ->
                        entry.controller().live.value?.let { goForeground(it.name, connected) }
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun goForeground(name: String?, heartConnected: Boolean): Boolean {
        val notice = notice(name?.takeIf { it.isNotBlank() } ?: "Breathing")
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val media = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                val withDevice = media or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                val useDevice = heartConnected && entry.heartLink().hasPermissions()
                try {
                    ServiceCompat.startForeground(this, NOTICE_ID, notice, if (useDevice) withDevice else media)
                } catch (_: SecurityException) {
                    // The connected device type needs Bluetooth permission; fall back to playback alone.
                    ServiceCompat.startForeground(this, NOTICE_ID, notice, media)
                }
            } else {
                startForeground(NOTICE_ID, notice)
            }
            true
        } catch (_: IllegalStateException) {
            // Includes ForegroundServiceStartNotAllowedException on Android 12 and later.
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private fun notice(title: String): Notification {
        Notices.ensureChannels(this)
        return NotificationCompat.Builder(this, Notices.CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(TEXT)
            .setContentIntent(Notices.openIntent(this, ROUTE, NOTICE_ID))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * Keeps the CPU awake so cues stay on time with the screen off. Only when the app declares WAKE_LOCK;
     * without it cues may drift while the phone sleeps, but the session itself keeps correct time.
     */
    private fun acquireWakeLock(plannedS: Double?) {
        if (wakeLock?.isHeld == true) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WAKE_LOCK) != PackageManager.PERMISSION_GRANTED) return
        val pm = getSystemService(PowerManager::class.java) ?: return
        val timeoutMs = plannedS?.let { (it * 1000).toLong() + WAKE_MARGIN_MS }?.coerceAtMost(WAKE_MAX_MS) ?: WAKE_MAX_MS
        wakeLock = try {
            pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_TAG).apply {
                setReferenceCounted(false)
                acquire(timeoutMs)
            }
        } catch (_: SecurityException) {
            null
        }
    }

    private fun releaseWakeLock() {
        val lock = wakeLock ?: return
        wakeLock = null
        runCatching { if (lock.isHeld) lock.release() }
    }

    companion object {
        const val ROUTE = "breathe/session"
        const val TEXT = "Breathing. Tap to return."

        val NOTICE_ID = Notices.Ids.BREATH

        private const val WAKE_TAG = "LifePowers:breath"
        private const val WAKE_MARGIN_MS = 5 * 60_000L
        private const val WAKE_MAX_MS = 3 * 60 * 60_000L
    }
}
