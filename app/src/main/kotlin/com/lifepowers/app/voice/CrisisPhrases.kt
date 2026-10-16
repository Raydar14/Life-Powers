package com.lifepowers.app.voice

/**
 * Plain substring matcher for self harm or crisis language in a transcript. Deliberately small and
 * conservative: a match shows resources and excludes the recording from scoring, nothing more.
 */
object CrisisPhrases {
    val phrases: List<String> = listOf(
        "kill myself",
        "killing myself",
        "end it all",
        "end my life",
        "don't want to be here",
        "do not want to be here",
        "don't want to be alive",
        "do not want to be alive",
        "want to die",
        "wanna die",
        "wish i was dead",
        "wish i were dead",
        "hurt myself",
        "hurting myself",
        "better off dead",
        "no reason to live",
        "suicidal",
        "suicide",
    )

    fun matches(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val lower = normalize(text)
        return phrases.any { lower.contains(it) }
    }

    /** Lowercase, straighten curly apostrophes, collapse whitespace. */
    internal fun normalize(text: String): String =
        text.lowercase()
            .replace('’', '\'')
            .replace('‘', '\'')
            .replace(Regex("\\s+"), " ")
            .trim()
}
