package com.example.userlistapp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.userlistapp.core.common.AppError
import com.example.userlistapp.core.common.AppResult
import com.example.userlistapp.domain.model.AppSettings
import com.example.userlistapp.domain.model.SessionState
import com.example.userlistapp.domain.model.ThemeMode
import com.example.userlistapp.domain.repository.AuthSessionRepository
import com.example.userlistapp.domain.repository.SettingsRepository
import com.example.userlistapp.domain.repository.UserRepository
import com.example.userlistapp.domain.usecase.RefreshUsersUseCase
import com.example.userlistapp.worker.UserSyncWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UserSyncWorkerTest {
    @Test
    fun `successful work updates last sync and returns success`() = runTest {
        val settings = WorkerSettingsRepository()
        val worker = worker(AppResult.Success(Unit), settings)

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertNotNull(settings.lastSuccessfulSync)
    }

    @Test
    fun `authentication required completes without updating last sync`() = runTest {
        val settings = WorkerSettingsRepository()
        val worker = worker(
            refreshResult = AppResult.Failure(AppError.AuthenticationRequired),
            settings = settings,
            signedIn = false,
        )

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertNull(settings.lastSuccessfulSync)
    }

    @Test
    fun `retryable failure returns retry before attempt limit`() = runTest {
        val worker = worker(AppResult.Failure(AppError.Network), WorkerSettingsRepository())

        assertEquals(ListenableWorker.Result.retry(), worker.doWork())
    }

    @Test
    fun `non-retryable failure returns failure`() = runTest {
        val worker = worker(AppResult.Failure(AppError.InvalidData), WorkerSettingsRepository())

        assertEquals(ListenableWorker.Result.failure(), worker.doWork())
    }

    private fun worker(
        refreshResult: AppResult<Unit>,
        settings: WorkerSettingsRepository,
        signedIn: Boolean = true,
    ): UserSyncWorker {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val users = WorkerUserRepository(refreshResult)
        val auth = WorkerAuthRepository(
            if (signedIn) SessionState.SignedIn(1) else SessionState.SignedOut,
        )
        val refreshUsers = RefreshUsersUseCase(users, auth)
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker = UserSyncWorker(
                appContext,
                workerParameters,
                refreshUsers,
                settings,
            )
        }
        return TestListenableWorkerBuilder<UserSyncWorker>(context)
            .setWorkerFactory(factory)
            .build()
    }
}

private class WorkerUserRepository(
    private val refreshResult: AppResult<Unit>,
) : UserRepository {
    override fun observeUsers() = MutableStateFlow(emptyList<com.example.userlistapp.domain.model.User>())
    override fun observeUser(userId: Int) =
        MutableStateFlow<com.example.userlistapp.domain.model.User?>(null)

    override suspend fun refreshUsers() = refreshResult
    override suspend fun setFavorite(userId: Int, favorite: Boolean) = AppResult.Success(Unit)
    override suspend fun saveNote(userId: Int, note: String) = AppResult.Success(Unit)
    override suspend fun deleteNote(userId: Int) = AppResult.Success(Unit)
}

private class WorkerAuthRepository(
    initialSession: SessionState,
) : AuthSessionRepository {
    override val sessionState = MutableStateFlow(initialSession)
    override val localAvatarUri = MutableStateFlow<String?>(null)
    override suspend fun signIn(username: String, password: String) =
        AppResult.Failure(AppError.InvalidCredentials)

    override suspend fun loadAccount(userId: Int) =
        AppResult.Failure(AppError.AuthenticationRequired)

    override suspend fun signOut() = AppResult.Success(Unit)
    override suspend fun importLocalAvatar(sourceUri: String) = AppResult.Success(Unit)
    override suspend fun removeLocalAvatar() = AppResult.Success(Unit)
}

private class WorkerSettingsRepository : SettingsRepository {
    override val settings: Flow<AppSettings> = MutableStateFlow(AppSettings())
    var lastSuccessfulSync: Long? = null

    override suspend fun setTheme(mode: ThemeMode) = Unit
    override suspend fun setBackgroundSync(enabled: Boolean) = Unit
    override suspend fun setLastSuccessfulSync(timestamp: Long) {
        lastSuccessfulSync = timestamp
    }
}
