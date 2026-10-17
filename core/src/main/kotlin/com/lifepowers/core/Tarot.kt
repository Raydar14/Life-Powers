package com.lifepowers.core

/**
 * The standard 78 card names, used to offer a picker. The app supplies names only; what a card means
 * is the person's to write.
 */
object Tarot {
    val MAJOR = listOf(
        "The Fool", "The Magician", "The High Priestess", "The Empress", "The Emperor", "The Hierophant",
        "The Lovers", "The Chariot", "Strength", "The Hermit", "Wheel of Fortune", "Justice", "The Hanged Man",
        "Death", "Temperance", "The Devil", "The Tower", "The Star", "The Moon", "The Sun", "Judgement", "The World",
    )
    val SUITS = listOf("Wands", "Cups", "Swords", "Pentacles")
    val RANKS = listOf("Ace", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Page", "Knight", "Queen", "King")

    val DECK: List<String> = MAJOR + SUITS.flatMap { suit -> RANKS.map { "$it of $suit" } }

    val SPREADS = listOf("One card", "Three card: past, present, future", "Three card: situation, action, outcome", "Celtic Cross", "Horseshoe")

    fun search(query: String, limit: Int = 8): List<String> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return (DECK.filter { it.lowercase().startsWith(q) } + DECK.filter { it.lowercase().contains(q) }).distinct().take(limit)
    }

    data class Pull(val card: String, val reversed: Boolean, val phase: String?, val dayTier: Tier?)

    /**
     * Descriptive counts for the mirror view: how often each card turned up, and in which phases and
     * day tiers. Never a prediction.
     */
    data class CardCount(val card: String, val count: Int, val reversed: Int, val byPhase: Map<String, Int>, val byTier: Map<Tier, Int>)

    fun counts(pulls: List<Pull>): List<CardCount> = pulls.groupBy { it.card }.map { (card, ps) ->
        CardCount(
            card, ps.size, ps.count { it.reversed },
            ps.mapNotNull { it.phase }.groupingBy { it }.eachCount(),
            ps.mapNotNull { it.dayTier }.groupingBy { it }.eachCount(),
        )
    }.sortedWith(compareByDescending<CardCount> { it.count }.thenBy { it.card })
}
