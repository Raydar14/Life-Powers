package com.lifepowers.app.vault

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64

/** One SQLite cell, typed the way the cursor reported it. */
sealed interface SqlValue {
    data object Null : SqlValue
    data class Integer(val value: Long) : SqlValue
    data class Real(val value: Double) : SqlValue
    data class Text(val value: String) : SqlValue
    class Blob(val bytes: ByteArray) : SqlValue {
        override fun equals(other: Any?): Boolean = other is Blob && other.bytes.contentEquals(bytes)
        override fun hashCode(): Int = bytes.contentHashCode()
    }
}

/** A preference value in a form that survives JSON and can be put back with the right key type. */
data class SettingValue(val type: String, val value: Any)

/**
 * The pure parts of backup, restore, and export: no Android types, so they run in plain JUnit.
 *
 * Backup JSON holds "format" ("life-powers-backup"), "version" (1), "createdUtc", "databases" with
 * "main" and "personal" each mapping a table name to a list of rows, "photos" as a list of file name
 * and base64 bytes, and "settings" mapping a name to its type and value.
 * A row is an object of column name to value. Integers and reals are JSON numbers, text is a JSON
 * string, null is null, and a blob is {"b64": base64}.
 */
object VaultCodec {
    const val FORMAT = "life-powers-backup"
    const val VERSION = 1
    const val DB_MAIN = "main"
    const val DB_PERSONAL = "personal"
    private const val BLOB_KEY = "b64"

    /** Tables Room and SQLite keep for themselves. They are never dumped, cleared, or restored. */
    private val internalTables = setOf("android_metadata", "sqlite_sequence", "room_master_table")

    fun isUserTable(name: String): Boolean =
        name.isNotBlank() && name !in internalTables && !name.startsWith("sqlite_") && !name.startsWith("room_")

    /**
     * Preference names that may hold a secret. They are never written to a backup, never replaced by
     * a restore, and only removed by delete everything.
     */
    fun isSecretPreference(name: String): Boolean {
        val n = name.lowercase()
        return n == "oura_token" || "key" in n || "token" in n || "secret" in n || "passphrase" in n || "password" in n
    }

    /** The EncryptedSharedPreferences file that holds the database passphrase (see data/DatabasePassphrase). */
    const val PASSPHRASE_PREFS = "life_powers_keys"

    /**
     * Whether delete everything clears a shared preferences file. The passphrase file stays, since the
     * database files stay and are only emptied. Library files stay too: the EncryptedFile keysets hold no
     * personal data, and WorkManager and WebView keep their own state.
     */
    fun shouldClearPreferenceFile(name: String): Boolean = name.isNotBlank() &&
        name != PASSPHRASE_PREFS &&
        !name.startsWith("__androidx_security_crypto_encrypted_file") &&
        !name.startsWith("androidx.") &&
        !name.startsWith("com.google.") &&
        !name.startsWith("WebView")

    // Cells

    fun toJson(v: SqlValue): JsonElement = when (v) {
        SqlValue.Null -> JsonNull
        is SqlValue.Integer -> JsonPrimitive(v.value)
        is SqlValue.Real -> if (v.value.isFinite()) JsonPrimitive(v.value) else JsonNull
        is SqlValue.Text -> JsonPrimitive(v.value)
        is SqlValue.Blob -> buildJsonObject { put(BLOB_KEY, Base64.getEncoder().encodeToString(v.bytes)) }
    }

    /** The cell a JSON value stands for, or null when it is not something a backup writes. */
    fun fromJson(e: JsonElement): SqlValue? = when (e) {
        is JsonNull -> SqlValue.Null
        is JsonPrimitive -> when {
            e.isString -> SqlValue.Text(e.content)
            e.content == "true" -> SqlValue.Integer(1)
            e.content == "false" -> SqlValue.Integer(0)
            else -> e.longOrNull?.let { SqlValue.Integer(it) } ?: e.doubleOrNull?.let { SqlValue.Real(it) }
        }
        is JsonObject -> (e[BLOB_KEY] as? JsonPrimitive)?.takeIf { it.isString }?.let {
            runCatching { SqlValue.Blob(Base64.getDecoder().decode(it.content)) }.getOrNull()
        }
        is JsonArray -> null
    }

    // Header

    /** A plain sentence when [root] is not a backup this version can read, or null when it is. */
    fun headerProblem(root: JsonElement): String? {
        val obj = root as? JsonObject ?: return NOT_A_BACKUP
        val format = (obj["format"] as? JsonPrimitive)?.contentOrNull
        if (format != FORMAT) return NOT_A_BACKUP
        val version = (obj["version"] as? JsonPrimitive)?.intOrNull ?: return NOT_A_BACKUP
        if (version > VERSION) return "This backup was made by a newer version of the app. Update the app, then restore."
        if (version < 1) return NOT_A_BACKUP
        if (obj["databases"] !is JsonObject) return NOT_A_BACKUP
        return null
    }

    const val NOT_A_BACKUP = "This file is not a Life Powers backup."

    // Settings

    /** A DataStore value as a type name and a JSON value, or null for a type the backup does not carry. */
    fun encodeSetting(value: Any): JsonObject? {
        val (type, json) = when (value) {
            is Boolean -> "boolean" to JsonPrimitive(value.toString())
            is Int -> "int" to JsonPrimitive(value.toString())
            is Long -> "long" to JsonPrimitive(value.toString())
            is Float -> "float" to JsonPrimitive(value.toString())
            is Double -> "double" to JsonPrimitive(value.toString())
            is String -> "string" to JsonPrimitive(value)
            is Set<*> -> "string_set" to JsonArray(value.filterIsInstance<String>().sorted().map { JsonPrimitive(it) })
            else -> return null
        }
        return JsonObject(mapOf("type" to JsonPrimitive(type), "value" to json))
    }

    fun decodeSetting(e: JsonElement): SettingValue? {
        val obj = e as? JsonObject ?: return null
        val type = (obj["type"] as? JsonPrimitive)?.contentOrNull ?: return null
        val raw = obj["value"] ?: return null
        if (type == "string_set") {
            val arr = raw as? JsonArray ?: return null
            return SettingValue(type, arr.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }.toSet())
        }
        val text = (raw as? JsonPrimitive)?.contentOrNull ?: return null
        val value: Any = when (type) {
            "boolean" -> text.toBooleanStrictOrNull()
            "int" -> text.toIntOrNull()
            "long" -> text.toLongOrNull()
            "float" -> text.toFloatOrNull()
            "double" -> text.toDoubleOrNull()
            "string" -> text
            else -> null
        } ?: return null
        return SettingValue(type, value)
    }

    // CSV

    /** One CSV field, quoted when it holds a comma, a quote, a line break, or edge spaces. */
    fun csvField(s: String?): String {
        if (s == null) return ""
        val needs = s.any { it == ',' || it == '"' || it == '\n' || it == '\r' } || s.startsWith(" ") || s.endsWith(" ")
        return if (needs) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }

    /** A CSV line with CRLF, as RFC 4180 and spreadsheets expect. */
    fun csvLine(fields: List<String?>): String = fields.joinToString(",") { csvField(it) } + "\r\n"

    fun csvCell(v: SqlValue): String = when (v) {
        SqlValue.Null -> ""
        is SqlValue.Integer -> v.value.toString()
        is SqlValue.Real -> if (v.value.isFinite()) v.value.toString() else ""
        is SqlValue.Text -> v.value
        is SqlValue.Blob -> Base64.getEncoder().encodeToString(v.bytes)
    }

    private val localStamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    /** "2026-10-17 07:30" in the zone the moment was recorded in, or empty when unknown. */
    fun localTime(utcMs: Long?, zoneOffsetMin: Int?): String {
        if (utcMs == null) return ""
        val offset = ZoneOffset.ofTotalSeconds(((zoneOffsetMin ?: 0).coerceIn(-18 * 60, 18 * 60)) * 60)
        return Instant.ofEpochMilli(utcMs).atOffset(offset).toLocalDateTime().format(localStamp)
    }

    // Names and confirmations

    fun backupFileName(date: LocalDate): String = "life-powers-$date.lpbackup"
    fun exportFileName(date: LocalDate): String = "life-powers-$date.zip"

    /** Delete everything goes ahead only once the person has typed the word. */
    fun deleteConfirmed(typed: String): Boolean = typed.trim().equals("delete", ignoreCase = true)

    /** Count of rows in [table], from a map of table name to row count. */
    fun count(counts: Map<String, Int>, table: String): Int = counts[table] ?: 0

    /** "12 goals, 30 journal entries, ..." with zero counts left out. */
    fun summaryLine(goals: Int, journalEntries: Int, photos: Int, events: Int, samples: Int, breathSessions: Int, experiments: Int): String {
        val parts = listOf(
            goals to ("goal" to "goals"),
            journalEntries to ("journal entry" to "journal entries"),
            photos to ("photo" to "photos"),
            events to ("moment" to "moments"),
            samples to ("measurement" to "measurements"),
            breathSessions to ("breathing session" to "breathing sessions"),
            experiments to ("experiment" to "experiments"),
        ).filter { it.first > 0 }.map { (n, words) -> "$n ${if (n == 1) words.first else words.second}" }
        return if (parts.isEmpty()) "nothing recorded yet" else parts.joinToString(", ")
    }

    /** The numeric value of a JSON cell, for reading counts or ids from parsed rows. */
    fun longOf(e: JsonElement?): Long? = (e as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull

    fun textOf(e: JsonElement?): String? = (e as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
}
