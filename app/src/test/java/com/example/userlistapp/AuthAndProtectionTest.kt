package com.example.userlistapp

import com.example.userlistapp.core.common.AppError
import com.example.userlistapp.core.common.AppResult
import com.example.userlistapp.domain.model.Account
import com.example.userlistapp.domain.model.AppSettings
import com.example.userlistapp.domain.model.SessionState
import com.example.userlistapp.domain.model.SyncState
import com.example.userlistapp.domain.model.ThemeMode
import com.example.userlistapp.domain.repository.AuthSessionRepository
import com.example.userlistapp.domain.repository.SettingsRepository
import com.example.userlistapp.domain.repository.SyncScheduler
import com.example.userlistapp.domain.repository.UserRepository
import com.example.userlistapp.domain.usecase.RefreshUsersUseCase
import com.example.userlistapp.feature.account.AuthViewModel
import com.example.userlistapp.worker.SyncCoordinator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthAndProtectionTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun `blank credentials do not invoke login and expose validation error`() =
        runTest(main.dispatcher) {
            val repository = FakeAuthRepository()
            val viewModel = AuthViewModel(authUseCases(repository))
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
            runCurrent()

            viewModel.signIn("", "")
            advanceUntilIdle()

            assertEquals(0, repository.loginCalls)
            assertEquals(
                R.string.error_invalid_credentials,
                viewModel.uiState.value.loginError?.resourceId
            )
        }

    @Test
    fun `successful login transitions to signed in and account content`() =
        runTest(main.dispatcher) {
            val repository = FakeAuthRepository()
            val viewModel = AuthViewModel(authUseCases(repository))
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }

            viewModel.signIn("emilys", "emilyspass")
            advanceUntilIdle()

            assertEquals(SessionState.SignedIn(1), viewModel.uiState.value.session)
            assertEquals("Emily User", viewModel.uiState.value.account?.fullName)
            assertNull(viewModel.uiState.value.loginError)
        }

    @Test
    fun `failed login remains signed out and can retry`() = runTest(main.dispatcher) {
        val repository =
            FakeAuthRepository(loginResult = AppResult.Failure(AppError.InvalidCredentials))
        val viewModel = AuthViewModel(authUseCases(repository))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }

        viewModel.signIn("bad", "bad")
        advanceUntilIdle()

        assertEquals(SessionState.SignedOut, viewModel.uiState.value.session)
        assertEquals(
            R.string.error_invalid_credentials,
            viewModel.uiState.value.loginError?.resourceId
        )
        assertFalse(viewModel.uiState.value.isSigningIn)
    }

    @Test
    fun `sign out clears session and local avatar`() = runTest(main.dispatcher) {
        val repository = FakeAuthRepository(SessionState.SignedIn(1))
        repository.avatar.value = "content://avatar"
        val viewModel = AuthViewModel(authUseCases(repository))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.signOut()
        advanceUntilIdle()

        assertEquals(SessionState.SignedOut, repository.session.value)
        assertNull(repository.avatar.value)
    }

    @Test
    fun `sign out cancels in-flight sign in and still signs out`() = runTest(main.dispatcher) {
        val repository = FakeAuthRepository()
        repository.suspendLogin = true
        val viewModel = AuthViewModel(authUseCases(repository))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }

        viewModel.signIn("emilys", "emilyspass")
        runCurrent()
        viewModel.signOut()
        advanceUntilIdle()

        assertEquals(1, repository.loginCalls)
        assertEquals(1, repository.signOutCalls)
        assertEquals(SessionState.SignedOut, repository.session.value)
        assertFalse(viewModel.uiState.value.isSigningIn)
    }

    @Test
    fun `protected refresh skips repository signed out and runs signed in`() =
        runTest(main.dispatcher) {
            val users = CountingUserRepository()
            val auth = FakeAuthRepository()
            val refresh = RefreshUsersUseCase(users, auth)

            assertEquals(AppResult.Failure(AppError.AuthenticationRequired), refresh())
            assertEquals(0, users.refreshCalls)
            auth.session.value = SessionState.SignedIn(1)
            assertEquals(AppResult.Success(Unit), refresh())
            assertEquals(1, users.refreshCalls)
        }

    @Test
    fun `sync coordinator requires both setting and signed in session`() =
        runTest(main.dispatcher) {
            val settings = FakeSettingsRepository()
            val auth = FakeAuthRepository()
            val scheduler = FakeScheduler()
            SyncCoordinator(settings, auth, scheduler).start(backgroundScope)
            runCurrent()
            assertEquals(listOf(false), scheduler.values)

            auth.session.value = SessionState.SignedIn(1)
            runCurrent()
            assertEquals(listOf(false, true), scheduler.values)

            settings.state.value = settings.state.value.copy(backgroundSyncEnabled = false)
            runCurrent()
            assertEquals(listOf(false, true, false), scheduler.values)
        }
}

private class FakeAuthRepository(
    initialSession: SessionState = SessionState.SignedOut,
    var loginResult: AppResult<Account> = AppResult.Success(
        Account(
            1,
            "emilys",
            "Emily",
            "User",
            "emily@example.com",
            ""
        )
    ),
) : AuthSessionRepository {
    val session = MutableStateFlow(initialSession)
    val avatar = MutableStateFlow<String?>(null)
    var loginCalls = 0
    var signOutCalls = 0
    var suspendLogin = false
    override val sessionState: Flow<SessionState> = session
    override val localAvatarUri: Flow<String?> = avatar
    override suspend fun signIn(username: String, password: String): AppResult<Account> {
        loginCalls++
        if (suspendLogin) awaitCancellation()
        if (loginResult is AppResult.Success) session.value =
            SessionState.SignedIn((loginResult as AppResult.Success).value.id)
        return loginResult
    }

    override suspend fun loadAccount(userId: Int) =
        AppResult.Success(Account(userId, "emilys", "Emily", "User", "emily@example.com", ""))

    override suspend fun signOut(): AppResult<Unit> {
        signOutCalls++
        session.value = SessionState.SignedOut
        avatar.value = null
        return AppResult.Success(Unit)
    }

    override suspend fun importLocalAvatar(sourceUri: String): AppResult<Unit> {
        avatar.value = sourceUri
        return AppResult.Success(Unit)
    }

    override suspend fun removeLocalAvatar(): AppResult<Unit> {
        avatar.value = null
        return AppResult.Success(Unit)
    }
}

private class CountingUserRepository : UserRepository {
    var refreshCalls = 0
    override fun observeUsers() =
        MutableStateFlow(emptyList<com.example.userlistapp.domain.model.User>())

    override fun observeUser(userId: Int) =
        MutableStateFlow<com.example.userlistapp.domain.model.User?>(null)

    override suspend fun refreshUsers(): AppResult<Unit> {
        refreshCalls++; return AppResult.Success(Unit)
    }

    override suspend fun setFavorite(userId: Int, favorite: Boolean) = AppResult.Success(Unit)
    override suspend fun saveNote(userId: Int, note: String) = AppResult.Success(Unit)
    override suspend fun deleteNote(userId: Int) = AppResult.Success(Unit)
}

private class FakeSettingsRepository : SettingsRepository {
    val state = MutableStateFlow(AppSettings(backgroundSyncEnabled = true))
    override val settings: Flow<AppSettings> = state
    override suspend fun setTheme(mode: ThemeMode) {
        state.value = state.value.copy(themeMode = mode)
    }

    override suspend fun setBackgroundSync(enabled: Boolean) {
        state.value = state.value.copy(backgroundSyncEnabled = enabled)
    }

    override suspend fun setLastSuccessfulSync(timestamp: Long) = Unit
}

private class FakeScheduler : SyncScheduler {
    val values = mutableListOf<Boolean>()
    override fun observeState(): Flow<SyncState> = MutableStateFlow(SyncState.IDLE)
    override fun setEnabled(enabled: Boolean) {
        values += enabled
    }
}
