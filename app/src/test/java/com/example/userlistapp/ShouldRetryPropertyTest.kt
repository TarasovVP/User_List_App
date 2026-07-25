package com.example.userlistapp

import com.example.userlistapp.core.common.AppError
import com.example.userlistapp.worker.UserSyncWorker
import com.example.userlistapp.worker.shouldRetry
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShouldRetryPropertyTest {
    @Test
    fun `no error is retried after the final allowed attempt`() = runTest {
        checkAll(Arb.int(min = UserSyncWorker.MAX_ATTEMPTS - 1, max = Int.MAX_VALUE)) { attempt ->
            listOf(AppError.Network, AppError.Http(500)).forEach { error ->
                assertFalse(shouldRetry(error, attempt))
            }
        }
    }

    @Test
    fun `network errors retry while attempts remain`() = runTest {
        checkAll(Arb.int(0 until UserSyncWorker.MAX_ATTEMPTS - 1)) { attempt ->
            assertTrue(shouldRetry(AppError.Network, attempt))
        }
    }

    @Test
    fun `server errors retry and client errors do not`() = runTest {
        checkAll(
            Arb.int(100..599),
            Arb.int(0 until UserSyncWorker.MAX_ATTEMPTS - 1),
        ) { statusCode, attempt ->
            val expected = statusCode >= 500
            assertEquals(expected, shouldRetry(AppError.Http(statusCode), attempt))
        }
    }

    @Test
    fun `authentication errors never retry`() = runTest {
        checkAll(
            Arb.int(min = 0, max = Int.MAX_VALUE),
            Arb.int(0..1),
        ) { attempt, errorIndex ->
            val error = listOf(
                AppError.AuthenticationRequired,
                AppError.InvalidCredentials,
            )[errorIndex]
            assertFalse(shouldRetry(error, attempt))
        }
    }

}
