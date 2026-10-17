package com.lifepowers.app.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.lifepowers.app.data.BehaviorEventDao
import com.lifepowers.app.data.Dates
import com.lifepowers.app.data.SyncCursorDao
import com.lifepowers.app.data.SyncCursorEntity
import com.lifepowers.app.domain.Connector
import com.lifepowers.app.domain.EventRepository
import com.lifepowers.app.domain.ForecastRepository
import com.lifepowers.app.domain.MetricRepository
import com.lifepowers.app.domain.nowZoneOffsetMin
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate

/**
 * Runs every available connector, stores what came back, then rebuilds daily rollups for the
 * touched range and recomputes yesterday's and today's forecasts. One connector failing is
 * recorded on its cursor row and does not stop the others.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val connectors: Set<@JvmSuppressWildcards Connector>,
    private val metrics: MetricRepository,
    private val events: EventRepository,
    private val eventDao: BehaviorEventDao,
    private val forecasts: ForecastRepository,
    private val cursors: SyncCursorDao,
    private val runState: SyncRunState,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val zone = nowZoneOffsetMin()
        val today = Dates.today(zone)
        var earliest: LocalDate? = null

        for (connector in connectors.sortedBy { it.id }) {
            val available = runCatching { connector.isAvailable() }.getOrDefault(false)
            if (!available) continue
            runState.start(connector.id)
            val existing = cursors.get(connector.id)
            try {
                val result = connector.sync(existing?.cursor, zone)
                metrics.upsertSamples(result.samples)
                result.events.forEach { e ->
                    if (eventDao.countAt(e.kind, e.startUtcMs) == 0) events.add(e)
                }
                val minMs = result.samples.minOfOrNull { it.startUtcMs }
                if (minMs != null) {
                    val d = Dates.localDate(minMs, zone)
                    val current = earliest
                    if (current == null || d.isBefore(current)) earliest = d
                }
                cursors.upsert(
                    SyncCursorEntity(
                        source = connector.id,
                        cursor = result.newCursor ?: existing?.cursor,
                        lastSuccessAt = System.currentTimeMillis(),
                        lastError = null,
                    ),
                )
            } catch (e: Exception) {
                cursors.upsert(
                    SyncCursorEntity(
                        source = connector.id,
                        cursor = existing?.cursor,
                        lastSuccessAt = existing?.lastSuccessAt,
                        lastError = e.message ?: e.javaClass.simpleName,
                    ),
                )
            } finally {
                runState.finish(connector.id)
            }
        }

        val from = (earliest ?: today).minusDays(1)
        runCatching { metrics.rebuildDaily(from, today) }
        runCatching { forecasts.recompute(today.minusDays(1)) }
        runCatching { forecasts.recompute(today) }

        return Result.success()
    }

    /** Only used below API 31, where expedited work runs as a foreground service. */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Sync", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val notification: Notification = Notification.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Life Powers")
            .setContentText("Syncing your data")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    companion object {
        const val UNIQUE_NOW = "sync_now"
        const val UNIQUE_PERIODIC = "sync_periodic"
        private const val CHANNEL_ID = "sync"
        private const val NOTIFICATION_ID = 4101
    }
}
