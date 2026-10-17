package com.lifepowers.app.vault

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VaultCodecTest {

    private fun roundTrip(v: SqlValue): SqlValue? {
        val text = VaultCodec.toJson(v).toString()
        return VaultCodec.fromJson(Json.parseToJsonElement(text))
    }

    @Test
    fun cellsSurviveJson() {
        assertEquals(SqlValue.Null, roundTrip(SqlValue.Null))
        assertEquals(SqlValue.Integer(Long.MAX_VALUE), roundTrip(SqlValue.Integer(Long.MAX_VALUE)))
        assertEquals(SqlValue.Integer(-42), roundTrip(SqlValue.Integer(-42)))
        assertEquals(SqlValue.Real(3.0), roundTrip(SqlValue.Real(3.0)))
        assertEquals(SqlValue.Real(0.125), roundTrip(SqlValue.Real(0.125)))
        assertEquals(SqlValue.Real(1.5e-7), roundTrip(SqlValue.Real(1.5e-7)))
        assertEquals(SqlValue.Text("123"), roundTrip(SqlValue.Text("123")))
        assertEquals(SqlValue.Text("line one\nsaid \"hi\", then left"), roundTrip(SqlValue.Text("line one\nsaid \"hi\", then left")))
        assertEquals(SqlValue.Blob(byteArrayOf(0, 1, -1, 127)), roundTrip(SqlValue.Blob(byteArrayOf(0, 1, -1, 127))))
    }

    @Test
    fun nonFiniteRealsBecomeNull() {
        assertEquals(JsonNull, VaultCodec.toJson(SqlValue.Real(Double.NaN)))
        assertEquals(JsonNull, VaultCodec.toJson(SqlValue.Real(Double.POSITIVE_INFINITY)))
    }

    @Test
    fun booleansReadAsIntegers() {
        assertEquals(SqlValue.Integer(1), VaultCodec.fromJson(JsonPrimitive(true)))
        assertEquals(SqlValue.Integer(0), VaultCodec.fromJson(JsonPrimitive(false)))
    }

    @Test
    fun secretsAreRecognized() {
        assertTrue(VaultCodec.isSecretPreference("oura_token"))
        assertTrue(VaultCodec.isSecretPreference("assistant_api_key"))
        assertTrue(VaultCodec.isSecretPreference("ClientSecret"))
        assertTrue(VaultCodec.isSecretPreference("db_passphrase"))
        assertFalse(VaultCodec.isSecretPreference("keep_audio"))
        assertFalse(VaultCodec.isSecretPreference("assistant_master"))
        assertFalse(VaultCodec.isSecretPreference("world_location_mode"))
    }

    @Test
    fun internalTablesAreSkipped() {
        assertFalse(VaultCodec.isUserTable("android_metadata"))
        assertFalse(VaultCodec.isUserTable("sqlite_sequence"))
        assertFalse(VaultCodec.isUserTable("sqlite_stat1"))
        assertFalse(VaultCodec.isUserTable("room_master_table"))
        assertTrue(VaultCodec.isUserTable("goal"))
        assertTrue(VaultCodec.isUserTable("metric_sample"))
    }

    @Test
    fun passphraseAndLibraryPrefsAreKept() {
        assertFalse(VaultCodec.shouldClearPreferenceFile("life_powers_keys"))
        assertFalse(VaultCodec.shouldClearPreferenceFile("__androidx_security_crypto_encrypted_file_pref__"))
        assertFalse(VaultCodec.shouldClearPreferenceFile("androidx.work.util.preferences"))
        assertTrue(VaultCodec.shouldClearPreferenceFile("assistant_secrets"))
        assertTrue(VaultCodec.shouldClearPreferenceFile("goal_reminders"))
    }

    @Test
    fun headerIsChecked() {
        val good = Json.parseToJsonElement("""{"format":"life-powers-backup","version":1,"databases":{}}""")
        assertNull(VaultCodec.headerProblem(good))
        val wrongFormat = Json.parseToJsonElement("""{"format":"other","version":1,"databases":{}}""")
        assertEquals(VaultCodec.NOT_A_BACKUP, VaultCodec.headerProblem(wrongFormat))
        val newer = Json.parseToJsonElement("""{"format":"life-powers-backup","version":9,"databases":{}}""")
        assertNotNull(VaultCodec.headerProblem(newer))
        val noDatabases = Json.parseToJsonElement("""{"format":"life-powers-backup","version":1}""")
        assertEquals(VaultCodec.NOT_A_BACKUP, VaultCodec.headerProblem(noDatabases))
        assertEquals(VaultCodec.NOT_A_BACKUP, VaultCodec.headerProblem(Json.parseToJsonElement("[1,2]")))
    }

    @Test
    fun settingsRoundTrip() {
        val values = listOf(true, 7, 9_000_000_000L, 1.5f, 2.25, "Portland", setOf("b", "a"))
        for (v in values) {
            val encoded: JsonObject = assertNotNull(VaultCodec.encodeSetting(v))
            val back = assertNotNull(VaultCodec.decodeSetting(Json.parseToJsonElement(encoded.toString())))
            assertEquals(v, back.value)
        }
        assertNull(VaultCodec.encodeSetting(Any()))
        assertNull(VaultCodec.decodeSetting(Json.parseToJsonElement("""{"type":"int","value":"many"}""")))
    }

    @Test
    fun csvQuotesOnlyWhenNeeded() {
        assertEquals("plain", VaultCodec.csvField("plain"))
        assertEquals("", VaultCodec.csvField(null))
        assertEquals("\"a,b\"", VaultCodec.csvField("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", VaultCodec.csvField("say \"hi\""))
        assertEquals("\"two\nlines\"", VaultCodec.csvField("two\nlines"))
        assertEquals("\" padded\"", VaultCodec.csvField(" padded"))
        assertEquals("a,,\"b,c\"\r\n", VaultCodec.csvLine(listOf("a", null, "b,c")))
    }

    @Test
    fun localTimeUsesRecordedOffset() {
        // 2026-10-17T14:30Z at UTC-7 is 07:30 local.
        val utc = java.time.Instant.parse("2026-10-17T14:30:00Z").toEpochMilli()
        assertEquals("2026-10-17 07:30", VaultCodec.localTime(utc, -420))
        assertEquals("", VaultCodec.localTime(null, 0))
    }

    @Test
    fun namesAndConfirmation() {
        assertEquals("life-powers-2026-10-17.lpbackup", VaultCodec.backupFileName(LocalDate.of(2026, 10, 17)))
        assertEquals("life-powers-2026-10-17.zip", VaultCodec.exportFileName(LocalDate.of(2026, 10, 17)))
        assertTrue(VaultCodec.deleteConfirmed(" delete "))
        assertTrue(VaultCodec.deleteConfirmed("Delete"))
        assertFalse(VaultCodec.deleteConfirmed("delet"))
        assertFalse(VaultCodec.deleteConfirmed(""))
    }

    @Test
    fun summaryLeavesOutZeros() {
        assertEquals("1 goal, 2 journal entries, 1 breathing session", VaultCodec.summaryLine(1, 2, 0, 0, 0, 1, 0))
        assertEquals("nothing recorded yet", VaultCodec.summaryLine(0, 0, 0, 0, 0, 0, 0))
    }
}
