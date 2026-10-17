package com.lifepowers.app.di

import com.lifepowers.app.domain.DataVault
import com.lifepowers.app.vault.DataVaultImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VaultModule {
    @Binds
    @Singleton
    abstract fun dataVault(impl: DataVaultImpl): DataVault
}
