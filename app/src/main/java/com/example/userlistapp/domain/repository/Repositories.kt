package com.example.userlistapp.domain.repository

import com.example.userlistapp.core.common.AppResult
import com.example.userlistapp.domain.model.AppSettings
import com.example.userlistapp.domain.model.SyncState
import com.example.userlistapp.domain.model.ThemeMode
import com.example.userlistapp.domain.model.User
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun observeUsers(): Flow<List<User>>
    fun observeUser(userId: Int): Flow<User?>
    suspend fun refreshUsers(): AppResult<Unit>
    suspend fun setFavorite(userId: Int, favorite: Boolean): AppResult<Unit>
    suspend fun saveNote(userId: Int, note: String): AppResult<Unit>
    suspend fun deleteNote(userId: Int): AppResult<Unit>
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setTheme(mode: ThemeMode)
    suspend fun setBackgroundSync(enabled: Boolean)
    suspend fun setLastSuccessfulSync(timestamp: Long)
}

interface SyncScheduler {
    fun observeState(): Flow<SyncState>
    fun setEnabled(enabled: Boolean)
}
