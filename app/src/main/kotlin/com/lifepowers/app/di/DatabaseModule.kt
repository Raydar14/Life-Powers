package com.lifepowers.app.di

import android.content.Context
import androidx.room.Room
import com.lifepowers.app.data.BehaviorEventDao
import com.lifepowers.app.data.DailyMetricDao
import com.lifepowers.app.data.DatabasePassphrase
import com.lifepowers.app.data.ForecastDao
import com.lifepowers.app.data.LifePowersDatabase
import com.lifepowers.app.data.MetricSampleDao
import com.lifepowers.app.data.SyncCursorDao
import com.lifepowers.app.data.UserProfileDao
import com.lifepowers.app.data.VoiceCheckinDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): LifePowersDatabase {
        System.loadLibrary("sqlcipher")
        val passphrase = DatabasePassphrase.getOrCreate(context)
        return Room.databaseBuilder(context, LifePowersDatabase::class.java, LifePowersDatabase.NAME)
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides fun metricSampleDao(db: LifePowersDatabase): MetricSampleDao = db.metricSampleDao()
    @Provides fun dailyMetricDao(db: LifePowersDatabase): DailyMetricDao = db.dailyMetricDao()
    @Provides fun behaviorEventDao(db: LifePowersDatabase): BehaviorEventDao = db.behaviorEventDao()
    @Provides fun userProfileDao(db: LifePowersDatabase): UserProfileDao = db.userProfileDao()
    @Provides fun voiceCheckinDao(db: LifePowersDatabase): VoiceCheckinDao = db.voiceCheckinDao()
    @Provides fun forecastDao(db: LifePowersDatabase): ForecastDao = db.forecastDao()
    @Provides fun syncCursorDao(db: LifePowersDatabase): SyncCursorDao = db.syncCursorDao()
}
