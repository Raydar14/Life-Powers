package com.lifepowers.core

/** A texture word with hidden coordinates: energy (-1 low .. 1 high) and ease (-1 tight .. 1 easy). */
data class FeelWord(val word: String, val energy: Double, val ease: Double, val origin: String = "builtin")

object FeelWords {
    val builtin: List<FeelWord> = listOf(
        FeelWord("cozy", -0.2, 0.9),
        FeelWord("light", 0.5, 0.8),
        FeelWord("steady", 0.1, 0.7),
        FeelWord("full", 0.4, 0.6),
        FeelWord("floaty", 0.2, 0.5),
        FeelWord("open", 0.4, 0.8),
        FeelWord("buzzing", 0.8, 0.1),
        FeelWord("wired", 0.9, -0.5),
        FeelWord("fried", -0.3, -0.7),
        FeelWord("heavy", -0.8, -0.4),
        FeelWord("foggy", -0.6, -0.2),
        FeelWord("flat", -0.7, 0.0),
        FeelWord("hollow", -0.6, -0.5),
        FeelWord("tangled", 0.1, -0.8),
        FeelWord("raw", 0.3, -0.9),
        FeelWord("tender", -0.2, -0.3),
        FeelWord("scratchy", 0.2, -0.6),
        FeelWord("numb", -0.8, -0.1),
    )

    enum class Tempo(val value: Int) { SLOW(1), EASY(2), QUICK(3), RACING(4) }

    data class Entry(val words: List<FeelWord>, val tempo: Tempo?, val tintHue: Double?, val tintLight: Double?)

    /** Averages the coordinates of the chosen words. */
    fun coordinates(words: List<FeelWord>): Pair<Double, Double>? {
        if (words.isEmpty()) return null
        return words.map { it.energy }.average() to words.map { it.ease }.average()
    }

    /** Places a new, unknown word near the center so usage can refine it later. */
    fun newUserWord(word: String): FeelWord = FeelWord(word.trim().lowercase(), 0.0, 0.0, "user")
}
