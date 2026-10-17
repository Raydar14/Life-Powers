package com.lifepowers.app.assistant

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The structured reply for photo questions. */
@Serializable
data class QuestionsReply(val questions: List<String> = emptyList())

/** The structured reply for check-in ratings. */
@Serializable
data class RatingsReply(
    val energy: Double,
    val optimism: Double,
    val tension: Double,
    val warmth: Double,
    val reflection: String = "",
)

/** Schemas for structured outputs, and parsing and cleaning of what comes back. Pure functions. */
object AssistantReplies {

    private val lenient = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    /**
     * JSON schema for {"questions": [...]}. Structured outputs reject numeric and length bounds,
     * so limits are described in words and enforced when parsing.
     */
    val QUESTIONS_SCHEMA: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "questions" to mapOf(
                "type" to "array",
                "description" to "Two or three short questions that help the person document the photo.",
                "items" to mapOf("type" to "string"),
            ),
        ),
        "required" to listOf("questions"),
        "additionalProperties" to false,
    )

    val RATINGS_SCHEMA: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "energy" to number("Energy in the words, 0 drained to 10 lively."),
            "optimism" to number("Optimism in the words, 0 bleak to 10 hopeful."),
            "tension" to number("Tension in the words, 0 at ease to 10 strained."),
            "warmth" to number("Warmth in the words, 0 distant to 10 connected and tender."),
            "reflection" to mapOf("type" to "string", "description" to "One gentle sentence in second person."),
        ),
        "required" to listOf("energy", "optimism", "tension", "warmth", "reflection"),
        "additionalProperties" to false,
    )

    private fun number(description: String): Map<String, Any> = mapOf("type" to "number", "description" to description)

    /** Two or three cleaned questions, or null when the reply could not be read. */
    fun parseQuestions(text: String): List<String>? {
        val reply = runCatching { lenient.decodeFromString(QuestionsReply.serializer(), stripFence(text)) }.getOrNull() ?: return null
        val questions = reply.questions
            .map { clean(it).replace('\n', ' ').trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(3)
        return questions.ifEmpty { null }
    }

    /** Ratings clamped to 0 to 10, or null when the reply could not be read. */
    fun parseRatings(text: String): RatingsReply? {
        val reply = runCatching { lenient.decodeFromString(RatingsReply.serializer(), stripFence(text)) }.getOrNull() ?: return null
        val values = listOf(reply.energy, reply.optimism, reply.tension, reply.warmth)
        if (values.any { it.isNaN() || it.isInfinite() }) return null
        return RatingsReply(
            energy = clamp(reply.energy),
            optimism = clamp(reply.optimism),
            tension = clamp(reply.tension),
            warmth = clamp(reply.warmth),
            reflection = clean(reply.reflection).replace('\n', ' ').trim(),
        )
    }

    fun clamp(v: Double): Double = (Math.round(v.coerceIn(0.0, 10.0) * 10.0) / 10.0)

    /** Drops a markdown code fence if one slipped around the JSON. */
    internal fun stripFence(text: String): String {
        val t = text.trim()
        if (!t.startsWith("```")) return t
        return t.removePrefix("```json").removePrefix("```JSON").removePrefix("```").removeSuffix("```").trim()
    }

    /**
     * Keeps the person's preference against em dashes even if one slips through: a spaced dash
     * becomes a comma, a tight one a hyphen. Also trims stray whitespace.
     */
    fun clean(text: String): String = text
        .replace(Regex("\\s*\u2014\\s*"), ", ")
        .replace(Regex("\\s+\u2013\\s+"), ", ")
        .replace(Regex(",\\s*,"), ",")
        .replace(Regex("[ \\t]+\n"), "\n")
        .trim()

    /** A plain word for a rating on one of the four scales. */
    fun ratingWord(scale: String, value: Double): String {
        val band = when {
            value < 2.0 -> 0
            value < 4.0 -> 1
            value < 6.0 -> 2
            value < 8.0 -> 3
            else -> 4
        }
        val words = when (scale) {
            "energy" -> listOf("drained", "low", "steady", "lively", "very lively")
            "optimism" -> listOf("bleak", "guarded", "even", "hopeful", "bright")
            "tension" -> listOf("at ease", "mostly calm", "some strain", "tense", "very tense")
            "warmth" -> listOf("distant", "reserved", "open", "warm", "tender")
            else -> listOf("very low", "low", "middling", "high", "very high")
        }
        return words[band]
    }

    /** "7" or "6.5", for showing a rating beside its word. */
    fun ratingNumber(value: Double): String {
        val r = clamp(value)
        return if (r == Math.floor(r)) r.toInt().toString() else r.toString()
    }
}
