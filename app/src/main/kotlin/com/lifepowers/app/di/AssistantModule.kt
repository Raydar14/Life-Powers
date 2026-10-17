package com.lifepowers.app.di

import com.lifepowers.app.assistant.AssistantRepositoryImpl
import com.lifepowers.app.domain.AssistantRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Assistant features: the repository behind the master switch. The key store, gateway, and photo notes inject directly. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AssistantModule {
    @Binds abstract fun assistantRepository(impl: AssistantRepositoryImpl): AssistantRepository
}
