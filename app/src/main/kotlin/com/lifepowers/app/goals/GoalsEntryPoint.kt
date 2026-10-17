package com.lifepowers.app.goals

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Lets the broadcast receivers reach singletons without field injection. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface GoalsEntryPoint {
    fun reminderScheduler(): ReminderSchedulerImpl
    fun reminderPoster(): ReminderPoster
    fun timerController(): TimerControllerImpl
}

internal fun goalsEntryPoint(context: Context): GoalsEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, GoalsEntryPoint::class.java)
