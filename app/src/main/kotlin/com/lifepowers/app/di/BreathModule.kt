package com.lifepowers.app.di

import com.lifepowers.app.breath.BreathRepositoryImpl
import com.lifepowers.app.breath.BreathSessionControllerImpl
import com.lifepowers.app.breath.HeartLinkImpl
import com.lifepowers.app.domain.BreathRepository
import com.lifepowers.app.domain.BreathSessionController
import com.lifepowers.app.domain.HeartLink
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Breathing: saved patterns and sessions, the Bluetooth heart rate link, and the running session. */
@Module
@InstallIn(SingletonComponent::class)
abstract class BreathModule {
    @Binds abstract fun breathRepository(impl: BreathRepositoryImpl): BreathRepository
    @Binds abstract fun heartLink(impl: HeartLinkImpl): HeartLink
    @Binds abstract fun breathSessionController(impl: BreathSessionControllerImpl): BreathSessionController
}
