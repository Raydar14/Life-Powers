package com.lifepowers.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MetricSampleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(samples: List<MetricSampleEntity>)

    @Query(
        "SELECT * FROM metric_sample WHERE is_canonical = 1 AND start_utc BETWEEN :fromUtc AND :toUtc ORDER BY start_utc",
    )
    suspend fun canonicalBetween(fromUtc: Long, toUtc: Long): List<MetricSampleEntity>

    @Query(
        "SELECT * FROM metric_sample WHERE metric = :metric AND is_canonical = 1 AND start_utc BETWEEN :fromUtc AND :toUtc ORDER BY start_utc",
    )
    suspend fun forMetricBetween(metric: String, fromUtc: Long, toUtc: Long): List<MetricSampleEntity>

    @Query("DELETE FROM metric_sample WHERE source = :source AND start_utc = :startUtc")
    suspend fun deleteAt(source: String, startUtc: Long)
}

@Dao
interface DailyMetricDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<DailyMetricEntity>)

    @Query("DELETE FROM daily_metric WHERE local_date BETWEEN :fromDate AND :toDate")
    suspend fun deleteBetween(fromDate: String, toDate: String)

    @Query("SELECT * FROM daily_metric WHERE local_date BETWEEN :fromDate AND :toDate ORDER BY local_date, metric, agg")
    fun between(fromDate: String, toDate: String): Flow<List<DailyMetricEntity>>

    @Query(
        "SELECT * FROM daily_metric WHERE metric = :metric AND agg = :agg AND local_date BETWEEN :fromDate AND :toDate ORDER BY local_date",
    )
    suspend fun series(metric: String, agg: String, fromDate: String, toDate: String): List<DailyMetricEntity>

    @Query("SELECT * FROM daily_metric WHERE local_date = :date")
    suspend fun forDay(date: String): List<DailyMetricEntity>
}

@Dao
interface BehaviorEventDao {
    @Insert
    suspend fun insert(event: BehaviorEventEntity): Long

    @Query("DELETE FROM behavior_event WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM behavior_event WHERE start_utc BETWEEN :fromUtc AND :toUtc ORDER BY start_utc")
    fun between(fromUtc: Long, toUtc: Long): Flow<List<BehaviorEventEntity>>

    @Query("SELECT COUNT(*) FROM behavior_event WHERE kind = :kind")
    suspend fun countOfKind(kind: String): Int

    @Query("SELECT COUNT(*) FROM behavior_event WHERE kind = :kind AND start_utc = :startUtc")
    suspend fun countAt(kind: String, startUtc: Long): Int

    @Query("SELECT * FROM behavior_event WHERE kind = :kind AND start_utc <= :beforeUtc ORDER BY start_utc DESC LIMIT 1")
    suspend fun latestOfKind(kind: String, beforeUtc: Long): BehaviorEventEntity?
}

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun observe(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun get(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: UserProfileEntity)
}

@Dao
interface VoiceCheckinDao {
    @Insert
    suspend fun insert(row: VoiceCheckinEntity): Long

    @Query("SELECT * FROM voice_checkin ORDER BY start_utc DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<VoiceCheckinEntity>>

    @Query("SELECT COUNT(*) FROM voice_checkin WHERE quality_ok = 1")
    suspend fun countQualityOk(): Int
}

@Dao
interface ForecastDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<ForecastEntity>)

    @Query("DELETE FROM forecast WHERE local_date = :date")
    suspend fun deleteForDay(date: String)

    @Query("SELECT * FROM forecast WHERE local_date = :date ORDER BY component")
    fun forDay(date: String): Flow<List<ForecastEntity>>

    @Query("SELECT * FROM forecast WHERE local_date = :date AND component = :component")
    suspend fun get(date: String, component: String): ForecastEntity?

    @Query("SELECT * FROM forecast WHERE component = :component AND local_date BETWEEN :fromDate AND :toDate ORDER BY local_date")
    fun componentBetween(component: String, fromDate: String, toDate: String): Flow<List<ForecastEntity>>
}

@Dao
interface SyncCursorDao {
    @Query("SELECT * FROM sync_cursor ORDER BY source")
    fun observeAll(): Flow<List<SyncCursorEntity>>

    @Query("SELECT * FROM sync_cursor WHERE source = :source")
    suspend fun get(source: String): SyncCursorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: SyncCursorEntity)

    @Delete
    suspend fun delete(row: SyncCursorEntity)
}
