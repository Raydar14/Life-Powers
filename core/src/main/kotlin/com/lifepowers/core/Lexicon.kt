package com.lifepowers.core

data class WordsScores(
    val valence: Double,
    val optimism: Double,
    val anxietyMarkers: Double,
    val selfFocus: Double,
    val energySelfReport: Double?,
    val wordCount: Int,
)

/** On device lexicon scorer for check-in and journal transcripts. Small, transparent, and tunable. */
object Lexicon {
    private val positive = setOf("good", "great", "fine", "okay", "ok", "glad", "happy", "calm", "rested", "excited", "grateful", "better", "love", "nice", "easy", "light", "clear", "strong", "proud", "fun", "cozy", "peaceful", "hopeful")
    private val negative = setOf("bad", "tired", "exhausted", "sad", "down", "low", "awful", "terrible", "stressed", "overwhelmed", "hard", "heavy", "worried", "anxious", "angry", "upset", "hurt", "sick", "lonely", "flat", "numb", "stuck", "drained", "fried", "foggy")
    private val future = setOf("plan", "planning", "will", "going to", "gonna", "looking forward", "tomorrow", "next", "excited", "hope", "hoping", "want to", "ready")
    private val worry = setOf("worried", "worry", "nervous", "anxious", "afraid", "scared", "what if", "can't stop", "cannot stop", "on edge", "tense", "panic", "dread", "overthinking")
    private val absolutist = setOf("always", "never", "completely", "totally", "nothing", "everything", "every time", "no one", "nobody", "constantly")
    private val firstPersonSingular = setOf("i", "me", "my", "mine", "myself", "i'm", "i've", "i'd", "i'll")

    fun score(text: String): WordsScores {
        val lower = text.lowercase()
        val tokens = Regex("[a-z']+").findAll(lower).map { it.value }.toList()
        val n = maxOf(1, tokens.size)
        val pos = tokens.count { it in positive }
        val neg = tokens.count { it in negative }
        val valence = if (pos + neg == 0) 0.5 else pos.toDouble() / (pos + neg)
        val fut = future.count { lower.contains(it) }
        val optimism = clamp01(0.5 + 0.15 * fut - 0.1 * neg.coerceAtMost(3))
        val worries = worry.count { lower.contains(it) } + absolutist.count { lower.contains(it) } * 0.5
        val anxiety = clamp01(worries / 4.0)
        val selfFocus = clamp01(tokens.count { it in firstPersonSingular }.toDouble() / n * 4.0)
        return WordsScores(valence, optimism, anxiety, selfFocus, energySelfReport(lower), tokens.size)
    }

    /** "energy is maybe a six", "energy 7 out of 10", "a four today". */
    fun energySelfReport(lower: String): Double? {
        val digit = Regex("energy[^.]{0,30}?\\b(10|[1-9])\\b").find(lower)?.groupValues?.get(1)
        if (digit != null) return digit.toDouble()
        val words = mapOf("one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10)
        val m = Regex("energy[^.]{0,30}?\\b(one|two|three|four|five|six|seven|eight|nine|ten)\\b").find(lower)
        return m?.let { words[it.groupValues[1]]?.toDouble() }
    }

    private fun clamp01(x: Double) = x.coerceIn(0.0, 1.0)
}
