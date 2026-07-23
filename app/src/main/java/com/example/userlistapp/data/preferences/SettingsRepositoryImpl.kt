package com.example.userlistapp.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.userlistapp.domain.model.AppSettings
import com.example.userlistapp.domain.model.ThemeMode
import com.example.userlistapp.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepositoryImpl(private val dataStore: DataStore<Preferences>) : SettingsRepository {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val backgroundSync = booleanPreferencesKey("background_sync")
        val lastSync = longPreferencesKey("last_sync")
    }

    override val settings: Flow<AppSettings> = dataStore.data
        .catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }
        .map { preferences ->
            AppSettings(
                themeMode = preferences[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
                backgroundSyncEnabled = preferences[Keys.backgroundSync] ?: true,
                lastSuccessfulSync = preferences[Keys.lastSync],
            )
        }

    override suspend fun setTheme(mode: ThemeMode) { dataStore.edit { it[Keys.theme] = mode.name } }
    override suspend fun setBackgroundSync(enabled: Boolean) { dataStore.edit { it[Keys.backgroundSync] = enabled } }
    override suspend fun setLastSuccessfulSync(timestamp: Long) { dataStore.edit { it[Keys.lastSync] = timestamp } }
}
