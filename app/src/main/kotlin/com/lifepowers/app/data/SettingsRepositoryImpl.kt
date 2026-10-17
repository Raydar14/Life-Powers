package com.lifepowers.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lifepowers.app.domain.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val store: DataStore<Preferences>,
) : SettingsRepository {

    override val assistantMaster: Flow<Boolean> = store.data.map { it[ASSISTANT_MASTER] ?: false }
    override val keepAudio: Flow<Boolean> = store.data.map { it[KEEP_AUDIO] ?: false }
    override val ouraToken: Flow<String?> = store.data.map { it[OURA_TOKEN] }

    override suspend fun setAssistantMaster(on: Boolean) {
        store.edit { it[ASSISTANT_MASTER] = on }
    }

    override suspend fun setKeepAudio(on: Boolean) {
        store.edit { it[KEEP_AUDIO] = on }
    }

    override suspend fun setOuraToken(token: String?) {
        store.edit { prefs ->
            if (token.isNullOrBlank()) prefs.remove(OURA_TOKEN) else prefs[OURA_TOKEN] = token.trim()
        }
    }

    companion object {
        val ASSISTANT_MASTER = booleanPreferencesKey("assistant_master")
        val KEEP_AUDIO = booleanPreferencesKey("keep_audio")
        val OURA_TOKEN = stringPreferencesKey("oura_token")
    }
}
