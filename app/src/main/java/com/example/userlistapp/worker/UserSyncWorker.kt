package com.example.userlistapp.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.userlistapp.core.common.AppError
import com.example.userlistapp.core.common.AppResult
import com.example.userlistapp.domain.repository.SettingsRepository
import com.example.userlistapp.domain.usecase.RefreshUsersUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class UserSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val refreshUsers: RefreshUsersUseCase,
    private val settings: SettingsRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = when (val result = refreshUsers()) {
        is AppResult.Success -> {
            settings.setLastSuccessfulSync(System.currentTimeMillis())
            Result.success()
        }

        is AppResult.Failure ->
            if (result.error == AppError.AuthenticationRequired) Result.success()
            else if (shouldRetry(
                    result.error,
                    runAttemptCount
                )
            ) Result.retry() else Result.failure()
    }

    companion object {
        const val UNIQUE_NAME = "user-sync"
        internal const val MAX_ATTEMPTS = 4
    }
}

internal fun shouldRetry(error: AppError, runAttemptCount: Int): Boolean {
    if (runAttemptCount >= UserSyncWorker.MAX_ATTEMPTS - 1) return false
    return when (error) {
        AppError.Network -> true
        is AppError.Http -> error.code >= 500
        AppError.InvalidData, AppError.Storage, AppError.Unknown -> false
        AppError.InvalidNote -> false
        AppError.AuthenticationRequired, AppError.InvalidCredentials -> false
    }
}
