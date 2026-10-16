package com.lifepowers.app.voice

import com.lifepowers.app.domain.CheckinRecord
import com.lifepowers.app.domain.EventKinds

/**
 * Tiny matcher for behaviors spoken in a check-in. Output is a list of event kind strings the UI can
 * show as confirm chips; nothing is stored until the person taps one.
 */
object BehaviorMentions {
    /** Event kind from docs/DATA_MODEL.md for phone use late at night; not yet in [EventKinds]. */
    const val SCREENS_IN_BED = "screens_in_bed"

    private val alcohol = listOf("wine", "beer", "beers", "drink", "drinks", "drinking", "cocktail", "cocktails", "whiskey", "vodka", "gin and", "a glass of", "glasses of")
    private val caffeine = listOf("coffee", "coffees", "espresso", "latte", "cappuccino", "caffeine", "energy drink", "red bull", "cold brew")
    private val latePhone = listOf("on my phone", "scrolling", "phone late", "phone in bed", "doomscroll", "on tiktok", "on instagram")
    private val stress = listOf("stressed", "stressful", "busy day", "a lot going on", "a lot on", "so much to do", "overwhelmed", "under pressure", "deadline")

    private val numberWords = mapOf(
        "a" to 1.0, "an" to 1.0, "one" to 1.0, "two" to 2.0, "couple" to 2.0, "three" to 3.0,
        "four" to 4.0, "five" to 5.0, "six" to 6.0, "few" to 3.0,
    )

    private val rules: List<Pair<String, List<String>>> = listOf(
        EventKinds.ALCOHOL to alcohol,
        EventKinds.CAFFEINE to caffeine,
        SCREENS_IN_BED to latePhone,
        EventKinds.STRESS_EVENT to stress,
    )

    fun detect(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        val lower = CrisisPhrases.normalize(text)
        // "drink" alone is ambiguous ("drink more water"); require no water context in the clause.
        return rules.mapNotNull { (kind, words) ->
            val hit = words.any { lower.contains(it) }
            if (!hit) return@mapNotNull null
            if (kind == EventKinds.ALCOHOL && isOnlyWater(lower)) return@mapNotNull null
            kind
        }
    }

    fun suggestions(record: CheckinRecord): List<String> = detect(record.transcript)

    /**
     * Rough count for alcohol or caffeine: a number word or digit right before the trigger word
     * ("two glasses of wine", "3 coffees"), else 1 when the kind was mentioned, else null.
     */
    fun quantityFor(kind: String, text: String?): Double? {
        if (text.isNullOrBlank()) return null
        val lower = CrisisPhrases.normalize(text)
        val words = when (kind) {
            EventKinds.ALCOHOL -> alcohol
            EventKinds.CAFFEINE -> caffeine
            else -> return null
        }
        if (!words.any { lower.contains(it) }) return null
        val pattern = Regex("\\b(\\d+|one|two|three|four|five|six|a|an|couple of|few)\\s+(?:more\\s+|big\\s+|small\\s+|large\\s+)?(glass of|glasses of|cup of|cups of|${words.joinToString("|") { Regex.escape(it) }})")
        val m = pattern.find(lower) ?: return 1.0
        val token = m.groupValues[1].removeSuffix(" of").trim()
        return token.toDoubleOrNull() ?: numberWords[token] ?: 1.0
    }

    private fun isOnlyWater(lower: String): Boolean {
        val alcoholSpecific = alcohol.filter { it != "drink" && it != "drinks" && it != "drinking" }
        if (alcoholSpecific.any { lower.contains(it) }) return false
        return lower.contains("water") || lower.contains("tea")
    }
}
