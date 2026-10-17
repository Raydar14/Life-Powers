package com.lifepowers.app.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantRepliesTest {

    @Test
    fun questionsParseAndTrimToThree() {
        val q = AssistantReplies.parseQuestions("""{"questions":["Is this the garden bed?","How long did it take?","Who helped?","Extra?"]}""")
        assertEquals(listOf("Is this the garden bed?", "How long did it take?", "Who helped?"), q)
    }

    @Test
    fun questionsSurviveACodeFence() {
        val q = AssistantReplies.parseQuestions("```json\n{\"questions\":[\"Where was this?\"]}\n```")
        assertEquals(listOf("Where was this?"), q)
    }

    @Test
    fun badQuestionsAreNull() {
        assertNull(AssistantReplies.parseQuestions("not json"))
        assertNull(AssistantReplies.parseQuestions("""{"questions":[" ",""]}"""))
    }

    @Test
    fun ratingsClampAndClean() {
        val r = AssistantReplies.parseRatings("""{"energy":12,"optimism":6.44,"tension":-1,"warmth":7,"reflection":"You sound tired \u2014 and hopeful."}""")
        assertNotNull(r)
        r!!
        assertEquals(10.0, r.energy, 0.0)
        assertEquals(6.4, r.optimism, 0.0)
        assertEquals(0.0, r.tension, 0.0)
        assertEquals(7.0, r.warmth, 0.0)
        assertEquals("You sound tired, and hopeful.", r.reflection)
    }

    @Test
    fun ratingsMissingAFieldAreNull() {
        assertNull(AssistantReplies.parseRatings("""{"energy":5,"optimism":5,"tension":5,"reflection":"x"}"""))
    }

    @Test
    fun cleanRemovesEmDashes() {
        assertEquals("Rest, then begin.", AssistantReplies.clean("Rest \u2014 then begin."))
        assertEquals("Rest, then begin.", AssistantReplies.clean("Rest\u2014then begin."))
        assertFalse(AssistantReplies.clean("a \u2014 b \u2014 c").contains("\u2014"))
        assertEquals("pages 3-4 stay", AssistantReplies.clean("pages 3-4 stay"))
    }

    @Test
    fun ratingWordsAndNumbers() {
        assertEquals("drained", AssistantReplies.ratingWord("energy", 1.0))
        assertEquals("steady", AssistantReplies.ratingWord("energy", 5.0))
        assertEquals("very tense", AssistantReplies.ratingWord("tension", 9.5))
        assertEquals("warm", AssistantReplies.ratingWord("warmth", 7.9))
        assertEquals("7", AssistantReplies.ratingNumber(7.0))
        assertEquals("6.5", AssistantReplies.ratingNumber(6.5))
    }

    @Test
    fun schemasNameEveryRequiredField() {
        @Suppress("UNCHECKED_CAST")
        val required = AssistantReplies.RATINGS_SCHEMA["required"] as List<String>
        assertEquals(listOf("energy", "optimism", "tension", "warmth", "reflection"), required)
        assertEquals(false, AssistantReplies.QUESTIONS_SCHEMA["additionalProperties"])
    }
}
