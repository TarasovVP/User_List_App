package com.example.userlistapp

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.userlistapp.core.common.AppError
import com.example.userlistapp.core.common.AppResult
import com.example.userlistapp.core.common.EMPTY
import com.example.userlistapp.data.local.LocalAvatarStorage
import com.example.userlistapp.data.preferences.createPreferencesDataStore
import com.example.userlistapp.data.remote.AccountDto
import com.example.userlistapp.data.remote.AuthApi
import com.example.userlistapp.data.remote.AuthTokenHolder
import com.example.userlistapp.data.remote.LoginRequestDto
import com.example.userlistapp.data.repository.AuthSessionRepositoryImpl
import com.example.userlistapp.domain.model.SessionState
import com.example.userlistapp.domain.repository.AuthSessionGuard
import com.example.userlistapp.domain.usecase.LoadAccountUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.File
import java.io.IOException
import kotlin.io.path.createTempDirectory

class AuthSessionRepositoryTest {
    private val dataStoreFiles = mutableListOf<File>()

    @After
    fun cleanup() {
        dataStoreFiles.forEach(File::delete)
        dataStoreFiles.clear()
    }

    @Test
    fun `missing id restores signed out and successful login persists only session id`() = runTest {
        val api = TestAuthApi()
        val repository = repository(api)
        assertEquals(SessionState.SignedOut, repository.sessionState.first())

        assertEquals(
            "emilys",
            (repository.signIn("emilys", "emilyspass") as AppResult.Success).value.username
        )
        assertEquals(SessionState.SignedIn(1), repository.sessionState.first())
    }

    @Test
    fun `network and invalid credentials map to typed authentication errors`() = runTest {
        val api = TestAuthApi(loginFailure = IOException("offline"))
        assertEquals(
            AppResult.Failure(AppError.Network),
            repository(api).signIn("user", "password")
        )

        api.loginFailure = HttpException(
            Response.error<AccountDto>(401, "{}".toResponseBody("application/json".toMediaType())),
        )
        assertEquals(
            AppResult.Failure(AppError.InvalidCredentials),
            repository(api).signIn("user", "password")
        )
    }

    @Test
    fun `failed session persistence never leaves an authorization token installed`() = runTest {
        val tokenHolder = AuthTokenHolder().apply { accessToken = "previous-token" }
        val repository = AuthSessionRepositoryImpl(
            AlwaysFailingDataStore(),
            TestAuthApi(accessToken = "new-token"),
            LocalAvatarStorage(createTempDirectory("account-avatars-").toFile()) { null },
            tokenHolder,
            StandardTestDispatcher(testScheduler),
            AuthSessionGuard(),
        )

        assertEquals(
            AppResult.Failure(AppError.Network),
            repository.signIn("emilys", "emilyspass"),
        )
        assertNull(tokenHolder.accessToken)
    }

    @Test
    fun `a corrupted session file is replaced instead of failing every read`() = runTest {
        val file = newDataStoreFile()
        file.writeText("not a preferences protobuf")
        val repository = repository(TestAuthApi(), file)

        assertEquals(SessionState.SignedOut, repository.sessionState.first())
        assertNull(repository.localAvatarUri.first())

        repository.signIn("emilys", "emilyspass")
        assertEquals(SessionState.SignedIn(1), repository.sessionState.first())
    }

    @Test
    fun `sign out clears user id and local avatar`() = runTest {
        val source = File.createTempFile("avatar-source-", ".image")
        source.writeText("avatar")
        val repository = repository(TestAuthApi())
        repository.signIn("emilys", "emilyspass")
        repository.importLocalAvatar(source.toURI().toString())
        val storedAvatar = File(java.net.URI(requireNotNull(repository.localAvatarUri.first())))

        assertEquals(AppResult.Success(Unit), repository.signOut())
        assertEquals(SessionState.SignedOut, repository.sessionState.first())
        assertNull(repository.localAvatarUri.first())
        assertEquals(false, storedAvatar.exists())
    }

    @Test
    fun `avatar import is rejected after sign out and leaves no file`() = runTest {
        val source = File.createTempFile("avatar-source-", ".image")
        source.writeText("avatar")
        val avatarDirectory = createTempDirectory("account-avatars-").toFile()
        val dataStoreFile = newDataStoreFile()
        val repository = AuthSessionRepositoryImpl(
            createPreferencesDataStore(scope = backgroundScope) { dataStoreFile },
            TestAuthApi(),
            LocalAvatarStorage(avatarDirectory) { uri -> File(java.net.URI(uri)).inputStream() },
            AuthTokenHolder(),
            StandardTestDispatcher(testScheduler),
            AuthSessionGuard(),
        )

        assertEquals(
            AppResult.Failure(AppError.AuthenticationRequired),
            repository.importLocalAvatar(source.toURI().toString()),
        )
        assertNull(repository.localAvatarUri.first())
        assertEquals(emptyList<File>(), avatarDirectory.listFiles().orEmpty().toList())
    }

    @Test
    fun `cancel after avatar preference commit rolls back uri and imported file`() = runTest {
        val source = File.createTempFile("avatar-source-", ".image")
        source.writeText("avatar")
        val avatarDirectory = createTempDirectory("account-avatars-").toFile()
        val dataStore = CommitThenCancelDataStore(
            mutablePreferencesOf(intPreferencesKey(AUTH_USER_ID_KEY) to 1),
        )
        val repository = AuthSessionRepositoryImpl(
            dataStore,
            TestAuthApi(),
            LocalAvatarStorage(avatarDirectory) { uri -> File(java.net.URI(uri)).inputStream() },
            AuthTokenHolder(),
            StandardTestDispatcher(testScheduler),
            AuthSessionGuard(),
        )

        var cancellationPropagated = false
        try {
            repository.importLocalAvatar(source.toURI().toString())
        } catch (_: CancellationException) {
            cancellationPropagated = true
        }

        assertTrue(cancellationPropagated)
        assertNull(dataStore.state.value[stringPreferencesKey(LOCAL_AVATAR_URI_KEY)])
        assertEquals(emptyList<File>(), avatarDirectory.listFiles().orEmpty().toList())
    }

    @Test
    fun `load account is pure and use case signs out for an invalid stored user`() = runTest {
        val api = TestAuthApi()
        val source = File.createTempFile("avatar-source-", ".image")
        source.writeText("avatar")
        val repository = repository(api)
        repository.signIn("emilys", "emilyspass")
        repository.importLocalAvatar(source.toURI().toString())
        val storedAvatar = File(java.net.URI(requireNotNull(repository.localAvatarUri.first())))
        api.accountFailure = HttpException(
            Response.error<AccountDto>(404, "{}".toResponseBody("application/json".toMediaType())),
        )

        assertEquals(AppResult.Failure(AppError.AuthenticationRequired), repository.loadAccount(1))
        assertEquals(SessionState.SignedIn(1), repository.sessionState.first())
        assertEquals(true, storedAvatar.exists())

        assertEquals(
            AppResult.Failure(AppError.AuthenticationRequired),
            LoadAccountUseCase(repository)(1),
        )
        assertEquals(SessionState.SignedOut, repository.sessionState.first())
        assertNull(repository.localAvatarUri.first())
        assertEquals(false, storedAvatar.exists())
    }

    private fun kotlinx.coroutines.test.TestScope.repository(
        api: AuthApi,
        file: File = newDataStoreFile(),
    ): AuthSessionRepositoryImpl {
        val avatarDirectory = createTempDirectory("account-avatars-").toFile()
        return AuthSessionRepositoryImpl(
            createPreferencesDataStore(scope = backgroundScope) { file },
            api,
            LocalAvatarStorage(avatarDirectory) { uri -> File(java.net.URI(uri)).inputStream() },
            AuthTokenHolder(),
            StandardTestDispatcher(testScheduler),
            AuthSessionGuard(),
        )
    }

    private fun newDataStoreFile(): File {
        val file = File.createTempFile("auth-session-", ".preferences_pb")
        file.delete()
        dataStoreFiles += file
        return file
    }
}

private class CommitThenCancelDataStore(initial: Preferences) : DataStore<Preferences> {
    val state = MutableStateFlow(initial)
    override val data: Flow<Preferences> = state
    private var shouldCancel = true

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences {
        val updated = transform(state.value)
        state.value = updated
        if (shouldCancel) {
            shouldCancel = false
            throw CancellationException("cancel after commit")
        }
        return updated
    }
}

private class AlwaysFailingDataStore : DataStore<Preferences> {
    override val data: Flow<Preferences> = MutableStateFlow(mutablePreferencesOf())

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = throw IOException("session persistence failed")
}

private class TestAuthApi(
    var loginFailure: Throwable? = null,
    var accountFailure: Throwable? = null,
    private val accessToken: String = String.EMPTY,
) : AuthApi {
    override suspend fun login(request: LoginRequestDto): AccountDto {
        loginFailure?.let { throw it }
        return AccountDto(
            1,
            request.username,
            "Emily",
            "Johnson",
            "emily@example.com",
            String.EMPTY,
            accessToken,
        )
    }

    override suspend fun getAccount(id: Int): AccountDto {
        accountFailure?.let { throw it }
        return AccountDto(id, "emilys", "Emily", "Johnson", "emily@example.com", String.EMPTY)
    }
}

private const val AUTH_USER_ID_KEY = "simulated_authenticated_user_id"
private const val LOCAL_AVATAR_URI_KEY = "local_account_avatar_uri"
