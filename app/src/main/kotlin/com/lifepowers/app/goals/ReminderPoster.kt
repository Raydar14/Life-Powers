package com.lifepowers.app.goals

import android.content.Context
import com.lifepowers.app.domain.GoalRepository
import com.lifepowers.app.notify.Notices
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** What a reminder or closing alarm posts when it fires, and the re-arm that follows. */
@Singleton
class ReminderPoster @Inject constructor(
    @ApplicationContext private val context: Context,
    private val goals: GoalRepository,
    private val scheduler: ReminderSchedulerImpl,
) {

    /** Posts a gentle progress line for [goalId] unless its current period is already met. */
    suspend fun onReminder(goalId: Long, firedAtUtcMs: Long) {
        try {
            val view = goals.view(goalId, LocalDate.now()).first()
            if (view != null && !view.goal.archived) {
                GoalMath.reminderLine(view.goal, view.current)?.let { line ->
                    Notices.post(context, Notices.CHANNEL_REMINDERS, Notices.Ids.reminder(goalId), view.goal.name, line, route = ROUTE)
                }
            }
        } finally {
            scheduler.rescheduleAfter(maxOf(System.currentTimeMillis(), firedAtUtcMs + ONE_MINUTE_MS))
        }
    }

    /** On the last day of a week, month, or year that is not yet met, says so once, kindly. */
    suspend fun onClosing(firedAtUtcMs: Long) {
        try {
            val today = LocalDate.now()
            goals.views(today).first()
                .filter { it.goal.closingNotice }
                .forEach { v ->
                    val line = v.closingLine ?: return@forEach
                    Notices.post(context, Notices.CHANNEL_REMINDERS, Notices.Ids.closing(v.goal.id), v.goal.name, line, route = ROUTE)
                }
        } finally {
            scheduler.rescheduleAfter(maxOf(System.currentTimeMillis(), firedAtUtcMs + ONE_MINUTE_MS))
        }
    }

    private companion object {
        const val ROUTE = "goals"
        const val ONE_MINUTE_MS = 60_000L
    }
}
