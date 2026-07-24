package com.example.userlistapp

import com.example.userlistapp.core.common.AppError
import com.example.userlistapp.data.local.nextSnapshotBatchId
import com.example.userlistapp.worker.UserSyncWorker
import com.example.userlistapp.worker.shouldRetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncEdgeCasesTest {
    @Test
    fun `snapshot batch ID saturates instead of wrapping below latest batch`() {
        assertEquals(
            Long.MAX_VALUE,
            nextSnapshotBatchId(candidateBatchId = 100, latestBatchId = Long.MAX_VALUE),
        )
    }

    @Test
    fun `retryable errors stop retrying at configured cap`() {
        assertTrue(shouldRetry(AppError.Network, runAttemptCount = 0))
        assertTrue(
            shouldRetry(
                AppError.Http(503),
                runAttemptCount = UserSyncWorker.MAX_ATTEMPTS - 2
            )
        )
        assertFalse(
            shouldRetry(
                AppError.Network,
                runAttemptCount = UserSyncWorker.MAX_ATTEMPTS - 1
            )
        )
        assertFalse(shouldRetry(AppError.Http(404), runAttemptCount = 0))
        assertFalse(shouldRetry(AppError.InvalidData, runAttemptCount = 0))
    }
}
