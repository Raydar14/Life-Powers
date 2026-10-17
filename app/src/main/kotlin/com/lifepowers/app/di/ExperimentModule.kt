package com.lifepowers.app.di

import com.lifepowers.app.domain.ExperimentRepository
import com.lifepowers.app.experiments.ExperimentRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Self experiments. The DAO itself comes from PersonalDatabaseModule. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ExperimentModule {
    @Binds abstract fun experimentRepository(impl: ExperimentRepositoryImpl): ExperimentRepository
}
