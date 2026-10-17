package com.lifepowers.app.connectors

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.lifepowers.app.domain.Connector
import com.lifepowers.app.domain.ConnectorResult
import com.lifepowers.core.Metrics
import com.lifepowers.core.Sample
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Screen time from UsageStatsManager. The cursor is the ISO date of the last fully synced day;
 * at most the last 7 local days are rebuilt, today included as a partial day that the next sync
 * replaces.
 */
@Singleton
class UsageStatsConnector @Inject constructor(
    @ApplicationContext private val context: Context,
) : Connector {

    override val id: String = ID
    override val displayName: String = "Screen time"

    override suspend fun isAvailable(): Boolean = hasUsageAccess(context)

    override suspend fun sync(cursor: String?, zoneOffsetMin: Int): ConnectorResult = withContext(Dispatchers.IO) {
        val zone = ZoneOffset.ofTotalSeconds(zoneOffsetMin * 60)
        val today = LocalDate.now(zone)
        val earliest = today.minusDays((MAX_DAYS - 1).toLong())
        val fromCursor = cursor?.let { runCatching { LocalDate.parse(it).plusDays(1) }.getOrNull() }
        var day = if (fromCursor != null && fromCursor.isAfter(earliest)) fromCursor else earliest
        if (day.isAfter(today)) day = today

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val nowMs = System.currentTimeMillis()
        val out = ArrayList<Sample>()
        while (!day.isAfter(today)) {
            val dayStart = day.atStartOfDay().toInstant(zone).toEpochMilli()
            val queryEnd = minOf(dayStart + QUERY_WINDOW_MS, nowMs)
            if (queryEnd > dayStart) {
                val intervals = ScreenIntervals.reconstruct(readEvents(usm, dayStart, queryEnd), queryEnd)
                val summary = ScreenIntervals.summarize(intervals, dayStart)
                out += Sample(Metrics.SCREEN_TOTAL_MIN, summary.totalMin, dayStart, null, zoneOffsetMin, SOURCE)
                out += Sample(Metrics.SCREEN_AFTER_21_MIN, summary.after21Min, dayStart, null, zoneOffsetMin, SOURCE)
                summary.lastUseUtcMs?.let { last ->
                    out += Sample(Metrics.SCREEN_LAST_USE_UTC, last.toDouble(), last, last, zoneOffsetMin, SOURCE)
                }
            }
            day = day.plusDays(1)
        }
        ConnectorResult(samples = out, newCursor = today.minusDays(1).toString())
    }

    private fun readEvents(usm: UsageStatsManager, fromMs: Long, toMs: Long): List<ScreenEvent> {
        val events = usm.queryEvents(fromMs, toMs) ?: return emptyList()
        val out = ArrayList<ScreenEvent>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val type = classify(e.eventType) ?: continue
            out += ScreenEvent(e.timeStamp, e.packageName ?: "", type)
        }
        return out
    }

    private fun classify(eventType: Int): ScreenEvent.Type? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) classifyQ(eventType) else classifyLegacy(eventType)

    private fun classifyQ(eventType: Int): ScreenEvent.Type? = when (eventType) {
        UsageEvents.Event.ACTIVITY_RESUMED -> ScreenEvent.Type.FOREGROUND
        UsageEvents.Event.ACTIVITY_PAUSED -> ScreenEvent.Type.BACKGROUND
        UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.DEVICE_SHUTDOWN -> ScreenEvent.Type.SCREEN_OFF
        else -> null
    }

    @Suppress("DEPRECATION")
    private fun classifyLegacy(eventType: Int): ScreenEvent.Type? = when (eventType) {
        UsageEvents.Event.MOVE_TO_FOREGROUND -> ScreenEvent.Type.FOREGROUND
        UsageEvents.Event.MOVE_TO_BACKGROUND -> ScreenEvent.Type.BACKGROUND
        UsageEvents.Event.SCREEN_NON_INTERACTIVE -> ScreenEvent.Type.SCREEN_OFF
        else -> null
    }

    companion object {
        const val ID = "usage_stats"
        const val SOURCE = "usage_stats"
        private const val MAX_DAYS = 7
        /** A day's query runs to 04:00 the next morning so the last use before sleep is caught. */
        private val QUERY_WINDOW_MS = Duration.ofHours(28).toMillis()

        /** Opens the system screen where the user grants usage access. */
        fun settingsIntent(): Intent =
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        fun hasUsageAccess(context: Context): Boolean {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            } else {
                legacyCheckOp(appOps, context)
            }
            return when (mode) {
                AppOpsManager.MODE_ALLOWED -> true
                AppOpsManager.MODE_DEFAULT ->
                    context.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) ==
                        PackageManager.PERMISSION_GRANTED
                else -> false
            }
        }

        @Suppress("DEPRECATION")
        private fun legacyCheckOp(appOps: AppOpsManager, context: Context): Int =
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    }
}
