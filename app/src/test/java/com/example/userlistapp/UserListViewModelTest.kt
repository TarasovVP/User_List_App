package com.example.userlistapp

import app.cash.turbine.test
import com.example.userlistapp.core.common.AppError
import com.example.userlistapp.core.common.AppResult
import com.example.userlistapp.domain.model.User
import com.example.userlistapp.domain.model.UserSort
import com.example.userlistapp.domain.repository.UserRepository
import com.example.userlistapp.domain.usecase.ObserveUsersUseCase
import com.example.userlistapp.domain.usecase.RefreshUsersUseCase
import com.example.userlistapp.feature.users.list.UserListViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserListViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test fun `successful load and search sort favorite filters combine reactively`() = runTest(main.dispatcher) {
        val repo = FakeUserRepository(listOf(sampleUser(1, "Zoe", "Able"), sampleUser(2, "ada", "Lovelace", favorite = true)))
        val vm = viewModel(repo)
        collectState(vm)
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.users.size)
        assertFalse(vm.uiState.value.isInitialLoading)
        assertNull(vm.uiState.value.initialError)

        vm.setQuery("EXAMPLE.COM")
        vm.setSort(UserSort.NAME_DESCENDING)
        advanceUntilIdle()
        assertEquals(listOf(1, 2), vm.uiState.value.users.map(User::id))

        vm.setFavoritesOnly(true)
        advanceUntilIdle()
        assertEquals(listOf(2), vm.uiState.value.users.map(User::id))
    }

    @Test fun `successful empty load shows empty content rather than an error`() = runTest(main.dispatcher) {
        val vm = viewModel(FakeUserRepository(emptyList()))
        collectState(vm)

        advanceUntilIdle()

        assertTrue(vm.uiState.value.users.isEmpty())
        assertFalse(vm.uiState.value.isInitialLoading)
        assertFalse(vm.uiState.value.hasCachedUsers)
        assertNull(vm.uiState.value.initialError)
    }

    @Test fun `initial network failure without cache shows full screen error`() = runTest(main.dispatcher) {
        val repo = FakeUserRepository(emptyList(), AppResult.Failure(AppError.Network))
        val vm = viewModel(repo)
        collectState(vm)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.users.isEmpty())
        assertNotNull(vm.uiState.value.initialError)
    }

    @Test fun `refresh failure keeps cache and emits snackbar once`() = runTest(main.dispatcher) {
        val repo = FakeUserRepository(listOf(sampleUser()), AppResult.Failure(AppError.Network))
        val vm = viewModel(repo)
        collectState(vm)
        advanceUntilIdle()
        vm.events.test {
            vm.refresh()
            advanceUntilIdle()
            assertEquals(1, vm.uiState.value.users.size)
            assertEquals(R.string.error_network, awaitItem().resourceId)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        vm.events.test {
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `initial loading remains visible while first refresh is running`() = runTest(main.dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeUserRepository(emptyList(), beforeRefresh = { gate.await() })
        val vm = viewModel(repository)
        collectState(vm)
        assertTrue(vm.uiState.value.isInitialLoading)
        runCurrent()
        assertTrue(vm.uiState.value.isInitialLoading)
        repeat(10) { vm.refresh() }
        runCurrent()
        assertEquals(1, repository.refreshCalls)
        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isInitialLoading)
    }

    @Test fun `retry without cache shows loading until refresh completes`() = runTest(main.dispatcher) {
        val repository = FakeUserRepository(emptyList(), AppResult.Failure(AppError.Network))
        val vm = viewModel(repository)
        collectState(vm)
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.initialError)

        val retryGate = CompletableDeferred<Unit>()
        repository.refreshResult = AppResult.Success(Unit)
        repository.beforeRefresh = { retryGate.await() }
        vm.refresh()
        runCurrent()

        assertTrue(vm.uiState.value.isInitialLoading)
        assertNull(vm.uiState.value.initialError)

        retryGate.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isInitialLoading)
    }

    @Test fun `fast refresh failure waits for cache before deciding to show snackbar`() = runTest(main.dispatcher) {
        val repository = DelayedCacheRepository()
        val vm = viewModel(repository)
        collectState(vm)
        runCurrent()

        assertEquals(0, repository.refreshCalls)

        vm.events.test {
            repository.users.emit(listOf(sampleUser()))
            advanceUntilIdle()

            assertEquals(1, repository.refreshCalls)
            assertTrue(vm.uiState.value.hasCachedUsers)
            assertEquals(R.string.error_network, awaitItem().resourceId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun viewModel(repo: UserRepository) = UserListViewModel(
        ObserveUsersUseCase(repo),
        RefreshUsersUseCase(repo),
        main.dispatcher,
    )

    private fun TestScope.collectState(viewModel: UserListViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
    }
}

private class DelayedCacheRepository : UserRepository {
    val users = MutableSharedFlow<List<User>>()
    var refreshCalls = 0

    override fun observeUsers(): Flow<List<User>> = users
    override fun observeUser(userId: Int): Flow<User?> = MutableStateFlow(null)
    override suspend fun refreshUsers(): AppResult<Unit> {
        refreshCalls++
        return AppResult.Failure(AppError.Network)
    }
    override suspend fun setFavorite(userId: Int, favorite: Boolean) = AppResult.Success(Unit)
    override suspend fun saveNote(userId: Int, note: String) = AppResult.Success(Unit)
    override suspend fun deleteNote(userId: Int) = AppResult.Success(Unit)
}

private class FakeUserRepository(
    initial: List<User>,
    var refreshResult: AppResult<Unit> = AppResult.Success(Unit),
    var beforeRefresh: suspend () -> Unit = {},
) : UserRepository {
    var refreshCalls = 0
    private val users = MutableStateFlow(initial)
    override fun observeUsers(): Flow<List<User>> = users
    override fun observeUser(userId: Int) = users.map { list -> list.firstOrNull { it.id == userId } }
    override suspend fun refreshUsers(): AppResult<Unit> { refreshCalls++; beforeRefresh(); return refreshResult }
    override suspend fun setFavorite(userId: Int, favorite: Boolean) = AppResult.Success(Unit)
    override suspend fun saveNote(userId: Int, note: String) = AppResult.Success(Unit)
    override suspend fun deleteNote(userId: Int) = AppResult.Success(Unit)
}
