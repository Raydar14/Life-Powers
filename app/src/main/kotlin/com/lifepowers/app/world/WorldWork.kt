package com.lifepowers.app.world

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lifepowers.app.domain.SkySettings
import com.lifepowers.app.domain.WorldRepository
import com.lifepowers.app.notify.Notices
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Scheduling for the earth and sky refresh and the evening sky notice. */
object WorldWork {
    const val UNIQUE_REFRESH = "world_refresh"
    const val UNIQUE_REFRESH_NOW = "world_refresh_now"
    const val UNIQUE_EVENING = "sky_evening"

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Call once at app start. Keeps work that is already scheduled. */
    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        val refresh = PeriodicWorkRequestBuilder<WorldRefreshWorker>(3, TimeUnit.HOURS)
            .setConstraints(network)
            .build()
        wm.enqueueUniquePeriodicWork(UNIQUE_REFRESH, ExistingPeriodicWorkPolicy.KEEP, refresh)
        wm.enqueueUniqueWork(UNIQUE_EVENING, ExistingWorkPolicy.KEEP, eveningRequest(SkySettings().eveningHour, marginMin = 1))
    }

    /** Moves the evening notice to [hour] local. Used when sky settings change. */
    fun rescheduleEvening(context: Context, hour: Int) {
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_EVENING, ExistingWorkPolicy.REPLACE, eveningRequest(hour, marginMin = 1))
    }

    /**
     * Queues tomorrow's evening notice from inside the running evening worker. REPLACE would cancel
     * the worker that calls it, so the next request is appended to the finishing one instead.
     */
    internal fun scheduleNextEvening(context: Context, hour: Int) {
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_EVENING, ExistingWorkPolicy.APPEND_OR_REPLACE, eveningRequest(hour, marginMin = 60))
    }

    /** One refresh as soon as the network allows, such as after the location changes. */
    fun refreshNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<WorldRefreshWorker>().setConstraints(network).build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_REFRESH_NOW, ExistingWorkPolicy.REPLACE, request)
    }

    private fun eveningRequest(hour: Int, marginMin: Long): OneTimeWorkRequest {
        val delay = WorldMath.delayToNextEvening(ZonedDateTime.now(), hour, marginMin).toMillis()
        return OneTimeWorkRequestBuilder<SkyEveningWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
    }
}

/** Every three hours on any network: device location when stale, then the full world refresh. */
@HiltWorker
class WorldRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val world: WorldRepositoryImpl,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        world.refreshDeviceLocationIfStale()
        world.refresh()
        return Result.success()
    }
}

/** Posts the evening sky notice when there is something worth saying, then queues the next evening. */
@HiltWorker
class SkyEveningWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val world: WorldRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = world.skySettings.first()
        try {
            if (settings.notifySky) {
                val zone = ZoneId.systemDefault()
                val today = LocalDate.now(zone)
                val text = EveningNotice.compose(world.skyToday(today).first(), world.skyToday(today.plusDays(1)).first(), zone)
                if (text != null) {
                    Notices.post(applicationContext, Notices.CHANNEL_SKY, Notices.Ids.SKY_EVENING, EveningNotice.TITLE, text, route = "sky")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("World", "evening notice failed", e)
        }
        WorldWork.scheduleNextEvening(applicationContext, settings.eveningHour)
        return Result.success()
    }
}
