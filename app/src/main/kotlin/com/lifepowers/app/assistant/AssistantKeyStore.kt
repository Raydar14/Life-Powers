package com.lifepowers.app.assistant

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The person's Claude API key, kept in EncryptedSharedPreferences ("assistant_secrets") under an
 * AES256_GCM master key in the Android keystore. The key is read only to make a confirmed call and is
 * never logged, shown, or included in backups. Call these off the main thread.
 */
@Singleton
class AssistantKeyStore @Inject constructor(@ApplicationContext private val context: Context) {

    @Volatile private var prefs: SharedPreferences? = null

    private fun prefs(): SharedPreferences {
        prefs?.let { return it }
        synchronized(this) {
            prefs?.let { return it }
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val created = EncryptedSharedPreferences.create(
                context,
                FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            prefs = created
            return created
        }
    }

    fun read(): String? = runCatching { prefs().getString(KEY, null) }.getOrNull()?.takeIf { it.isNotBlank() }

    /** Stores the trimmed key, or removes it for null or blank. Returns false when the keystore refused. */
    fun write(key: String?): Boolean = runCatching {
        val trimmed = key?.trim().orEmpty()
        val editor = prefs().edit()
        if (trimmed.isEmpty()) editor.remove(KEY) else editor.putString(KEY, trimmed)
        editor.commit()
    }.getOrDefault(false)

    companion object {
        const val FILE = "assistant_secrets"
        private const val KEY = "anthropic_api_key"
    }
}
