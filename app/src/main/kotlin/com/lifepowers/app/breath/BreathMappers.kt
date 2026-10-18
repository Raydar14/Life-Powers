package com.lifepowers.app.breath

import com.lifepowers.app.data.personal.BreathPatternEntity
import com.lifepowers.app.data.personal.BreathSessionEntity
import com.lifepowers.app.domain.BreathSessionRecord
import com.lifepowers.app.domain.SavedBreathPattern
import com.lifepowers.core.BioSummary
import com.lifepowers.core.BreathPattern

/* Row and domain conversions for breath patterns and sessions. */

internal fun BreathPatternEntity.toDomain() = SavedBreathPattern(
    id = id,
    name = name,
    pattern = BreathPattern(inhaleS, holdInS, exhaleS, holdOutS),
    guide = BreathCodec.decodeGuide(guide),
    sessionMin = sessionMin,
    createdUtcMs = createdUtc,
    lastUsedUtcMs = lastUsedUtc,
)

internal fun SavedBreathPattern.toEntity() = BreathPatternEntity(
    id = id,
    name = name.trim(),
    inhaleS = pattern.inhaleS,
    holdInS = pattern.holdInS,
    exhaleS = pattern.exhaleS,
    holdOutS = pattern.holdOutS,
    guide = BreathCodec.encodeGuide(guide),
    sessionMin = sessionMin?.takeIf { it > 0 },
    createdUtc = createdUtcMs,
    lastUsedUtc = lastUsedUtcMs,
)

internal fun BreathSessionEntity.toDomain() = BreathSessionRecord(
    id = id,
    patternId = patternId,
    patternName = patternName,
    pattern = BreathPattern(inhaleS, holdInS, exhaleS, holdOutS),
    startUtcMs = startUtc,
    endUtcMs = endUtc,
    zoneOffsetMin = zoneOffsetMin,
    sensor = sensor,
    summary = BioSummary(
        startBpm = startBpm,
        endBpm = endBpm,
        startRmssdMs = startRmssd,
        endRmssdMs = endRmssd,
        bestSwingBpm = bestSwing,
        meanSync = meanSync,
        readings = readings,
    ),
    hrTimeline = BreathCodec.decodeTimeline(hrTimeline),
    goalId = goalId,
)

internal fun BreathSessionRecord.toEntity(eventId: Long?) = BreathSessionEntity(
    id = id,
    patternId = patternId,
    patternName = patternName,
    inhaleS = pattern.inhaleS,
    holdInS = pattern.holdInS,
    exhaleS = pattern.exhaleS,
    holdOutS = pattern.holdOutS,
    startUtc = startUtcMs,
    endUtc = endUtcMs,
    zoneOffsetMin = zoneOffsetMin,
    sensor = sensor,
    startBpm = summary.startBpm,
    endBpm = summary.endBpm,
    startRmssd = summary.startRmssdMs,
    endRmssd = summary.endRmssdMs,
    bestSwing = summary.bestSwingBpm,
    meanSync = summary.meanSync,
    readings = summary.readings,
    hrTimeline = BreathCodec.encodeTimeline(hrTimeline),
    goalId = goalId,
    eventId = eventId,
)
