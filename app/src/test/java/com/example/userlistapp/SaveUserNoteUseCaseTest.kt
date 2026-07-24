package com.example.userlistapp

import com.example.userlistapp.core.common.AppError
import com.example.userlistapp.core.common.AppResult
import com.example.userlistapp.domain.model.User
import com.example.userlistapp.domain.repository.UserRepository
import com.example.userlistapp.domain.usecase.SaveUserNoteUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveUserNoteUseCaseTest {
    @Test
    fun `blank note returns validation error without calling repository`() = runTest {
        val repository = NoteRepository()

        val result = SaveUserNoteUseCase(repository)(userId = 4, note = "   ")

        assertEquals(AppResult.Failure(AppError.InvalidNote), result)
        assertNull(repository.savedNote)
    }

    @Test
    fun `valid note is trimmed before repository call`() = runTest {
        val repository = NoteRepository()

        val result = SaveUserNoteUseCase(repository)(userId = 4, note = "  remember  ")

        assertTrue(result is AppResult.Success)
        assertEquals("remember", repository.savedNote)
    }
}

private class NoteRepository : UserRepository {
    var savedNote: String? = null

    override fun observeUsers(): Flow<List<User>> = emptyFlow()
    override fun observeUser(userId: Int): Flow<User?> = emptyFlow()
    override suspend fun refreshUsers(): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun setFavorite(userId: Int, favorite: Boolean): AppResult<Unit> =
        AppResult.Success(Unit)

    override suspend fun saveNote(userId: Int, note: String): AppResult<Unit> {
        savedNote = note
        return AppResult.Success(Unit)
    }

    override suspend fun deleteNote(userId: Int): AppResult<Unit> = AppResult.Success(Unit)
}
