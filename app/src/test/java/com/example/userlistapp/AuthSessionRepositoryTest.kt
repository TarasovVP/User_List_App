package com.example.userlistapp

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.userlistapp.core.common.AppError
import com.example.userlistapp.core.common.AppResult
import com.example.userlistapp.data.preferences.AuthSessionRepositoryImpl
import com.example.userlistapp.data.preferences.LocalAvatarStorage
import com.example.userlistapp.data.remote.AccountDto
import com.example.userlistapp.data.remote.AuthApi
import com.example.userlistapp.data.remote.LoginRequestDto
import com.example.userlistapp.domain.model.SessionState
import com.example.userlistapp.domain.usecase.LoadAccountUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.After
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
    ): AuthSessionRepositoryImpl {
        val file = File.createTempFile("auth-session-", ".preferences_pb")
        file.delete()
        dataStoreFiles += file
        val avatarDirectory = createTempDirectory("account-avatars-").toFile()
        return AuthSessionRepositoryImpl(
            PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { file }),
            api,
            LocalAvatarStorage(avatarDirectory) { uri -> File(java.net.URI(uri)).inputStream() },
            StandardTestDispatcher(testScheduler),
        )
    }
}

private class TestAuthApi(
    var loginFailure: Throwable? = null,
    var accountFailure: Throwable? = null,
) : AuthApi {
    override suspend fun login(request: LoginRequestDto): AccountDto {
        loginFailure?.let { throw it }
        return AccountDto(1, request.username, "Emily", "Johnson", "emily@example.com", "")
    }

    override suspend fun getAccount(id: Int): AccountDto {
        accountFailure?.let { throw it }
        return AccountDto(id, "emilys", "Emily", "Johnson", "emily@example.com", "")
    }
}
