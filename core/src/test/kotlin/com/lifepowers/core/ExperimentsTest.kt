package com.lifepowers.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

class ExperimentsTest {
    private val start = LocalDate.of(2026, 10, 1)
    private val spec = ExperimentSpec(1, "no screens after 9 pm", Metrics.SLEEP_DEEP_MIN, start, blockDays = 3, targetDays = 28, outcomeLag = 1)

    @Test fun blocksAlternateWithinPairs() {
        val s = Experiments.schedule(spec)
        assertEquals(28, s.size)
        assertEquals(s[0].second, s[2].second)
        assertTrue(s[0].second != s[3].second)
        assertTrue(s.count { it.second == Condition.ON } in 12..16)
        assertNull(Experiments.condition(spec, start.minusDays(1)))
        assertNull(Experiments.condition(spec, start.plusDays(28)))
    }

    @Test fun scoresAClearEffectAndHonorsAdherence() {
        val outcome = (0..30).associate { d ->
            val date = start.plusDays(d.toLong())
            val prev = Experiments.condition(spec, date.minusDays(1))
            date to (60.0 + (d % 3) + if (prev == Condition.ON) 15.0 else 0.0)
        }
        val today = start.plusDays(28)
        val r = Experiments.score(spec, emptyMap(), outcome, today)
        assertTrue(r.ready)
        assertTrue(r.effect!! > 1.0)
        assertEquals(true, r.good)
        assertTrue(r.pValue!! < 0.01)
        val skipAll = Experiments.schedule(spec).associate { it.first to false }
        assertFalse(Experiments.score(spec, skipAll, outcome, today).ready)
        assertTrue(Experiments.sentence(spec, r) { "deep sleep" }.startsWith("Your deep sleep was clearly higher on days with no screens after 9 pm"))
    }

    @Test fun backupRoundTripAndWrongPassword() {
        val data = "hello backup".toByteArray()
        val enc = BackupCrypto.encrypt(data, "correct horse".toCharArray(), iterations = 20_000)
        assertEquals("hello backup", String(BackupCrypto.decrypt(enc, "correct horse".toCharArray())))
        try {
            BackupCrypto.decrypt(enc, "wrong password".toCharArray()); fail()
        } catch (e: BackupCrypto.WrongPasswordException) { }
        try {
            BackupCrypto.decrypt("not a backup at all, clearly".toByteArray(), "x".toCharArray()); fail()
        } catch (e: BackupCrypto.NotABackupException) { }
        assertEquals("Use at least 10 characters.", BackupCrypto.passwordProblem("short"))
    }
}
