package com.lifepowers.app.data.personal

import com.lifepowers.app.data.Dates
import com.lifepowers.app.domain.BehaviorEvent
import com.lifepowers.app.domain.EventKinds
import com.lifepowers.app.domain.EventRepository
import com.lifepowers.app.domain.JournalEntry
import com.lifepowers.app.domain.JournalRepository
import com.lifepowers.app.domain.MetricRepository
import com.lifepowers.app.domain.Photo
import com.lifepowers.app.domain.TarotPull
import com.lifepowers.app.domain.nowZoneOffsetMin
import com.lifepowers.core.Lexicon
import com.lifepowers.core.Metrics
import com.lifepowers.core.Sample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Journal entries with their photos and tarot pulls. Each entry and each pull keeps exactly one
 * moment on the timeline in the main database; the row remembers that event's id so a re-save
 * replaces it rather than adding another.
 */
@Singleton
class JournalRepositoryImpl @Inject constructor(
    private val dao: JournalDao,
    private val vault: PhotoVault,
    private val events: EventRepository,
    private val metrics: MetricRepository,
) : JournalRepository {

    override fun entries(from: LocalDate, to: LocalDate): Flow<List<JournalEntry>> =
        combine(dao.entries(Dates.iso(from), Dates.iso(to)), dao.allPhotos(), dao.allPulls()) { rows, photos, pulls ->
            val photosBy = photos.groupBy { it.entryId }
            val pullsBy = pulls.filter { it.entryId != null }.groupBy { it.entryId }
            rows.map { row ->
                row.toDomain(
                    photosBy[row.id].orEmpty().map { it.toDomain() },
                    pullsBy[row.id].orEmpty().map { it.toDomain() },
                )
            }
        }

    override fun entry(id: Long): Flow<JournalEntry?> =
        combine(dao.entry(id), dao.allPhotos(), dao.allPulls()) { row, photos, pulls ->
            if (row == null) {
                null
            } else {
                row.toDomain(
                    photos.filter { it.entryId == row.id }.map { it.toDomain() },
                    pulls.filter { it.entryId == row.id }.map { it.toDomain() },
                )
            }
        }

    override suspend fun saveEntry(entry: JournalEntry): Long {
        val existing = if (entry.id != 0L) dao.entryOnce(entry.id) else null
        existing?.eventId?.let { events.delete(it) }
        val at = momentFor(entry.localDate, entry.createdUtcMs, entry.zoneOffsetMin)
        val noteSource = entry.text.ifBlank { entry.voiceTranscript.orEmpty() }.trim()
        val eventId = events.add(
            BehaviorEvent(
                kind = EventKinds.JOURNAL,
                startUtcMs = at,
                zoneOffsetMin = entry.zoneOffsetMin,
                note = noteSource.take(NOTE_CHARS).ifBlank { null },
                source = SOURCE,
            ),
        )
        val id = dao.upsertEntry(entry.toEntity(eventId))

        existing?.let { old ->
            val oldDate = LocalDate.parse(old.localDate)
            metrics.deleteSamples(SOURCE, momentFor(oldDate, old.createdUtc, old.zoneOffsetMin))
            if (!entry.scoreWords || entry.text.isBlank()) metrics.rebuildDaily(oldDate, oldDate)
        }
        if (entry.scoreWords && entry.text.isNotBlank()) {
            val w = Lexicon.score(entry.text)
            val samples = buildList {
                add(Sample(Metrics.WORDS_VALENCE, w.valence, at, null, entry.zoneOffsetMin, SOURCE))
                add(Sample(Metrics.WORDS_OPTIMISM, w.optimism, at, null, entry.zoneOffsetMin, SOURCE))
                add(Sample(Metrics.WORDS_ANXIETY, w.anxietyMarkers, at, null, entry.zoneOffsetMin, SOURCE))
                add(Sample(Metrics.WORDS_SELF_FOCUS, w.selfFocus, at, null, entry.zoneOffsetMin, SOURCE))
                w.energySelfReport?.let { add(Sample(Metrics.WORDS_ENERGY_SELF_REPORT, it, at, null, entry.zoneOffsetMin, SOURCE)) }
            }.filter { !it.value.isNaN() }
            metrics.upsertSamples(samples)
            metrics.rebuildDaily(entry.localDate, entry.localDate)
        }
        return id
    }

    override suspend fun deleteEntry(id: Long) {
        val row = dao.entryOnce(id) ?: return
        dao.photosFor(id).forEach { p ->
            vault.delete(p.fileName)
            dao.deletePhoto(p.id)
        }
        dao.pullsFor(id).forEach { p ->
            p.eventId?.let { events.delete(it) }
            dao.deletePull(p.id)
        }
        row.eventId?.let { events.delete(it) }
        val date = LocalDate.parse(row.localDate)
        metrics.deleteSamples(SOURCE, momentFor(date, row.createdUtc, row.zoneOffsetMin))
        metrics.rebuildDaily(date, date)
        dao.deleteEntry(id)
    }

    override suspend fun addPhoto(entryId: Long, bytes: ByteArray, mime: String, caption: String?): Photo {
        val stored = vault.store(bytes, mime)
        val entity = PhotoEntity(
            entryId = entryId,
            fileName = stored.fileName,
            mime = stored.mime,
            caption = caption?.takeIf { it.isNotBlank() },
            createdUtc = System.currentTimeMillis(),
        )
        return entity.copy(id = dao.insertPhoto(entity)).toDomain()
    }

    override suspend fun loadPhoto(photo: Photo): ByteArray? = vault.load(photo.fileName)

    override suspend fun deletePhoto(id: Long) {
        val row = dao.photoOnce(id) ?: return
        vault.delete(row.fileName)
        dao.deletePhoto(id)
    }

    override suspend fun savePull(pull: TarotPull): Long {
        val existing = if (pull.id != 0L) dao.pullOnce(pull.id) else null
        existing?.eventId?.let { events.delete(it) }
        val zone = nowZoneOffsetMin()
        val card = pull.card.trim()
        val eventId = events.add(
            BehaviorEvent(
                kind = EventKinds.TAROT_PULL,
                startUtcMs = momentFor(pull.localDate, pull.createdUtcMs, zone),
                zoneOffsetMin = zone,
                note = if (pull.reversed) "$card reversed" else card,
                source = SOURCE,
            ),
        )
        return dao.upsertPull(pull.toEntity(eventId))
    }

    override suspend fun deletePull(id: Long) {
        val row = dao.pullOnce(id) ?: return
        row.eventId?.let { events.delete(it) }
        dao.deletePull(id)
    }

    override fun pulls(from: LocalDate, to: LocalDate): Flow<List<TarotPull>> =
        dao.pulls(Dates.iso(from), Dates.iso(to)).map { rows -> rows.map { it.toDomain() } }

    /**
     * The creation time when it falls on [date]; otherwise noon of [date], so an entry written for an
     * earlier day sits on that day's timeline.
     */
    private fun momentFor(date: LocalDate, createdUtcMs: Long, zoneOffsetMin: Int): Long =
        if (Dates.localDate(createdUtcMs, zoneOffsetMin) == date) createdUtcMs
        else Dates.startOfDayMs(date, zoneOffsetMin) + NOON_MS

    private companion object {
        const val SOURCE = "journal"
        const val NOTE_CHARS = 80
        const val NOON_MS = 12 * 60 * 60 * 1000L
    }
}
