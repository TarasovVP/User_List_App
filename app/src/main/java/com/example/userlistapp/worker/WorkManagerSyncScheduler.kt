package com.example.userlistapp.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.userlistapp.domain.model.SyncState
import com.example.userlistapp.domain.repository.SyncScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class WorkManagerSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : SyncScheduler {
    private val workManager by lazy { WorkManager.getInstance(context) }

    override fun observeState(): Flow<SyncState> = workManager
        .getWorkInfosForUniqueWorkFlow(UserSyncWorker.UNIQUE_NAME)
        .map { work -> work.map(WorkInfo::state).toDomainState() }
        .distinctUntilChanged()

    override fun setEnabled(enabled: Boolean) {
        if (!enabled) {
            workManager.cancelUniqueWork(UserSyncWorker.UNIQUE_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<UserSyncWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(
            UserSyncWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}

internal fun Iterable<WorkInfo.State>.toDomainState(): SyncState {
    val states = toSet()
    return when {
        WorkInfo.State.RUNNING in states -> SyncState.RUNNING
        WorkInfo.State.ENQUEUED in states || WorkInfo.State.BLOCKED in states -> SyncState.IDLE
        WorkInfo.State.FAILED in states -> SyncState.FAILED
        WorkInfo.State.SUCCEEDED in states -> SyncState.SUCCEEDED
        else -> SyncState.IDLE
    }
}
