package com.example.userlistapp.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/**
 * Property delegates keep exactly one store per file in the process, which DataStore requires.
 * An unreadable file is replaced with empty preferences instead of failing every read, which
 * would otherwise leave the application unable to start.
 */
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = SETTINGS_DATA_STORE_NAME,
    corruptionHandler = replaceCorruptedPreferences(),
)

val Context.authSessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = AUTH_SESSION_DATA_STORE_NAME,
    corruptionHandler = replaceCorruptedPreferences(),
)

/** Builds a store over an explicit file with the same corruption behaviour as the delegates. */
fun createPreferencesDataStore(
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    produceFile: () -> File,
): DataStore<Preferences> = PreferenceDataStoreFactory.create(
    corruptionHandler = replaceCorruptedPreferences(),
    scope = scope,
    produceFile = produceFile,
)

private fun replaceCorruptedPreferences() =
    ReplaceFileCorruptionHandler { emptyPreferences() }

private const val SETTINGS_DATA_STORE_NAME = "settings"
private const val AUTH_SESSION_DATA_STORE_NAME = "auth_session"
