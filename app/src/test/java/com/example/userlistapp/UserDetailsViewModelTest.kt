package com.example.userlistapp

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.userlistapp.core.common.AppError
import com.example.userlistapp.core.common.AppResult
import com.example.userlistapp.domain.model.User
import com.example.userlistapp.domain.repository.UserRepository
import com.example.userlistapp.domain.usecase.DeleteUserNoteUseCase
import com.example.userlistapp.domain.usecase.ObserveUserDetailsUseCase
import com.example.userlistapp.domain.usecase.SaveUserNoteUseCase
import com.example.userlistapp.domain.usecase.ToggleFavoriteUseCase
import com.example.userlistapp.feature.users.details.UserDetailsViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UserDetailsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun `draft survives unrelated database updates and controls save availability`() =
        runTest(main.dispatcher) {
            val repository = DetailsRepository(sampleUser(note = "stored"))
            val viewModel = viewModel(repository)
            collectState(viewModel)
            advanceUntilIdle()

            assertEquals("stored", viewModel.uiState.value.noteDraft)
            assertFalse(viewModel.uiState.value.canSave)

            viewModel.setNoteDraft("edited")
            repository.emit(repository.user.value.copy(isFavorite = true))
            advanceUntilIdle()

            assertEquals("edited", viewModel.uiState.value.noteDraft)
            assertTrue(viewModel.uiState.value.canSave)
        }

    @Test
    fun `operation failure emits an error event`() = runTest(main.dispatcher) {
        val repository = DetailsRepository(
            sampleUser(),
            toggleResult = AppResult.Failure(AppError.Network),
        )
        val viewModel = viewModel(repository)
        collectState(viewModel)
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.toggleFavorite()
            advanceUntilIdle()
            assertEquals(R.string.error_network, awaitItem().resourceId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `successful delete clears draft and returns it to database source of truth`() =
        runTest(main.dispatcher) {
            val repository = DetailsRepository(sampleUser(note = "stored"))
            val viewModel = viewModel(repository)
            collectState(viewModel)
            advanceUntilIdle()
            viewModel.setNoteDraft("edited")

            viewModel.deleteNote()
            advanceUntilIdle()

            assertEquals("", viewModel.uiState.value.noteDraft)
            assertFalse(viewModel.uiState.value.canDelete)

            repository.emit(repository.user.value.copy(note = "restored"))
            advanceUntilIdle()
            assertEquals("restored", viewModel.uiState.value.noteDraft)
        }

    @Test
    fun `successful save trims note and clears draft override`() = runTest(main.dispatcher) {
        val repository = DetailsRepository(sampleUser(note = "stored"))
        val viewModel = viewModel(repository)
        collectState(viewModel)
        advanceUntilIdle()
        viewModel.setNoteDraft("  edited  ")
        advanceUntilIdle()

        viewModel.saveNote()
        advanceUntilIdle()

        assertEquals("edited", repository.user.value.note)
        assertEquals("edited", viewModel.uiState.value.noteDraft)
        assertFalse(viewModel.uiState.value.canSave)
    }

    @Test
    fun `delete without persisted note keeps active draft and skips repository`() =
        runTest(main.dispatcher) {
            val repository = DetailsRepository(sampleUser(note = null))
            val viewModel = viewModel(repository)
            collectState(viewModel)
            advanceUntilIdle()
            viewModel.setNoteDraft("unsaved")
            advanceUntilIdle()

            viewModel.deleteNote()
            advanceUntilIdle()

            assertEquals(0, repository.deleteCalls)
            assertEquals("unsaved", viewModel.uiState.value.noteDraft)
        }

    @Test
    fun `active operation rejects duplicate favorite and delete calls`() =
        runTest(main.dispatcher) {
            val favoriteGate = CompletableDeferred<Unit>()
            val repository = DetailsRepository(
                sampleUser(note = "stored"),
                beforeToggle = { favoriteGate.await() },
            )
            val viewModel = viewModel(repository)
            collectState(viewModel)
            advanceUntilIdle()

            viewModel.toggleFavorite()
            viewModel.toggleFavorite()
            viewModel.deleteNote()
            runCurrent()

            assertEquals(1, repository.toggleCalls)
            assertEquals(0, repository.deleteCalls)
            assertTrue(viewModel.uiState.value.isSaving)

            favoriteGate.complete(Unit)
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.isSaving)
        }

    private fun viewModel(repository: DetailsRepository) = UserDetailsViewModel(
        SavedStateHandle(mapOf("userId" to repository.currentUserId)),
        ObserveUserDetailsUseCase(repository),
        ToggleFavoriteUseCase(repository),
        SaveUserNoteUseCase(repository),
        DeleteUserNoteUseCase(repository),
    )

    private fun TestScope.collectState(viewModel: UserDetailsViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
    }
}

private class DetailsRepository(
    initialUser: User,
    private val toggleResult: AppResult<Unit> = AppResult.Success(Unit),
    private val beforeToggle: suspend () -> Unit = {},
    private val beforeDelete: suspend () -> Unit = {},
) : UserRepository {
    val user = MutableStateFlow(initialUser)
    val currentUserId: Int get() = user.value.id
    var toggleCalls = 0
    var deleteCalls = 0

    fun emit(value: User) {
        user.value = value
    }

    override fun observeUsers(): Flow<List<User>> = user.map(::listOf)
    override fun observeUser(userId: Int): Flow<User?> =
        user.map { it.takeIf { value -> value.id == userId } }

    override suspend fun refreshUsers(): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun setFavorite(userId: Int, favorite: Boolean): AppResult<Unit> {
        toggleCalls++
        beforeToggle()
        return toggleResult
    }

    override suspend fun saveNote(userId: Int, note: String): AppResult<Unit> {
        user.value = user.value.copy(note = note)
        return AppResult.Success(Unit)
    }

    override suspend fun deleteNote(userId: Int): AppResult<Unit> {
        deleteCalls++
        beforeDelete()
        user.value = user.value.copy(note = null)
        return AppResult.Success(Unit)
    }
}
