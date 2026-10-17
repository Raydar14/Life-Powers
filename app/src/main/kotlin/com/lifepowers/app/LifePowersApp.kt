package com.lifepowers.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.lifepowers.app.domain.ReminderScheduler
import com.lifepowers.app.domain.SyncManager
import com.lifepowers.app.experiments.ExperimentWork
import com.lifepowers.app.notify.Notices
import com.lifepowers.app.patterns.PersonalWork
import com.lifepowers.app.world.WorldWork
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * WorkManager is initialized on demand (the manifest removes the default initializer) so the
 * worker factory can come from Hilt.
 */
@HiltAndroidApp
class LifePowersApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncManager: SyncManager
    @Inject lateinit var reminderScheduler: ReminderScheduler

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        Notices.ensureChannels(this)
        syncManager.schedulePeriodic()
        PersonalWork.schedule(this)
        WorldWork.schedule(this)
        ExperimentWork.schedule(this)
        // Alarms are lost on force stop, so re-arm on every start. Idempotent.
        appScope.launch { runCatching { reminderScheduler.rescheduleAll() } }
    }
}
