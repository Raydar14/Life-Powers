package com.lifepowers.app.data.personal

import com.lifepowers.app.data.Dates
import com.lifepowers.app.domain.Goal
import com.lifepowers.app.domain.HabitCheck
import com.lifepowers.app.domain.JournalEntry
import com.lifepowers.app.domain.Photo
import com.lifepowers.app.domain.TarotPull
import com.lifepowers.app.domain.TimerSession
import com.lifepowers.core.Cadence
import com.lifepowers.core.Comparison
import com.lifepowers.core.TargetKind
import java.time.LocalDate

/* Row and domain conversions for the personal database. Enums are stored by name. */

private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
    name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: fallback

private fun date(iso: String): LocalDate = Dates.parseIso(iso) ?: LocalDate.ofEpochDay(0)

fun GoalEntity.toDomain() = Goal(
    id = id,
    name = name,
    cadence = enumOr(cadence, Cadence.DAILY),
    kind = enumOr(kind, TargetKind.CHECK),
    target = target,
    metric = metric,
    comparison = enumOr(comparison, Comparison.AT_LEAST),
    threshold = threshold,
    autoSource = autoSource,
    isHabit = isHabit,
    reminderTimes = PersonalCodec.decodeTimes(reminderTimes),
    reminderDays = PersonalCodec.decodeDays(reminderDays),
    closingNotice = closingNotice,
    archived = archived,
    createdUtcMs = createdUtc,
)

fun Goal.toEntity() = GoalEntity(
    id = id,
    name = name.trim(),
    cadence = cadence.name,
    kind = kind.name,
    target = target,
    metric = metric?.takeIf { it.isNotBlank() },
    comparison = comparison.name,
    threshold = threshold,
    autoSource = autoSource?.takeIf { it.isNotBlank() },
    isHabit = isHabit,
    reminderTimes = PersonalCodec.encodeTimes(reminderTimes),
    reminderDays = PersonalCodec.encodeDays(reminderDays),
    closingNotice = closingNotice,
    archived = archived,
    createdUtc = createdUtcMs,
)

fun HabitCheckEntity.toDomain() = HabitCheck(
    id = id,
    goalId = goalId,
    localDate = date(localDate),
    amount = amount,
    source = source,
    note = note,
    createdUtcMs = createdUtc,
)

fun HabitCheck.toEntity() = HabitCheckEntity(
    id = id,
    goalId = goalId,
    localDate = Dates.iso(localDate),
    amount = amount,
    source = source,
    note = note,
    createdUtc = createdUtcMs,
)

fun TimerSessionEntity.toDomain() = TimerSession(
    id = id,
    goalId = goalId,
    startUtcMs = startUtc,
    endUtcMs = endUtc,
    plannedMin = plannedMin,
    zoneOffsetMin = zoneOffsetMin,
)

fun PhotoEntity.toDomain() = Photo(
    id = id,
    entryId = entryId,
    fileName = fileName,
    mime = mime,
    caption = caption,
    createdUtcMs = createdUtc,
)

fun TarotPullEntity.toDomain() = TarotPull(
    id = id,
    entryId = entryId,
    localDate = date(localDate),
    deck = deck,
    spread = spread,
    position = position,
    card = card,
    reversed = reversed,
    note = note,
    interpretation = interpretation,
    interpretationFromAssistant = fromAssistant,
    createdUtcMs = createdUtc,
)

fun TarotPull.toEntity(eventId: Long?) = TarotPullEntity(
    id = id,
    entryId = entryId,
    localDate = Dates.iso(localDate),
    deck = deck,
    spread = spread,
    position = position,
    card = card.trim(),
    reversed = reversed,
    note = note,
    interpretation = interpretation,
    fromAssistant = interpretationFromAssistant,
    createdUtc = createdUtcMs,
    eventId = eventId,
)

fun JournalEntryEntity.toDomain(photos: List<Photo>, pulls: List<TarotPull>) = JournalEntry(
    id = id,
    localDate = date(localDate),
    createdUtcMs = createdUtc,
    zoneOffsetMin = zoneOffsetMin,
    text = text,
    voiceTranscript = voiceTranscript,
    tags = PersonalCodec.decodeTags(tags),
    scoreWords = scoreWords,
    photos = photos,
    pulls = pulls,
)

fun JournalEntry.toEntity(eventId: Long?) = JournalEntryEntity(
    id = id,
    localDate = Dates.iso(localDate),
    createdUtc = createdUtcMs,
    zoneOffsetMin = zoneOffsetMin,
    text = text,
    voiceTranscript = voiceTranscript,
    tags = PersonalCodec.encodeTags(tags),
    scoreWords = scoreWords,
    eventId = eventId,
)
