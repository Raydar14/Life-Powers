package com.lifepowers.app.di

import com.lifepowers.app.data.CheckinRepositoryImpl
import com.lifepowers.app.data.EventRepositoryImpl
import com.lifepowers.app.data.ForecastRepositoryImpl
import com.lifepowers.app.data.MetricRepositoryImpl
import com.lifepowers.app.data.ProfileRepositoryImpl
import com.lifepowers.app.data.SettingsRepositoryImpl
import com.lifepowers.app.domain.CheckinRepository
import com.lifepowers.app.domain.EventRepository
import com.lifepowers.app.domain.ForecastRepository
import com.lifepowers.app.domain.MetricRepository
import com.lifepowers.app.domain.ProfileRepository
import com.lifepowers.app.domain.SettingsRepository
import com.lifepowers.app.domain.SyncManager
import com.lifepowers.app.sync.SyncManagerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun metricRepository(impl: MetricRepositoryImpl): MetricRepository
    @Binds abstract fun eventRepository(impl: EventRepositoryImpl): EventRepository
    @Binds abstract fun profileRepository(impl: ProfileRepositoryImpl): ProfileRepository
    @Binds abstract fun forecastRepository(impl: ForecastRepositoryImpl): ForecastRepository
    @Binds abstract fun checkinRepository(impl: CheckinRepositoryImpl): CheckinRepository
    @Binds abstract fun settingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
    @Binds abstract fun syncManager(impl: SyncManagerImpl): SyncManager
}
