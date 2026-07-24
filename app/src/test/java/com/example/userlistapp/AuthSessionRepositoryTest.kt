package com.example.userlistapp

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.userlistapp.core.common.AppError
import com.example.userlistapp.core.common.AppResult
import com.example.userlistapp.data.preferences.AuthSessionRepositoryImpl
import com.example.userlistapp.data.remote.AccountDto
import com.example.userlistapp.data.remote.AuthApi
import com.example.userlistapp.data.remote.LoginRequestDto
import com.example.userlistapp.domain.model.SessionState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.File
import java.io.IOException

class AuthSessionRepositoryTest {
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
        val repository = repository(TestAuthApi())
        repository.signIn("emilys", "emilyspass")
        repository.setLocalAvatar("content://avatar")

        assertEquals(AppResult.Success(Unit), repository.signOut())
        assertEquals(SessionState.SignedOut, repository.sessionState.first())
        assertNull(repository.localAvatarUri.first())
    }

    @Test
    fun `invalid stored user clears simulated session`() = runTest {
        val api = TestAuthApi()
        val repository = repository(api)
        repository.signIn("emilys", "emilyspass")
        api.accountFailure = HttpException(
            Response.error<AccountDto>(404, "{}".toResponseBody("application/json".toMediaType())),
        )

        assertEquals(AppResult.Failure(AppError.AuthenticationRequired), repository.loadAccount(1))
        assertEquals(SessionState.SignedOut, repository.sessionState.first())
    }

    private fun kotlinx.coroutines.test.TestScope.repository(api: AuthApi): AuthSessionRepositoryImpl {
        val file = File.createTempFile("auth-session-", ".preferences_pb")
        file.delete()
        return AuthSessionRepositoryImpl(
            PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { file }),
            api,
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
