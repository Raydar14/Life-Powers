package com.lifepowers.app.di

import com.lifepowers.app.connectors.HealthConnectConnector
import com.lifepowers.app.connectors.OuraConnector
import com.lifepowers.app.connectors.UsageStatsConnector
import com.lifepowers.app.domain.Connector
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** Every connector is contributed to one Set<Connector>, which the sync worker iterates. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ConnectorModule {
    @Binds @IntoSet abstract fun healthConnect(impl: HealthConnectConnector): Connector
    @Binds @IntoSet abstract fun oura(impl: OuraConnector): Connector
    @Binds @IntoSet abstract fun usageStats(impl: UsageStatsConnector): Connector
}
