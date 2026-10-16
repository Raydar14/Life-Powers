package com.lifepowers.app.data

import com.lifepowers.core.AcousticFeatures
import com.lifepowers.core.ParsedSymptom
import com.lifepowers.core.VoiceIndices
import com.lifepowers.core.WordsScores
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/*
 * The core data classes are not @Serializable, so the check-in row stores these mirrors.
 * Every field is nullable or defaulted so an older row still decodes after a core change.
 */

@Serializable
data class AcousticFeaturesDto(
    val durationS: Double = 0.0,
    val f0MeanHz: Double = 0.0,
    val f0StdHz: Double = 0.0,
    val f0RangeHz: Double = 0.0,
    val loudnessMeanDb: Double = 0.0,
    val loudnessStdDb: Double = 0.0,
    val voicedFraction: Double = 0.0,
    val pauseCount: Int = 0,
    val pauseMeanS: Double = 0.0,
    val speechRateHz: Double = 0.0,
    val qualityOk: Boolean = true,
    val qualityNote: String? = null,
) {
    fun toCore() = AcousticFeatures(
        durationS, f0MeanHz, f0StdHz, f0RangeHz, loudnessMeanDb, loudnessStdDb,
        voicedFraction, pauseCount, pauseMeanS, speechRateHz, qualityOk, qualityNote,
    )

    companion object {
        fun from(f: AcousticFeatures) = AcousticFeaturesDto(
            f.durationS, f.f0MeanHz, f.f0StdHz, f.f0RangeHz, f.loudnessMeanDb, f.loudnessStdDb,
            f.voicedFraction, f.pauseCount, f.pauseMeanS, f.speechRateHz, f.qualityOk, f.qualityNote,
        )
    }
}

@Serializable
data class WordsScoresDto(
    val valence: Double = 0.0,
    val optimism: Double = 0.0,
    val anxietyMarkers: Double = 0.0,
    val selfFocus: Double = 0.0,
    val energySelfReport: Double? = null,
    val wordCount: Int = 0,
) {
    fun toCore() = WordsScores(valence, optimism, anxietyMarkers, selfFocus, energySelfReport, wordCount)

    companion object {
        fun from(w: WordsScores) =
            WordsScoresDto(w.valence, w.optimism, w.anxietyMarkers, w.selfFocus, w.energySelfReport, w.wordCount)
    }
}

@Serializable
data class VoiceIndicesDto(val energy: Double? = null, val flatness: Double? = null, val tension: Double? = null) {
    fun toCore() = VoiceIndices(energy, flatness, tension)

    companion object {
        fun from(v: VoiceIndices) = VoiceIndicesDto(v.energy, v.flatness, v.tension)
    }
}

@Serializable
data class ParsedSymptomDto(val kind: String, val severity: Int = 3, val phrase: String = "") {
    fun toCore() = ParsedSymptom(kind, severity, phrase)

    companion object {
        fun from(s: ParsedSymptom) = ParsedSymptomDto(s.kind, s.severity, s.phrase)
    }
}

/** Encodes and decodes the JSON columns of voice_checkin. Decoding failures yield null, never a crash. */
class CheckinJson(private val json: Json) {
    fun encodeFeatures(f: AcousticFeatures?): String? =
        f?.let { json.encodeToString(AcousticFeaturesDto.serializer(), AcousticFeaturesDto.from(it)) }

    fun decodeFeatures(s: String?): AcousticFeatures? =
        s?.let { runCatching { json.decodeFromString(AcousticFeaturesDto.serializer(), it).toCore() }.getOrNull() }

    fun encodeWords(w: WordsScores?): String? =
        w?.let { json.encodeToString(WordsScoresDto.serializer(), WordsScoresDto.from(it)) }

    fun decodeWords(s: String?): WordsScores? =
        s?.let { runCatching { json.decodeFromString(WordsScoresDto.serializer(), it).toCore() }.getOrNull() }

    fun encodeIndices(v: VoiceIndices?): String? =
        v?.let { json.encodeToString(VoiceIndicesDto.serializer(), VoiceIndicesDto.from(it)) }

    fun decodeIndices(s: String?): VoiceIndices? =
        s?.let { runCatching { json.decodeFromString(VoiceIndicesDto.serializer(), it).toCore() }.getOrNull() }

    fun encodeSymptoms(list: List<ParsedSymptom>): String =
        json.encodeToString(symptomList, list.map { ParsedSymptomDto.from(it) })

    fun decodeSymptoms(s: String?): List<ParsedSymptom> =
        s?.let { runCatching { json.decodeFromString(symptomList, it).map { d -> d.toCore() } }.getOrNull() }
            ?: emptyList()

    fun encodeStrings(list: List<String>): String = json.encodeToString(stringList, list)

    fun decodeStrings(s: String?): List<String> =
        s?.let { runCatching { json.decodeFromString(stringList, it) }.getOrNull() } ?: emptyList()

    private companion object {
        val symptomList = ListSerializer(ParsedSymptomDto.serializer())
        val stringList = ListSerializer(String.serializer())
    }
}
