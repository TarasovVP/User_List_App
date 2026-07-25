package com.example.userlistapp.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/**
 * An unreadable preferences file is replaced with empty preferences instead of failing every
 * read, which would otherwise make the application unable to start.
 */
fun createPreferencesDataStore(
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    produceFile: () -> File,
): DataStore<Preferences> = PreferenceDataStoreFactory.create(
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
    scope = scope,
    produceFile = produceFile,
)

fun createPreferencesDataStore(context: Context, name: String): DataStore<Preferences> =
    createPreferencesDataStore { context.preferencesDataStoreFile(name) }

const val SETTINGS_DATA_STORE_NAME = "settings"
const val AUTH_SESSION_DATA_STORE_NAME = "auth_session"
