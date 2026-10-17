package com.lifepowers.app.patterns

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.lifepowers.app.goals.AutoCheckWorker
import java.util.concurrent.TimeUnit

/** Background jobs for Phase 2: daily pattern findings and automatic habit checks every six hours. */
object PersonalWork {

    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        val patterns = PeriodicWorkRequestBuilder<PatternWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        wm.enqueueUniquePeriodicWork(PatternWorker.UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, patterns)

        val autoCheck = PeriodicWorkRequestBuilder<AutoCheckWorker>(6, TimeUnit.HOURS).build()
        wm.enqueueUniquePeriodicWork(AutoCheckWorker.UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, autoCheck)
    }
}
