package com.example.userlistapp

import com.example.userlistapp.core.common.AppResult
import com.example.userlistapp.core.common.TimeProvider
import com.example.userlistapp.domain.repository.UserRepository
import com.example.userlistapp.domain.usecase.MarkUserAsViewedUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkUserAsViewedUseCaseTest {
    @Test
    fun `use case reads time once and forwards it to repository`() = runTest {
        var callCount = 0
        val timeProvider = TimeProvider {
            callCount++
            123456789L
        }
        val repository = object : UserRepositoryStub() {
            var capturedUserId: Int? = null
            var capturedViewedAt: Long? = null
            override suspend fun markUserAsViewed(userId: Int, viewedAt: Long): AppResult<Unit> {
                capturedUserId = userId
                capturedViewedAt = viewedAt
                return AppResult.Success(Unit)
            }
        }
        val useCase = MarkUserAsViewedUseCase(repository, timeProvider)

        useCase(42)

        assertEquals(42, repository.capturedUserId)
        assertEquals(123456789L, repository.capturedViewedAt)
        assertEquals(1, callCount)
    }
}

private open class UserRepositoryStub : UserRepository {
    override fun observeUsers() = throw NotImplementedError()
    override fun observeUser(userId: Int) = throw NotImplementedError()
    override suspend fun refreshUsers(source: com.example.userlistapp.domain.model.RefreshSource) = throw NotImplementedError()
    override suspend fun setFavorite(userId: Int, favorite: Boolean) = throw NotImplementedError()
    override suspend fun saveNote(userId: Int, note: String) = throw NotImplementedError()
    override suspend fun deleteNote(userId: Int) = throw NotImplementedError()
    override suspend fun markUserAsViewed(userId: Int, viewedAt: Long): AppResult<Unit> = throw NotImplementedError()
}
