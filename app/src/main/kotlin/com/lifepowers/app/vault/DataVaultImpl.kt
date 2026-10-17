package com.lifepowers.app.vault

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.work.WorkManager
import com.lifepowers.app.data.LifePowersDatabase
import com.lifepowers.app.data.personal.PersonalDatabase
import com.lifepowers.app.data.personal.PhotoVault
import com.lifepowers.app.domain.BackupSummary
import com.lifepowers.app.domain.DataResult
import com.lifepowers.app.domain.DataVault
import com.lifepowers.app.domain.ReminderScheduler
import com.lifepowers.core.BackupCrypto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backup, restore, spreadsheet export, and delete everything, over both encrypted Room databases.
 *
 * Tables are read and written generically through each database's open SupportSQLiteDatabase, so a
 * table added in a later version is carried without changes here. A restore skips tables and columns
 * this version does not have. Secrets (the Oura token, the assistant key, the database passphrase)
 * never enter a backup. One operation runs at a time.
 */
@Singleton
class DataVaultImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mainDb: LifePowersDatabase,
    private val personalDb: PersonalDatabase,
    private val photoVault: PhotoVault,
    private val store: DataStore<Preferences>,
    private val reminderScheduler: ReminderScheduler,
) : DataVault {

    private val mutex = Mutex()

    override suspend fun backup(out: OutputStream, password: CharArray, includePhotos: Boolean): DataResult =
        guarded("The backup could not be written. Nothing on the phone changed.") { doBackup(out, password, includePhotos) }

    override suspend fun restore(input: InputStream, password: CharArray): DataResult =
        guarded("The backup could not be restored.") { doRestore(input, password) }

    override suspend fun exportCsv(out: OutputStream): DataResult =
        guarded("The spreadsheet files could not be written.") { doExport(out) }

    override suspend fun deleteEverything(): DataResult =
        guarded("Something could not be deleted. Try again, or remove the app from the phone's settings.") { doDelete() }

    /** Runs [block] alone, on the IO dispatcher, turning every failure into a plain sentence. */
    private suspend fun guarded(failure: String, block: suspend () -> DataResult): DataResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupCrypto.WrongPasswordException) {
                DataResult.Failed(e.message ?: "The password does not open this backup.")
            } catch (e: BackupCrypto.NotABackupException) {
                DataResult.Failed(e.message ?: VaultCodec.NOT_A_BACKUP)
            } catch (e: OutOfMemoryError) {
                DataResult.Failed("This is too large for the phone to hold at once. A backup without photos is much smaller.")
            } catch (e: Exception) {
                DataResult.Failed(failure)
            }
        }
    }

    // Backup

    private suspend fun doBackup(out: OutputStream, password: CharArray, includePhotos: Boolean): DataResult {
        BackupCrypto.passwordProblem(String(password))?.let { return DataResult.Failed(it) }

        val plain = ByteArrayOutputStream(1 shl 20)
        val w = BufferedWriter(OutputStreamWriter(plain, Charsets.UTF_8), 64 * 1024)
        w.write("{")
        w.key("format"); w.write(JsonPrimitive(VaultCodec.FORMAT).toString()); w.write(",")
        w.key("version"); w.write(VaultCodec.VERSION.toString()); w.write(",")
        w.key("createdUtc"); w.write(System.currentTimeMillis().toString()); w.write(",")

        w.key("databases"); w.write("{")
        w.key(VaultCodec.DB_MAIN)
        val main = dumpDatabase(mainDb, w)
        w.write(",")
        w.key(VaultCodec.DB_PERSONAL)
        val personal = dumpDatabase(personalDb, w)
        w.write("},")

        w.key("photos"); w.write("[")
        var photoCount = 0
        if (includePhotos) {
            for (name in photoFileNames()) {
                val bytes = photoVault.load(name) ?: continue
                if (photoCount > 0) w.write(",")
                val photo = JsonObject(
                    mapOf(
                        "fileName" to JsonPrimitive(name),
                        "bytesBase64" to JsonPrimitive(Base64.getEncoder().encodeToString(bytes)),
                    )
                )
                w.write(photo.toString())
                photoCount++
            }
        }
        w.write("],")

        w.key("settings"); w.write(JsonObject(settingsForBackup()).toString())
        w.write("}")
        w.flush()

        val bytes = plain.toByteArray()
        plain.reset()
        val sealed = try {
            BackupCrypto.encrypt(bytes, password)
        } finally {
            bytes.fill(0)
        }
        out.write(sealed)
        out.flush()

        val summary = summaryOf(main, personal, photoCount)
        return DataResult.Done(summary, "Backup saved: ${summaryText(summary)}.")
    }

    /** Writes `{table:[row,...],...}` for every user table, inside one transaction for a consistent picture. */
    private suspend fun dumpDatabase(db: RoomDatabase, w: Writer): Map<String, Int> = db.withTransaction {
        val sdb = db.openHelper.writableDatabase
        val counts = LinkedHashMap<String, Int>()
        w.write("{")
        userTables(sdb).forEachIndexed { i, table ->
            if (i > 0) w.write(",")
            w.key(table)
            w.write("[")
            var n = 0
            sdb.query("SELECT * FROM ${quote(table)}").use { c ->
                val names = c.columnNames
                while (c.moveToNext()) {
                    if (n > 0) w.write(",")
                    val row = LinkedHashMap<String, JsonElement>(names.size * 2)
                    for (col in names.indices) row[names[col]] = VaultCodec.toJson(cell(c, col))
                    w.write(JsonObject(row).toString())
                    n++
                }
            }
            w.write("]")
            counts[table] = n
        }
        w.write("}")
        counts
    }

    private fun photoFileNames(): List<String> {
        val sdb = personalDb.openHelper.writableDatabase
        if ("photo" !in userTables(sdb)) return emptyList()
        return sdb.query("SELECT DISTINCT file_name FROM photo WHERE file_name IS NOT NULL").use { c ->
            buildList { while (c.moveToNext()) c.getString(0)?.let { add(it) } }
        }
    }

    private suspend fun settingsForBackup(): Map<String, JsonElement> {
        val prefs = runCatching { store.data.first() }.getOrNull() ?: return emptyMap()
        val out = LinkedHashMap<String, JsonElement>()
        for ((key, value) in prefs.asMap()) {
            if (VaultCodec.isSecretPreference(key.name)) continue
            VaultCodec.encodeSetting(value)?.let { out[key.name] = it }
        }
        return out
    }

    // Restore

    private suspend fun doRestore(input: InputStream, password: CharArray): DataResult {
        val file = input.readBytes()
        val plain = BackupCrypto.decrypt(file, password)
        val root = try {
            Json.parseToJsonElement(String(plain, Charsets.UTF_8))
        } catch (e: Exception) {
            return DataResult.Failed(VaultCodec.NOT_A_BACKUP)
        } finally {
            plain.fill(0)
        }
        VaultCodec.headerProblem(root)?.let { return DataResult.Failed(it) }
        val obj = root as JsonObject
        val databases = obj["databases"] as JsonObject
        val empty = JsonObject(emptyMap())

        val main = restoreDatabase(mainDb, databases[VaultCodec.DB_MAIN] as? JsonObject ?: empty)
        val personal = restoreDatabase(personalDb, databases[VaultCodec.DB_PERSONAL] as? JsonObject ?: empty)

        val photos = restorePhotos(obj["photos"] as? JsonArray ?: JsonArray(emptyList()))
        runCatching { removeUnreferencedPhotoFiles() }

        (obj["settings"] as? JsonObject)?.let { restoreSettings(it) }

        // Goal reminders live in alarms, not in the database, so arm them for the restored goals.
        runCatching { reminderScheduler.rescheduleAll() }

        val summary = summaryOf(main, personal, photos)
        return DataResult.Done(summary, "Restored ${summaryText(summary)}.")
    }

    /**
     * Empties every user table of [db] and fills it from [tables], in one transaction. Tables this
     * version does not have, and columns a table does not have, are skipped. A row the schema refuses
     * is skipped rather than failing the whole restore.
     */
    private suspend fun restoreDatabase(db: RoomDatabase, tables: JsonObject): Map<String, Int> = db.withTransaction {
        val sdb = db.openHelper.writableDatabase
        val existing = userTables(sdb)
        for (table in existing) sdb.execSQL("DELETE FROM ${quote(table)}")
        val counts = LinkedHashMap<String, Int>()
        for (table in existing) {
            val rows = tables[table] as? JsonArray ?: continue
            val columns = columnsOf(sdb, table)
            var n = 0
            for (row in rows) {
                val values = contentValuesOf(row as? JsonObject ?: continue, columns)
                if (values.size() == 0) continue
                val id = try {
                    sdb.insert(table, SQLiteDatabase.CONFLICT_REPLACE, values)
                } catch (e: SQLException) {
                    -1L
                }
                if (id != -1L) n++
            }
            counts[table] = n
        }
        counts
    }

    private fun contentValuesOf(row: JsonObject, columns: Set<String>): ContentValues {
        val cv = ContentValues(row.size)
        for ((column, json) in row) {
            if (column !in columns) continue
            when (val v = VaultCodec.fromJson(json)) {
                null -> Unit
                SqlValue.Null -> cv.putNull(column)
                is SqlValue.Integer -> cv.put(column, v.value)
                is SqlValue.Real -> cv.put(column, v.value)
                is SqlValue.Text -> cv.put(column, v.value)
                is SqlValue.Blob -> cv.put(column, v.bytes)
            }
        }
        return cv
    }

    /**
     * Stores each photo again through [PhotoVault], encrypted under this phone's key. PhotoVault picks
     * a fresh file name, so the photo rows are pointed at the new names.
     */
    private suspend fun restorePhotos(photos: JsonArray): Int {
        if (photos.isEmpty()) return 0
        val sdb = personalDb.openHelper.writableDatabase
        if ("photo" !in userTables(sdb)) return 0
        val mimes = sdb.query("SELECT file_name, mime FROM photo").use { c ->
            buildMap<String, String> {
                while (c.moveToNext()) {
                    val name = c.getString(0) ?: continue
                    put(name, c.getString(1) ?: "image/jpeg")
                }
            }
        }
        val renames = ArrayList<Triple<String, String, String>>()
        for (p in photos) {
            val o = p as? JsonObject ?: continue
            val oldName = VaultCodec.textOf(o["fileName"]) ?: continue
            val b64 = VaultCodec.textOf(o["bytesBase64"]) ?: continue
            val bytes = runCatching { Base64.getDecoder().decode(b64) }.getOrNull() ?: continue
            val stored = try {
                photoVault.store(bytes, mimes[oldName] ?: "image/jpeg")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                continue
            }
            renames += Triple(oldName, stored.fileName, stored.mime)
        }
        if (renames.isNotEmpty()) {
            personalDb.withTransaction {
                val db = personalDb.openHelper.writableDatabase
                for ((oldName, newName, mime) in renames) {
                    db.execSQL("UPDATE photo SET file_name = ?, mime = ? WHERE file_name = ?", arrayOf<Any?>(newName, mime, oldName))
                }
            }
        }
        return renames.size
    }

    /** Deletes photo files no photo row points at, such as the ones the restore replaced. */
    private fun removeUnreferencedPhotoFiles() {
        val dir = File(context.filesDir, PhotoVault.DIR)
        val files = dir.listFiles() ?: return
        val referenced = photoFileNames().map { File(it).name }.toSet()
        for (f in files) if (f.isFile && f.name !in referenced) f.delete()
    }

    /** Replaces every non secret preference with the backup's. Secrets on this phone stay as they are. */
    private suspend fun restoreSettings(settings: JsonObject) {
        store.edit { prefs ->
            for (key in prefs.asMap().keys.toList()) {
                if (VaultCodec.isSecretPreference(key.name)) continue
                @Suppress("UNCHECKED_CAST")
                prefs.remove(key as Preferences.Key<Any>)
            }
            for ((name, json) in settings) {
                if (VaultCodec.isSecretPreference(name)) continue
                val s = VaultCodec.decodeSetting(json) ?: continue
                when (s.type) {
                    "boolean" -> prefs[booleanPreferencesKey(name)] = s.value as Boolean
                    "int" -> prefs[intPreferencesKey(name)] = s.value as Int
                    "long" -> prefs[longPreferencesKey(name)] = s.value as Long
                    "float" -> prefs[floatPreferencesKey(name)] = s.value as Float
                    "double" -> prefs[doublePreferencesKey(name)] = s.value as Double
                    "string" -> prefs[stringPreferencesKey(name)] = s.value as String
                    "string_set" -> prefs[stringSetPreferencesKey(name)] = (s.value as Set<*>).filterIsInstance<String>().toSet()
                }
            }
        }
    }

    // Export

    private class CsvSpec(
        val file: String,
        val db: SupportSQLiteDatabase,
        val table: String,
        val sql: String,
        /** Explicit header and row mapping, or null to write every selected column as it is. */
        val header: List<String>? = null,
        val map: ((Map<String, SqlValue>) -> List<String?>)? = null,
    )

    private fun doExport(out: OutputStream): DataResult {
        val main = mainDb.openHelper.writableDatabase
        val personal = personalDb.openHelper.writableDatabase
        val mainTables = userTables(main).toSet()
        val personalTables = userTables(personal).toSet()

        val specs = listOf(
            CsvSpec(
                "daily_metrics.csv", main, "daily_metric",
                "SELECT local_date, metric, agg, value, n FROM daily_metric ORDER BY local_date, metric, agg",
            ),
            CsvSpec(
                "events.csv", main, "behavior_event",
                "SELECT * FROM behavior_event ORDER BY start_utc",
                header = listOf("kind", "start", "end", "quantity", "unit", "note", "source"),
                map = { r ->
                    val zone = r.long("zone_offset_min")?.toInt()
                    listOf(
                        r.text("kind"),
                        VaultCodec.localTime(r.long("start_utc"), zone),
                        VaultCodec.localTime(r.long("end_utc"), zone),
                        r.text("quantity"), r.text("unit"), r.text("note"), r.text("source"),
                    )
                },
            ),
            CsvSpec("goals.csv", personal, "goal", "SELECT * FROM goal ORDER BY id"),
            CsvSpec(
                "habit_checks.csv", personal, "habit_check",
                "SELECT h.local_date AS local_date, h.goal_id AS goal_id, g.name AS goal, h.amount AS amount, " +
                    "h.source AS source, h.note AS note FROM habit_check h LEFT JOIN goal g ON g.id = h.goal_id " +
                    "ORDER BY h.local_date, h.created_utc",
            ),
            CsvSpec(
                "breath_sessions.csv", personal, "breath_session",
                "SELECT * FROM breath_session ORDER BY start_utc",
                header = listOf(
                    "start", "end", "minutes", "pattern", "inhale_s", "hold_in_s", "exhale_s", "hold_out_s", "sensor",
                    "start_bpm", "end_bpm", "start_rmssd", "end_rmssd", "best_swing", "mean_sync", "readings",
                ),
                map = { r ->
                    val zone = r.long("zone_offset_min")?.toInt()
                    val start = r.long("start_utc")
                    val end = r.long("end_utc")
                    val minutes = if (start != null && end != null) ((end - start) / 600.0).let { Math.round(it) / 100.0 }.toString() else null
                    listOf(
                        VaultCodec.localTime(start, zone), VaultCodec.localTime(end, zone), minutes, r.text("pattern_name"),
                        r.text("inhale_s"), r.text("hold_in_s"), r.text("exhale_s"), r.text("hold_out_s"), r.text("sensor"),
                        r.text("start_bpm"), r.text("end_bpm"), r.text("start_rmssd"), r.text("end_rmssd"),
                        r.text("best_swing"), r.text("mean_sync"), r.text("readings"),
                    )
                },
            ),
            CsvSpec("experiments.csv", personal, "experiment", "SELECT * FROM experiment ORDER BY started_on"),
            CsvSpec("experiment_days.csv", personal, "experiment_day", "SELECT * FROM experiment_day ORDER BY experiment_id, local_date"),
        )

        val zip = ZipOutputStream(out)
        var written = 0
        for (spec in specs) {
            val present = if (spec.db === main) spec.table in mainTables else spec.table in personalTables
            if (!present) continue
            writeCsv(zip, spec)
            written++
        }
        zip.finish()
        out.flush()
        return DataResult.Done(null, "Saved $written spreadsheet files in one zip.")
    }

    private fun writeCsv(zip: ZipOutputStream, spec: CsvSpec) {
        zip.putNextEntry(ZipEntry(spec.file))
        // Not closed: closing the writer would close the zip. Flushed before the entry closes.
        val w = BufferedWriter(OutputStreamWriter(zip, Charsets.UTF_8), 32 * 1024)
        w.write("﻿") // so spreadsheet apps read the file as UTF-8
        spec.db.query(spec.sql).use { c ->
            val names = c.columnNames
            val map = spec.map
            w.write(VaultCodec.csvLine(spec.header ?: names.toList()))
            while (c.moveToNext()) {
                val row = LinkedHashMap<String, SqlValue>(names.size * 2)
                for (i in names.indices) row[names[i]] = cell(c, i)
                val fields = if (map != null) map(row) else row.values.map { VaultCodec.csvCell(it) }
                w.write(VaultCodec.csvLine(fields))
            }
        }
        w.flush()
        zip.closeEntry()
    }

    private fun Map<String, SqlValue>.long(column: String): Long? = when (val v = this[column]) {
        is SqlValue.Integer -> v.value
        is SqlValue.Real -> v.value.toLong()
        else -> null
    }

    private fun Map<String, SqlValue>.text(column: String): String? = this[column]?.let { if (it == SqlValue.Null) null else VaultCodec.csvCell(it) }

    // Delete everything

    private suspend fun doDelete(): DataResult {
        mainDb.clearAllTables()
        personalDb.clearAllTables()

        // With no goals left this cancels every armed reminder alarm. It reads the armed list from
        // shared preferences, so it runs before those are cleared.
        runCatching { reminderScheduler.rescheduleAll() }
        runCatching { WorkManager.getInstance(context).cancelAllWork() }
        runCatching { NotificationManagerCompat.from(context).cancelAll() }

        val files = context.filesDir
        File(files, PhotoVault.DIR).deleteRecursively()
        File(files, AUDIO_DIR).deleteRecursively()
        File(files, PATTERNS_FILE).delete()
        File(files, "$PATTERNS_FILE.tmp").delete()
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }

        store.edit { it.clear() }
        clearSharedPreferences()

        return DataResult.Done(
            null,
            "Everything is gone from this phone: records, photos, audio, settings, keys, and tokens. Backups you saved elsewhere are untouched.",
        )
    }

    /**
     * Clears every app shared preferences file except the database passphrase and library files.
     * Encrypted files are cleared through EncryptedSharedPreferences so their keysets stay valid for
     * anything that still holds them open.
     */
    private fun clearSharedPreferences() {
        val dir = File(context.dataDir, "shared_prefs")
        val names = dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".xml") }
            ?.map { it.name.removeSuffix(".xml") }
            .orEmpty()
        for (name in names) {
            if (!VaultCodec.shouldClearPreferenceFile(name)) continue
            val raw = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            val encrypted = raw.contains(ESP_KEY_KEYSET) || raw.contains(ESP_VALUE_KEYSET)
            val cleared = encrypted && runCatching {
                EncryptedSharedPreferences.create(
                    context,
                    name,
                    masterKey(),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                ).edit().clear().commit()
            }.getOrDefault(false)
            if (!cleared) raw.edit().clear().commit()
        }
    }

    private fun masterKey(): MasterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()

    // Shared

    private fun summaryOf(main: Map<String, Int>, personal: Map<String, Int>, photos: Int) = BackupSummary(
        goals = VaultCodec.count(personal, "goal"),
        journalEntries = VaultCodec.count(personal, "journal_entry"),
        photos = photos,
        events = VaultCodec.count(main, "behavior_event"),
        samples = VaultCodec.count(main, "metric_sample"),
        breathSessions = VaultCodec.count(personal, "breath_session"),
        experiments = VaultCodec.count(personal, "experiment"),
    )

    private fun summaryText(s: BackupSummary): String =
        VaultCodec.summaryLine(s.goals, s.journalEntries, s.photos, s.events, s.samples, s.breathSessions, s.experiments)

    private fun userTables(sdb: SupportSQLiteDatabase): List<String> =
        sdb.query("SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name").use { c ->
            buildList { while (c.moveToNext()) c.getString(0)?.takeIf { VaultCodec.isUserTable(it) }?.let { add(it) } }
        }

    private fun columnsOf(sdb: SupportSQLiteDatabase, table: String): Set<String> =
        sdb.query("PRAGMA table_info(${quote(table)})").use { c ->
            val nameIndex = c.getColumnIndex("name")
            buildSet { while (c.moveToNext()) if (nameIndex >= 0) c.getString(nameIndex)?.let { add(it) } }
        }

    private fun cell(c: Cursor, i: Int): SqlValue = when (c.getType(i)) {
        Cursor.FIELD_TYPE_NULL -> SqlValue.Null
        Cursor.FIELD_TYPE_INTEGER -> SqlValue.Integer(c.getLong(i))
        Cursor.FIELD_TYPE_FLOAT -> SqlValue.Real(c.getDouble(i))
        Cursor.FIELD_TYPE_BLOB -> SqlValue.Blob(c.getBlob(i) ?: ByteArray(0))
        else -> SqlValue.Text(c.getString(i) ?: "")
    }

    private fun quote(identifier: String): String = "`" + identifier.replace("`", "``") + "`"

    private fun Writer.key(name: String) {
        write(JsonPrimitive(name).toString())
        write(":")
    }

    private companion object {
        const val AUDIO_DIR = "audio"
        const val PATTERNS_FILE = "patterns.json"
        const val ESP_KEY_KEYSET = "__androidx_security_crypto_encrypted_prefs_key_keyset__"
        const val ESP_VALUE_KEYSET = "__androidx_security_crypto_encrypted_prefs_value_keyset__"
    }
}
