package com.lifepowers.app.patterns

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lifepowers.app.domain.PatternRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Recomputes the pattern findings once a day. */
@HiltWorker
class PatternWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val patterns: PatternRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        runCatching { patterns.recompute() }
        return Result.success()
    }

    companion object {
        const val UNIQUE_PERIODIC = "patterns_periodic"
    }
}
