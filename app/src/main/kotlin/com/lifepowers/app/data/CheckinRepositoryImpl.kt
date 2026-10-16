package com.lifepowers.app.data

import com.lifepowers.app.domain.CheckinRecord
import com.lifepowers.app.domain.CheckinRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CheckinRepositoryImpl @Inject constructor(
    private val dao: VoiceCheckinDao,
    private val json: CheckinJson,
) : CheckinRepository {

    override suspend fun save(record: CheckinRecord): Long = dao.insert(record.toEntity())

    override fun recent(limit: Int): Flow<List<CheckinRecord>> =
        dao.recent(limit).map { rows -> rows.map { it.toRecord() } }

    override suspend fun baselineCount(): Int = dao.countQualityOk()

    private fun CheckinRecord.toEntity() = VoiceCheckinEntity(
        id = id,
        startUtc = startUtcMs,
        zoneOffsetMin = zoneOffsetMin,
        durationS = durationS,
        promptId = promptId,
        transcript = transcript,
        featuresJson = json.encodeFeatures(features),
        wordsJson = json.encodeWords(words),
        indicesJson = json.encodeIndices(indices),
        symptomsJson = json.encodeSymptoms(symptoms),
        qualityOk = qualityOk,
        qualityNote = qualityNote,
        crisisFlag = crisisFlag,
    )

    private fun VoiceCheckinEntity.toRecord() = CheckinRecord(
        id = id,
        startUtcMs = startUtc,
        zoneOffsetMin = zoneOffsetMin,
        durationS = durationS,
        promptId = promptId,
        transcript = transcript,
        features = json.decodeFeatures(featuresJson),
        words = json.decodeWords(wordsJson),
        indices = json.decodeIndices(indicesJson),
        symptoms = json.decodeSymptoms(symptomsJson),
        qualityOk = qualityOk,
        qualityNote = qualityNote,
        crisisFlag = crisisFlag,
    )
}
