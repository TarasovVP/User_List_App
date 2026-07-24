package com.example.userlistapp.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.userlistapp.core.common.AppError
import com.example.userlistapp.core.common.AppResult
import com.example.userlistapp.core.common.IoDispatcher
import com.example.userlistapp.data.remote.AccountDto
import com.example.userlistapp.data.remote.AuthApi
import com.example.userlistapp.data.remote.LoginRequestDto
import com.example.userlistapp.domain.model.Account
import com.example.userlistapp.domain.model.SessionState
import com.example.userlistapp.domain.repository.AuthSessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

val Context.authSessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_session")

class AuthSessionRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
    private val api: AuthApi,
    private val avatarStorage: LocalAvatarStorage,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AuthSessionRepository {
    private object Keys {
        val authenticatedUserId = intPreferencesKey("simulated_authenticated_user_id")
        val localAccountAvatarUri = stringPreferencesKey("local_account_avatar_uri")
    }

    override val sessionState: Flow<SessionState> = dataStore.data
        .catch { error ->
            if (error is CancellationException) throw error
            emit(emptyPreferences())
        }
        .map { it[Keys.authenticatedUserId]?.let(SessionState::SignedIn) ?: SessionState.SignedOut }

    override val localAvatarUri: Flow<String?> = dataStore.data
        .catch { error ->
            if (error is CancellationException) throw error
            emit(emptyPreferences())
        }
        .map { it[Keys.localAccountAvatarUri] }

    override suspend fun signIn(
        username: String,
        password: String,
    ): AppResult<Account> = withContext(ioDispatcher) {
        try {
            val account = api.login(LoginRequestDto(username.trim(), password)).toDomain()
            if (account.id <= 0) return@withContext AppResult.Failure(AppError.InvalidData)
            dataStore.edit { it[Keys.authenticatedUserId] = account.id }
            AppResult.Success(account)
        } catch (error: CancellationException) {
            throw error
        } catch (error: HttpException) {
            AppResult.Failure(
                if (error.code() == 400 || error.code() == 401) AppError.InvalidCredentials else AppError.Http(
                    error.code()
                )
            )
        } catch (error: IOException) {
            AppResult.Failure(AppError.Network)
        } catch (_: Exception) {
            AppResult.Failure(AppError.Unknown)
        }
    }

    override suspend fun loadAccount(userId: Int): AppResult<Account> =
        withContext(ioDispatcher) {
            try {
                val account = api.getAccount(userId).toDomain()
                if (account.id <= 0) {
                    AppResult.Failure(AppError.InvalidData)
                } else {
                    AppResult.Success(account)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: HttpException) {
                if (error.code() == 404) {
                    AppResult.Failure(AppError.AuthenticationRequired)
                } else {
                    AppResult.Failure(AppError.Http(error.code()))
                }
            } catch (error: IOException) {
                AppResult.Failure(AppError.Network)
            } catch (_: Exception) {
                AppResult.Failure(AppError.Unknown)
            }
    }

    override suspend fun signOut(): AppResult<Unit> = withContext(ioDispatcher) {
        try {
            var localAvatarUri: String? = null
            dataStore.edit {
                localAvatarUri = it[Keys.localAccountAvatarUri]
                it.remove(Keys.authenticatedUserId)
                it.remove(Keys.localAccountAvatarUri)
            }
            localAvatarUri?.let(avatarStorage::delete)
            AppResult.Success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            AppResult.Failure(AppError.Storage)
        }
    }

    override suspend fun importLocalAvatar(sourceUri: String): AppResult<Unit> =
        withContext(ioDispatcher) {
            val importedUri = try {
                avatarStorage.import(sourceUri)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                return@withContext AppResult.Failure(AppError.Storage)
            }
            try {
                var previousUri: String? = null
                dataStore.edit { preferences ->
                    previousUri = preferences[Keys.localAccountAvatarUri]
                    preferences[Keys.localAccountAvatarUri] = importedUri
                }
                previousUri?.let(avatarStorage::delete)
                AppResult.Success(Unit)
            } catch (error: CancellationException) {
                avatarStorage.delete(importedUri)
                throw error
            } catch (_: Exception) {
                avatarStorage.delete(importedUri)
                AppResult.Failure(AppError.Storage)
            }
        }

    override suspend fun removeLocalAvatar(): AppResult<Unit> = withContext(ioDispatcher) {
        try {
            var localAvatarUri: String? = null
            dataStore.edit { preferences ->
                localAvatarUri = preferences[Keys.localAccountAvatarUri]
                preferences.remove(Keys.localAccountAvatarUri)
            }
            localAvatarUri?.let(avatarStorage::delete)
            AppResult.Success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            AppResult.Failure(AppError.Storage)
        }
    }
}

private fun AccountDto.toDomain() = Account(id, username, firstName, lastName, email, image)
