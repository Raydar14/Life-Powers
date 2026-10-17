package com.lifepowers.app.di

import com.lifepowers.app.data.personal.JournalRepositoryImpl
import com.lifepowers.app.domain.GoalRepository
import com.lifepowers.app.domain.JournalRepository
import com.lifepowers.app.domain.PatternRepository
import com.lifepowers.app.domain.ReminderScheduler
import com.lifepowers.app.domain.TimerController
import com.lifepowers.app.goals.GoalRepositoryImpl
import com.lifepowers.app.goals.ReminderSchedulerImpl
import com.lifepowers.app.goals.TimerControllerImpl
import com.lifepowers.app.patterns.PatternRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Goals, the timer, reminders, the journal, and patterns. */
@Module
@InstallIn(SingletonComponent::class)
abstract class PersonalModule {
    @Binds abstract fun goalRepository(impl: GoalRepositoryImpl): GoalRepository
    @Binds abstract fun timerController(impl: TimerControllerImpl): TimerController
    @Binds abstract fun reminderScheduler(impl: ReminderSchedulerImpl): ReminderScheduler
    @Binds abstract fun journalRepository(impl: JournalRepositoryImpl): JournalRepository
    @Binds abstract fun patternRepository(impl: PatternRepositoryImpl): PatternRepository
}
