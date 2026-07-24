package com.example.userlistapp

import com.example.userlistapp.core.common.AppError
import com.example.userlistapp.core.common.AppResult
import com.example.userlistapp.domain.model.Account
import com.example.userlistapp.domain.model.SessionState
import com.example.userlistapp.domain.repository.AuthSessionRepository
import com.example.userlistapp.domain.usecase.SignInUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SignInUseCaseTest {
    @Test
    fun `blank credentials fail without calling repository`() = runTest {
        val repository = CountingAuthRepository()
        val useCase = SignInUseCase(repository)

        assertEquals(
            AppResult.Failure(AppError.InvalidCredentials),
            useCase(" ", "password"),
        )
        assertEquals(0, repository.signInCalls)
    }

    @Test
    fun `valid credentials are delegated to repository`() = runTest {
        val repository = CountingAuthRepository()
        val useCase = SignInUseCase(repository)

        assertEquals(repository.signInResult, useCase("emilys", "emilyspass"))
        assertEquals(1, repository.signInCalls)
    }
}

private class CountingAuthRepository : AuthSessionRepository {
    override val sessionState = MutableStateFlow<SessionState>(SessionState.SignedOut)
    override val localAvatarUri = MutableStateFlow<String?>(null)
    var signInCalls = 0
    val signInResult = AppResult.Success(
        Account(1, "emilys", "Emily", "User", "emily@example.com", ""),
    )

    override suspend fun signIn(username: String, password: String): AppResult<Account> {
        signInCalls++
        return signInResult
    }

    override suspend fun loadAccount(userId: Int) =
        AppResult.Failure(AppError.AuthenticationRequired)

    override suspend fun signOut() = AppResult.Success(Unit)
    override suspend fun importLocalAvatar(sourceUri: String) = AppResult.Success(Unit)
    override suspend fun removeLocalAvatar() = AppResult.Success(Unit)
}
