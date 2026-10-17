package com.lifepowers.app.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        MetricSampleEntity::class,
        DailyMetricEntity::class,
        BehaviorEventEntity::class,
        UserProfileEntity::class,
        VoiceCheckinEntity::class,
        ForecastEntity::class,
        SyncCursorEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class LifePowersDatabase : RoomDatabase() {
    abstract fun metricSampleDao(): MetricSampleDao
    abstract fun dailyMetricDao(): DailyMetricDao
    abstract fun behaviorEventDao(): BehaviorEventDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun voiceCheckinDao(): VoiceCheckinDao
    abstract fun forecastDao(): ForecastDao
    abstract fun syncCursorDao(): SyncCursorDao

    companion object {
        const val NAME = "life_powers.db"
    }
}
