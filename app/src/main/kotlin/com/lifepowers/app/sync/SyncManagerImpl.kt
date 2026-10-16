package com.lifepowers.app.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.lifepowers.app.data.SyncCursorDao
import com.lifepowers.app.domain.Connector
import com.lifepowers.app.domain.SyncManager
import com.lifepowers.app.domain.SyncState
import com.lifepowers.app.domain.SyncStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cursors: SyncCursorDao,
    private val connectors: Set<@JvmSuppressWildcards Connector>,
    private val runState: SyncRunState,
) : SyncManager {

    override val status: Flow<List<SyncStatus>> =
        combine(cursors.observeAll(), runState.running) { rows, running ->
            val byId = rows.associateBy { it.source }
            connectors.sortedBy { it.id }.map { c ->
                val row = byId[c.id]
                val available = runCatching { c.isAvailable() }.getOrDefault(false)
                val state = when {
                    !available -> SyncState.NOT_CONNECTED
                    c.id in running -> SyncState.RUNNING
                    row == null -> SyncState.IDLE
                    row.lastError != null -> SyncState.ERROR
                    row.lastSuccessAt != null -> SyncState.OK
                    else -> SyncState.IDLE
                }
                SyncStatus(c.id, state, row?.lastSuccessAt, row?.lastError)
            }
        }

    override suspend fun syncNow() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(SyncWorker.UNIQUE_NOW, ExistingWorkPolicy.KEEP, request)
    }

    override fun schedulePeriodic() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(3, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(SyncWorker.UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
