package com.lifepowers.app.data.personal

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/*
 * Phase 2 tables live in their own encrypted database, so the Phase 1 database needs no migration and
 * nobody's synced history is touched. Same SQLCipher passphrase, separate file.
 * Lists are stored as comma separated text: reminder times as HH:mm, days as ISO day numbers 1 to 7,
 * tags as plain words. Dates are ISO local dates.
 */

@Entity(tableName = "goal")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val cadence: String,
    val kind: String,
    val target: Double,
    val metric: String?,
    val comparison: String,
    val threshold: Double?,
    @ColumnInfo(name = "auto_source") val autoSource: String?,
    @ColumnInfo(name = "is_habit") val isHabit: Boolean,
    @ColumnInfo(name = "reminder_times") val reminderTimes: String,
    @ColumnInfo(name = "reminder_days") val reminderDays: String,
    @ColumnInfo(name = "closing_notice") val closingNotice: Boolean,
    val archived: Boolean,
    @ColumnInfo(name = "created_utc") val createdUtc: Long,
)

@Entity(tableName = "habit_check", indices = [Index(value = ["goal_id", "local_date"])])
data class HabitCheckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "goal_id") val goalId: Long,
    @ColumnInfo(name = "local_date") val localDate: String,
    val amount: Double,
    val source: String,
    val note: String?,
    @ColumnInfo(name = "created_utc") val createdUtc: Long,
)

@Entity(tableName = "timer_session", indices = [Index(value = ["start_utc"])])
data class TimerSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "goal_id") val goalId: Long?,
    @ColumnInfo(name = "start_utc") val startUtc: Long,
    @ColumnInfo(name = "end_utc") val endUtc: Long?,
    @ColumnInfo(name = "planned_min") val plannedMin: Int?,
    @ColumnInfo(name = "zone_offset_min") val zoneOffsetMin: Int,
)

@Entity(tableName = "journal_entry", indices = [Index(value = ["local_date"])])
data class JournalEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "local_date") val localDate: String,
    @ColumnInfo(name = "created_utc") val createdUtc: Long,
    @ColumnInfo(name = "zone_offset_min") val zoneOffsetMin: Int,
    val text: String,
    @ColumnInfo(name = "voice_transcript") val voiceTranscript: String?,
    val tags: String,
    @ColumnInfo(name = "score_words") val scoreWords: Boolean,
    /** The behavior_event row in the main database that marks this entry on the timeline. */
    @ColumnInfo(name = "event_id") val eventId: Long?,
)

@Entity(tableName = "photo", indices = [Index(value = ["entry_id"])])
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "entry_id") val entryId: Long,
    @ColumnInfo(name = "file_name") val fileName: String,
    val mime: String,
    val caption: String?,
    @ColumnInfo(name = "created_utc") val createdUtc: Long,
)

@Entity(tableName = "tarot_pull", indices = [Index(value = ["local_date"]), Index(value = ["entry_id"])])
data class TarotPullEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "entry_id") val entryId: Long?,
    @ColumnInfo(name = "local_date") val localDate: String,
    val deck: String?,
    val spread: String?,
    val position: String?,
    val card: String,
    val reversed: Boolean,
    val note: String?,
    val interpretation: String?,
    @ColumnInfo(name = "from_assistant") val fromAssistant: Boolean,
    @ColumnInfo(name = "created_utc") val createdUtc: Long,
    @ColumnInfo(name = "event_id") val eventId: Long?,
)

@Entity(tableName = "world_event", indices = [Index(value = ["utc"])])
data class WorldEventEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val title: String,
    val utc: Long,
    val magnitude: Double?,
    @ColumnInfo(name = "distance_km") val distanceKm: Double?,
    @ColumnInfo(name = "ring_of_fire") val ringOfFire: Boolean,
    val link: String?,
    val notified: Boolean,
)

@Dao
interface GoalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(goal: GoalEntity): Long

    @Query("SELECT * FROM goal WHERE archived = 0 ORDER BY created_utc DESC")
    fun active(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goal WHERE archived = 0 ORDER BY created_utc DESC")
    suspend fun activeOnce(): List<GoalEntity>

    @Query("SELECT * FROM goal WHERE id = :id")
    fun byId(id: Long): Flow<GoalEntity?>

    @Query("SELECT * FROM goal WHERE id = :id")
    suspend fun byIdOnce(id: Long): GoalEntity?

    @Query("UPDATE goal SET archived = 1 WHERE id = :id")
    suspend fun archive(id: Long)

    @Query("DELETE FROM goal WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface HabitCheckDao {
    @Insert
    suspend fun insert(check: HabitCheckEntity): Long

    @Query("DELETE FROM habit_check WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM habit_check WHERE goal_id = :goalId")
    suspend fun deleteForGoal(goalId: Long)

    @Query("DELETE FROM habit_check WHERE goal_id = :goalId AND local_date = :date AND source = 'auto'")
    suspend fun deleteAuto(goalId: Long, date: String)

    @Query("SELECT * FROM habit_check WHERE local_date BETWEEN :from AND :to ORDER BY local_date, created_utc")
    fun between(from: String, to: String): Flow<List<HabitCheckEntity>>

    @Query("SELECT * FROM habit_check WHERE local_date BETWEEN :from AND :to ORDER BY local_date, created_utc")
    suspend fun betweenOnce(from: String, to: String): List<HabitCheckEntity>

    @Query("SELECT * FROM habit_check WHERE goal_id = :goalId AND local_date BETWEEN :from AND :to ORDER BY local_date, created_utc")
    fun forGoal(goalId: Long, from: String, to: String): Flow<List<HabitCheckEntity>>
}

@Dao
interface TimerSessionDao {
    @Insert
    suspend fun insert(session: TimerSessionEntity): Long

    @Update
    suspend fun update(session: TimerSessionEntity)

    @Query("DELETE FROM timer_session WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM timer_session WHERE end_utc IS NULL ORDER BY start_utc DESC LIMIT 1")
    suspend fun running(): TimerSessionEntity?

    @Query("SELECT * FROM timer_session WHERE start_utc BETWEEN :fromUtc AND :toUtc ORDER BY start_utc")
    fun between(fromUtc: Long, toUtc: Long): Flow<List<TimerSessionEntity>>
}

@Dao
interface JournalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntry(entry: JournalEntryEntity): Long

    @Query("SELECT * FROM journal_entry WHERE id = :id")
    suspend fun entryOnce(id: Long): JournalEntryEntity?

    @Query("SELECT * FROM journal_entry WHERE id = :id")
    fun entry(id: Long): Flow<JournalEntryEntity?>

    @Query("SELECT * FROM journal_entry WHERE local_date BETWEEN :from AND :to ORDER BY local_date DESC, created_utc DESC")
    fun entries(from: String, to: String): Flow<List<JournalEntryEntity>>

    @Query("SELECT COUNT(*) FROM journal_entry WHERE local_date = :date")
    suspend fun countOn(date: String): Int

    @Query("DELETE FROM journal_entry WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Insert
    suspend fun insertPhoto(photo: PhotoEntity): Long

    @Query("SELECT * FROM photo WHERE id = :id")
    suspend fun photoOnce(id: Long): PhotoEntity?

    @Query("SELECT * FROM photo WHERE entry_id = :entryId ORDER BY created_utc")
    suspend fun photosFor(entryId: Long): List<PhotoEntity>

    @Query("SELECT * FROM photo ORDER BY created_utc")
    fun allPhotos(): Flow<List<PhotoEntity>>

    @Query("DELETE FROM photo WHERE id = :id")
    suspend fun deletePhoto(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPull(pull: TarotPullEntity): Long

    @Query("SELECT * FROM tarot_pull WHERE id = :id")
    suspend fun pullOnce(id: Long): TarotPullEntity?

    @Query("SELECT * FROM tarot_pull WHERE local_date BETWEEN :from AND :to ORDER BY local_date DESC, created_utc DESC")
    fun pulls(from: String, to: String): Flow<List<TarotPullEntity>>

    @Query("SELECT * FROM tarot_pull WHERE entry_id = :entryId ORDER BY created_utc")
    suspend fun pullsFor(entryId: Long): List<TarotPullEntity>

    @Query("SELECT * FROM tarot_pull ORDER BY created_utc")
    fun allPulls(): Flow<List<TarotPullEntity>>

    @Query("DELETE FROM tarot_pull WHERE id = :id")
    suspend fun deletePull(id: Long)
}

@Dao
interface WorldEventDao {
    /** Keeps the notified flag of rows already stored. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNew(events: List<WorldEventEntity>): List<Long>

    @Query("SELECT * FROM world_event WHERE utc >= :sinceUtc ORDER BY utc DESC")
    fun since(sinceUtc: Long): Flow<List<WorldEventEntity>>

    @Query("SELECT * FROM world_event WHERE notified = 0 ORDER BY utc DESC")
    suspend fun unnotified(): List<WorldEventEntity>

    @Query("UPDATE world_event SET notified = 1 WHERE id = :id")
    suspend fun markNotified(id: String)

    @Query("DELETE FROM world_event WHERE utc < :beforeUtc")
    suspend fun prune(beforeUtc: Long)
}

@Database(
    entities = [
        GoalEntity::class,
        HabitCheckEntity::class,
        TimerSessionEntity::class,
        JournalEntryEntity::class,
        PhotoEntity::class,
        TarotPullEntity::class,
        WorldEventEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class PersonalDatabase : RoomDatabase() {
    abstract fun goalDao(): GoalDao
    abstract fun habitCheckDao(): HabitCheckDao
    abstract fun timerSessionDao(): TimerSessionDao
    abstract fun journalDao(): JournalDao
    abstract fun worldEventDao(): WorldEventDao

    companion object {
        const val NAME = "life_powers_personal.db"
    }
}
