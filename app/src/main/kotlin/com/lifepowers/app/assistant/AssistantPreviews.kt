package com.lifepowers.app.assistant

import com.lifepowers.app.domain.AssistantFeatures
import com.lifepowers.app.domain.AssistantPreview
import com.lifepowers.app.domain.Photo
import com.lifepowers.app.domain.TarotPull
import java.util.Locale

/**
 * Builds exactly what each call will send, as plain lines for the person to read before anything
 * leaves the phone. Pure functions, so the preview and the request are built from the same inputs.
 */
object AssistantPreviews {

    private const val NOTE_PREVIEW_CHARS = 80

    fun card(pull: TarotPull, includeNote: Boolean, dayTier: String?): AssistantPreview {
        val sends = mutableListOf<String>()
        sends += "The card: ${pull.card.trim()}, ${if (pull.reversed) "reversed" else "upright"}"
        spreadAndPosition(pull)?.let { sends += "Spread and position: $it" }
        val note = pull.note?.trim().orEmpty()
        if (includeNote && note.isNotEmpty()) sends += "Your note: “${shorten(note, NOTE_PREVIEW_CHARS)}”"
        if (!dayTier.isNullOrBlank()) sends += "Today's forecast color: ${dayTier.trim()}"
        return AssistantPreview(
            feature = AssistantFeatures.CARD,
            title = "Ask about this card",
            sends = sends,
            neverSends = listOf("No health measurements", "No photos", "No journal text beyond the note you chose"),
        )
    }

    fun photo(sizeBytes: Int, caption: String?): AssistantPreview {
        val sends = mutableListOf("One photo, ${sizeWords(sizeBytes)}, without its location or camera details")
        val c = caption?.trim().orEmpty()
        if (c.isNotEmpty()) sends += "Its caption: “${shorten(c, NOTE_PREVIEW_CHARS)}”"
        return AssistantPreview(
            feature = AssistantFeatures.PHOTO,
            title = "Ask about this photo",
            sends = sends,
            neverSends = listOf("No other photos", "No journal text", "No health measurements", "No location"),
        )
    }

    fun checkin(transcript: String): AssistantPreview {
        val words = wordCount(transcript)
        return AssistantPreview(
            feature = AssistantFeatures.CHECKIN,
            title = "Ask for a richer reading",
            sends = listOf("The words of this check-in, ${if (words == 1) "1 word" else "about $words words"}"),
            neverSends = listOf("No audio", "No voice measurements", "No health measurements", "No name or date"),
        )
    }

    fun narrative(findings: List<String>, goals: List<String>): AssistantPreview {
        val sends = mutableListOf<String>()
        for (f in findings.map { it.trim() }.filter { it.isNotEmpty() }) sends += "Finding: $f"
        for (g in goals.map { it.trim() }.filter { it.isNotEmpty() }) sends += "Goal: $g"
        if (sends.isEmpty()) sends += "Nothing yet. There are no findings or goals to write from."
        return AssistantPreview(
            feature = AssistantFeatures.NARRATIVE,
            title = "Write this week's note",
            sends = sends,
            neverSends = listOf("No raw measurements", "No journal text", "No photos", "No check-in words"),
        )
    }

    /** The plain words stored in the call log: what was sent, never the key. */
    fun sentSummary(preview: AssistantPreview): String = "Sent " + preview.sends.joinToString("; ") { it.trimEnd('.') }

    internal fun spreadAndPosition(pull: TarotPull): String? {
        val parts = listOfNotNull(pull.spread?.trim()?.takeIf { it.isNotEmpty() }, pull.position?.trim()?.takeIf { it.isNotEmpty() })
        return if (parts.isEmpty()) null else parts.joinToString(", ")
    }

    /** "about 400 KB" or "about 1.2 MB". */
    fun sizeWords(bytes: Int): String {
        val kb = bytes / 1024.0
        return if (kb < 1000) {
            val rounded = if (kb < 100) kb.toInt().coerceAtLeast(1) else ((kb / 10).toInt() * 10)
            "about $rounded KB"
        } else {
            "about " + String.format(Locale.US, "%.1f", kb / 1024.0) + " MB"
        }
    }

    fun wordCount(text: String): Int = text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }

    internal fun shorten(text: String, max: Int): String {
        val oneLine = text.replace(Regex("\\s+"), " ").trim()
        return if (oneLine.length <= max) oneLine else oneLine.take(max).trimEnd() + "..."
    }
}
