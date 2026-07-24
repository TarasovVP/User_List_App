package com.example.userlistapp.domain.repository

import com.example.userlistapp.core.common.AppResult
import com.example.userlistapp.domain.model.Account
import com.example.userlistapp.domain.model.AppSettings
import com.example.userlistapp.domain.model.SessionState
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

/**
 * A deliberately simulated session: only a DummyJSON user id and an optional local
 * avatar URI are persisted. It is not a production authentication/token store.
 */
interface AuthSessionRepository {
    val sessionState: Flow<SessionState>
    val localAvatarUri: Flow<String?>
    suspend fun signIn(username: String, password: String): AppResult<Account>
    suspend fun loadAccount(userId: Int): AppResult<Account>
    suspend fun signOut(): AppResult<Unit>
    suspend fun setLocalAvatar(uri: String?): AppResult<Unit>
}
