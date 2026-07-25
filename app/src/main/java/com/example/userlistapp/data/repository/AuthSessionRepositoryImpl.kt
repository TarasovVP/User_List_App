package com.example.userlistapp.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.userlistapp.core.common.AppError
import com.example.userlistapp.core.common.AppResult
import com.example.userlistapp.core.common.IoDispatcher
import com.example.userlistapp.data.local.LocalAvatarStorage
import com.example.userlistapp.data.remote.AccountDto
import com.example.userlistapp.data.remote.AuthApi
import com.example.userlistapp.data.remote.AuthTokenHolder
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
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

class AuthSessionRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
    private val api: AuthApi,
    private val avatarStorage: LocalAvatarStorage,
    private val tokenHolder: AuthTokenHolder,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AuthSessionRepository {
    private object Keys {
        val authenticatedUserId = intPreferencesKey(AUTHENTICATED_USER_ID_KEY)
        val localAccountAvatarUri = stringPreferencesKey(LOCAL_ACCOUNT_AVATAR_URI_KEY)
    }

    private val preferences: Flow<Preferences> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }

    override val sessionState: Flow<SessionState> = preferences.map { prefs ->
        prefs[Keys.authenticatedUserId]?.let(SessionState::SignedIn) ?: SessionState.SignedOut
    }

    override val localAvatarUri: Flow<String?> = preferences.map { it[Keys.localAccountAvatarUri] }

    override suspend fun signIn(
        username: String,
        password: String,
    ): AppResult<Account> = withContext(ioDispatcher) {
        try {
            val dto = api.login(LoginRequestDto(username.trim(), password))
            val account = dto.toDomain()
            if (account.id <= 0) return@withContext AppResult.Failure(AppError.InvalidData)
            if (dto.accessToken.isNotEmpty()) tokenHolder.accessToken = dto.accessToken
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
        } catch (_: SerializationException) {
            AppResult.Failure(AppError.InvalidData)
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
            } catch (_: SerializationException) {
                AppResult.Failure(AppError.InvalidData)
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
            tokenHolder.accessToken = null
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

private const val AUTHENTICATED_USER_ID_KEY = "simulated_authenticated_user_id"
private const val LOCAL_ACCOUNT_AVATAR_URI_KEY = "local_account_avatar_uri"
