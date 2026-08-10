package com.atomichabits.tracker.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        val SHEETS_URL = stringPreferencesKey("sheets_webapp_url")
        val AUTO_SYNC = booleanPreferencesKey("auto_sync_enabled")
    }

    val sheetsUrl: Flow<String> = context.dataStore.data.map { it[Keys.SHEETS_URL] ?: "" }
    val autoSyncEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_SYNC] ?: false }

    suspend fun setSheetsUrl(url: String) {
        context.dataStore.edit { it[Keys.SHEETS_URL] = url }
    }

    suspend fun setAutoSync(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_SYNC] = enabled }
    }
}
