package com.lifepowers.app.goals

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lifepowers.app.domain.GoalRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate

/** Writes automatic habit checks for yesterday and today from metrics, the journal, and phone use. */
@HiltWorker
class AutoCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val goals: GoalRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val today = LocalDate.now()
        runCatching { goals.autoCheck(today.minusDays(1)) }
        runCatching { goals.autoCheck(today) }
        return Result.success()
    }

    companion object {
        const val UNIQUE_PERIODIC = "auto_check_periodic"
    }
}
