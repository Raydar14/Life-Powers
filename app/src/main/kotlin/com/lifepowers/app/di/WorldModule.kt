package com.lifepowers.app.di

import com.lifepowers.app.domain.SkyTierSource
import com.lifepowers.app.domain.WorldRepository
import com.lifepowers.app.world.SkyTierSourceImpl
import com.lifepowers.app.world.WorldRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class WorldModule {
    @Binds abstract fun worldRepository(impl: WorldRepositoryImpl): WorldRepository
    @Binds abstract fun skyTierSource(impl: SkyTierSourceImpl): SkyTierSource
}
