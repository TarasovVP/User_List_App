package com.example.userlistapp.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.userlistapp.core.common.AppError
import com.example.userlistapp.core.common.AppResult
import com.example.userlistapp.data.remote.AccountDto
import com.example.userlistapp.data.remote.AuthApi
import com.example.userlistapp.data.remote.LoginRequestDto
import com.example.userlistapp.domain.model.Account
import com.example.userlistapp.domain.model.SessionState
import com.example.userlistapp.domain.repository.AuthSessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import java.io.IOException

class AuthSessionRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
    private val api: AuthApi,
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

    override suspend fun signIn(username: String, password: String): AppResult<Account> {
        if (username.isBlank() || password.isBlank()) return AppResult.Failure(AppError.InvalidCredentials)
        return try {
            val account = api.login(LoginRequestDto(username.trim(), password)).toDomain()
            if (account.id <= 0) return AppResult.Failure(AppError.InvalidData)
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

    override suspend fun loadAccount(userId: Int): AppResult<Account> = try {
        val account = api.getAccount(userId).toDomain()
        if (account.id <= 0) AppResult.Failure(AppError.InvalidData) else AppResult.Success(account)
    } catch (error: CancellationException) {
        throw error
    } catch (error: HttpException) {
        if (error.code() == 404) {
            signOut()
            AppResult.Failure(AppError.AuthenticationRequired)
        } else AppResult.Failure(AppError.Http(error.code()))
    } catch (error: IOException) {
        AppResult.Failure(AppError.Network)
    } catch (_: Exception) {
        AppResult.Failure(AppError.Unknown)
    }

    override suspend fun signOut(): AppResult<Unit> = try {
        dataStore.edit {
            it.remove(Keys.authenticatedUserId)
            it.remove(Keys.localAccountAvatarUri)
        }
        AppResult.Success(Unit)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        AppResult.Failure(AppError.Storage)
    }

    override suspend fun setLocalAvatar(uri: String?): AppResult<Unit> = try {
        dataStore.edit { preferences ->
            if (uri == null) preferences.remove(Keys.localAccountAvatarUri)
            else preferences[Keys.localAccountAvatarUri] = uri
        }
        AppResult.Success(Unit)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        AppResult.Failure(AppError.Storage)
    }
}

private fun AccountDto.toDomain() = Account(id, username, firstName, lastName, email, image)
