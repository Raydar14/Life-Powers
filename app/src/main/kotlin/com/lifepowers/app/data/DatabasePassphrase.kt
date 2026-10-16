package com.lifepowers.app.data

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * A random 32 byte SQLCipher passphrase, generated once and kept in EncryptedSharedPreferences
 * under an AES256_GCM master key in the Android keystore.
 */
object DatabasePassphrase {
    private const val PREFS = "life_powers_keys"
    private const val KEY = "db_passphrase"

    fun getOrCreate(context: Context): ByteArray {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val prefs = EncryptedSharedPreferences.create(
            context,
            PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        val existing = prefs.getString(KEY, null)
        if (existing != null) return Base64.decode(existing, Base64.NO_WRAP)
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        prefs.edit().putString(KEY, Base64.encodeToString(bytes, Base64.NO_WRAP)).apply()
        return bytes
    }
}
