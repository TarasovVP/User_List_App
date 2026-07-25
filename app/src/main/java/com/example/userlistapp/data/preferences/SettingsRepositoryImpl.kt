package com.example.userlistapp.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.userlistapp.domain.model.AppSettings
import com.example.userlistapp.domain.model.ThemeMode
import com.example.userlistapp.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

class SettingsRepositoryImpl(private val dataStore: DataStore<Preferences>) : SettingsRepository {
    private object Keys {
        val theme = stringPreferencesKey(THEME_KEY)
        val backgroundSync = booleanPreferencesKey(BACKGROUND_SYNC_KEY)
        val lastSync = longPreferencesKey(LAST_SYNC_KEY)
    }

    override val settings: Flow<AppSettings> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            AppSettings(
                themeMode = preferences[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                    ?: ThemeMode.SYSTEM,
                backgroundSyncEnabled = preferences[Keys.backgroundSync] ?: true,
                lastSuccessfulSync = preferences[Keys.lastSync],
            )
        }

    override suspend fun setTheme(mode: ThemeMode) {
        dataStore.edit { it[Keys.theme] = mode.name }
    }

    override suspend fun setBackgroundSync(enabled: Boolean) {
        dataStore.edit { it[Keys.backgroundSync] = enabled }
    }

    override suspend fun setLastSuccessfulSync(timestamp: Long) {
        dataStore.edit { it[Keys.lastSync] = timestamp }
    }
}

private const val THEME_KEY = "theme"
private const val BACKGROUND_SYNC_KEY = "background_sync"
private const val LAST_SYNC_KEY = "last_sync"
