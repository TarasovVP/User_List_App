package com.example.userlistapp

import androidx.work.WorkInfo
import com.example.userlistapp.domain.model.SyncState
import com.example.userlistapp.worker.toDomainState
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkManagerSyncSchedulerTest {
    @Test
    fun `running has highest priority`() {
        assertEquals(
            SyncState.RUNNING,
            listOf(
                WorkInfo.State.FAILED,
                WorkInfo.State.ENQUEUED,
                WorkInfo.State.RUNNING,
            ).toDomainState(),
        )
    }

    @Test
    fun `pending work has priority over terminal states`() {
        assertEquals(
            SyncState.IDLE,
            listOf(WorkInfo.State.SUCCEEDED, WorkInfo.State.BLOCKED).toDomainState(),
        )
        assertEquals(
            SyncState.IDLE,
            listOf(WorkInfo.State.FAILED, WorkInfo.State.ENQUEUED).toDomainState(),
        )
    }

    @Test
    fun `failed has priority over succeeded`() {
        assertEquals(
            SyncState.FAILED,
            listOf(WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED).toDomainState(),
        )
    }

    @Test
    fun `succeeded and empty states map correctly`() {
        assertEquals(SyncState.SUCCEEDED, listOf(WorkInfo.State.SUCCEEDED).toDomainState())
        assertEquals(SyncState.IDLE, emptyList<WorkInfo.State>().toDomainState())
        assertEquals(SyncState.IDLE, listOf(WorkInfo.State.CANCELLED).toDomainState())
    }
}
