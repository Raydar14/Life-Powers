package com.lifepowers.app.assistant

import com.lifepowers.app.domain.AssistantFeatures
import com.lifepowers.app.domain.TarotPull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AssistantPreviewsTest {

    private val pull = TarotPull(
        id = 4,
        localDate = LocalDate.of(2026, 10, 17),
        spread = "Three card",
        position = "past",
        card = "The Moon",
        reversed = true,
        note = "Felt foggy all morning",
    )

    @Test
    fun cardListsOnlyWhatIsChosen() {
        val p = AssistantPreviews.card(pull, includeNote = false, dayTier = null)
        assertEquals(AssistantFeatures.CARD, p.feature)
        assertEquals(listOf("The card: The Moon, reversed", "Spread and position: Three card, past"), p.sends)
        assertTrue(p.neverSends.contains("No health measurements"))
        assertTrue(p.neverSends.contains("No photos"))
        assertTrue(p.neverSends.contains("No journal text beyond the note you chose"))
    }

    @Test
    fun cardAddsNoteAndTierWhenChosen() {
        val p = AssistantPreviews.card(pull, includeNote = true, dayTier = "softer")
        assertTrue(p.sends.any { it.startsWith("Your note: ") && it.contains("Felt foggy all morning") })
        assertEquals("Today's forecast color: softer", p.sends.last())
    }

    @Test
    fun cardUprightWithoutSpreadHasOneLine() {
        val p = AssistantPreviews.card(pull.copy(reversed = false, spread = null, position = " ", note = null), includeNote = true, dayTier = null)
        assertEquals(listOf("The card: The Moon, upright"), p.sends)
    }

    @Test
    fun cardMessageLeavesTheNoteOutUnlessChosen() {
        assertFalse(AssistantPrompts.cardMessage(pull, includeNote = false, dayTier = null).contains("foggy"))
        assertTrue(AssistantPrompts.cardMessage(pull, includeNote = true, dayTier = null).contains("foggy"))
        assertFalse(AssistantPrompts.cardMessage(pull, includeNote = false, dayTier = null).contains("forecast"))
    }

    @Test
    fun photoPreviewSaysSizeAndCaption() {
        val p = AssistantPreviews.photo(400 * 1024, "Garden bed")
        assertEquals("One photo, about 400 KB, without its location or camera details", p.sends[0])
        assertEquals("Its caption: “Garden bed”", p.sends[1])
        assertEquals(1, AssistantPreviews.photo(400 * 1024, "  ").sends.size)
    }

    @Test
    fun sizeWordsRound() {
        assertEquals("about 1 KB", AssistantPreviews.sizeWords(200))
        assertEquals("about 56 KB", AssistantPreviews.sizeWords(56 * 1024 + 300))
        assertEquals("about 410 KB", AssistantPreviews.sizeWords(415 * 1024))
        assertEquals("about 1.4 MB", AssistantPreviews.sizeWords(1_450_000))
    }

    @Test
    fun checkinCountsWords() {
        val p = AssistantPreviews.checkin("  I slept badly   but the walk helped ")
        assertEquals(listOf("The words of this check-in, about 7 words"), p.sends)
        assertTrue(p.neverSends.contains("No audio"))
    }

    @Test
    fun narrativeListsEachLine() {
        val p = AssistantPreviews.narrative(listOf("Short sleep tends to come before cravings.", " "), listOf("Walk 20 minutes: 4 of 7 days"))
        assertEquals(listOf("Finding: Short sleep tends to come before cravings.", "Goal: Walk 20 minutes: 4 of 7 days"), p.sends)
    }

    @Test
    fun summaryIsPlainWords() {
        val p = AssistantPreviews.card(pull, includeNote = false, dayTier = null)
        assertEquals("Sent The card: The Moon, reversed; Spread and position: Three card, past", AssistantPreviews.sentSummary(p))
    }

    @Test
    fun everySystemPromptRefusesEmDashes() {
        for (prompt in listOf(AssistantPrompts.CARD_SYSTEM, AssistantPrompts.PHOTO_SYSTEM, AssistantPrompts.CHECKIN_SYSTEM, AssistantPrompts.NARRATIVE_SYSTEM)) {
            assertTrue(prompt.contains("Do not use em dashes."))
            assertFalse(prompt.contains("\u2014"))
        }
        assertTrue(AssistantPrompts.PHOTO_SYSTEM.contains("appearance"))
    }
}
