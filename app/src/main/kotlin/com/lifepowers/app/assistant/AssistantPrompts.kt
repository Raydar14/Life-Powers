package com.lifepowers.app.assistant

import com.lifepowers.app.domain.TarotPull

/**
 * System prompts and the user messages built from exactly what the preview listed.
 * Every system prompt carries the person's standing preference against em dashes.
 */
object AssistantPrompts {

    private const val STYLE = "Do not use em dashes. Write in plain sentence case with no headings, lists, emoji, or markdown."

    val CARD_SYSTEM = """
        You help a person reflect on a tarot card they drew and recorded in their private journal.
        Share the card's traditional meanings for the way it was drawn, upright or reversed, and offer reflection prompts.
        Frame everything as a mirror for reflection. Never present it as a prediction of events, and never give advice about what the person should do.
        Do not give medical, legal, or financial guidance.
        Write warmly and plainly, in second person, in short paragraphs, under 180 words in total.
        End with two or three open questions the person can sit with.
        $STYLE
    """.trimIndent()

    val PHOTO_SYSTEM = """
        You help a person document a photo in their private journal so the record is richer than a caption.
        Look at the photo and ask two or three short, friendly questions that would help them write down what it shows and what it meant: what they did, where, how long it took, who was there, how it felt.
        For example: "Is this the garden bed from last week? How long did you work on it?"
        Base the questions on what is visible and on their caption when there is one. Keep each question under 20 words.
        Never comment on the person's body, weight, shape, skin, or appearance unless their caption asks you to.
        Do not guess who people in the photo are.
        $STYLE
        Reply only with the JSON object requested.
    """.trimIndent()

    val CHECKIN_SYSTEM = """
        You read the transcript of a short spoken check-in a person recorded for their private journal.
        Rate what the words express on four scales from 0 to 10:
        energy (0 drained, 10 lively), optimism (0 bleak, 10 hopeful), tension (0 at ease, 10 strained), warmth (0 distant, 10 connected and tender).
        Rate only what the words express, and use 5 for a scale the words do not touch.
        Then write one gentle sentence in second person that mirrors back what you heard, without judging.
        Never diagnose. Never name a mental health condition or describe feelings as a disorder. Give no medical advice.
        If the words suggest the person may be in danger, the sentence gently suggests reaching out to someone they trust or to local emergency services.
        $STYLE
        Reply only with the JSON object requested.
    """.trimIndent()

    val NARRATIVE_SYSTEM = """
        You write a short weekly note for a person from findings their private wellbeing app found in their own data, and from their goal summaries.
        Write a warm, plain note of about 120 words, in second person.
        Use only the findings and goals given. Do not add numbers, facts, or causes that were not given, and keep the careful wording: a pattern is something that tends to go together, not proof of a cause.
        Give no medical advice and no diagnoses, and never suggest changing a medication.
        $STYLE
    """.trimIndent()

    fun cardMessage(pull: TarotPull, includeNote: Boolean, dayTier: String?): String = buildString {
        appendLine("The card I drew: ${pull.card.trim()}, ${if (pull.reversed) "reversed" else "upright"}.")
        AssistantPreviews.spreadAndPosition(pull)?.let { appendLine("Spread and position: $it.") }
        val note = pull.note?.trim().orEmpty()
        if (includeNote && note.isNotEmpty()) appendLine("My own note about it: $note")
        if (!dayTier.isNullOrBlank()) {
            appendLine("Today's color in my app's day forecast: ${dayTier.trim()}. It is my own wellbeing forecast, not a fortune.")
        }
        append("Please share its traditional meanings and a few reflection prompts.")
    }

    fun photoMessage(caption: String?): String {
        val c = caption?.trim().orEmpty()
        return if (c.isEmpty()) {
            "Here is a photo from my journal. Ask me two or three questions that would help me document it."
        } else {
            "Here is a photo from my journal. My caption: $c\nAsk me two or three questions that would help me document it."
        }
    }

    fun checkinMessage(transcript: String): String = "Here is what I said in my check-in:\n\n${transcript.trim()}"

    fun narrativeMessage(findings: List<String>, goals: List<String>): String = buildString {
        val f = findings.map { it.trim() }.filter { it.isNotEmpty() }
        val g = goals.map { it.trim() }.filter { it.isNotEmpty() }
        appendLine("Findings from my own data this week:")
        if (f.isEmpty()) appendLine("None yet.") else f.forEach { appendLine("- $it") }
        appendLine()
        appendLine("My goals:")
        if (g.isEmpty()) appendLine("None set.") else g.forEach { appendLine("- $it") }
        appendLine()
        append("Please write this week's note.")
    }
}
