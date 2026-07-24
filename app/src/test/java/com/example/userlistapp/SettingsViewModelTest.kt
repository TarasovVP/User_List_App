package com.example.userlistapp

import app.cash.turbine.test
import com.example.userlistapp.domain.model.AppSettings
import com.example.userlistapp.domain.model.SyncState
import com.example.userlistapp.domain.model.ThemeMode
import com.example.userlistapp.domain.repository.SettingsRepository
import com.example.userlistapp.domain.repository.SyncScheduler
import com.example.userlistapp.feature.settings.SettingsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test fun `theme and background sync changes are persisted`() = runTest(main.dispatcher) {
        val repository = RecordingSettingsRepository()
        val scheduler = RecordingScheduler()
        val viewModel = SettingsViewModel(repository, scheduler)
        collectState(viewModel)
        advanceUntilIdle()

        viewModel.setTheme(ThemeMode.DARK)
        viewModel.setBackgroundSync(false)
        advanceUntilIdle()

        assertEquals(ThemeMode.DARK, repository.state.value.themeMode)
        assertEquals(false, repository.state.value.backgroundSyncEnabled)
        assertEquals(repository.state.value, viewModel.uiState.value.settings)
        // Scheduling is centralized in SyncCoordinator and observes the persisted value.
        assertEquals(emptyList<Boolean>(), scheduler.enabledValues)
    }

    @Test fun `ui state combines persisted settings with scheduler state`() = runTest(main.dispatcher) {
        val repository = RecordingSettingsRepository()
        val scheduler = RecordingScheduler()
        val viewModel = SettingsViewModel(repository, scheduler)
        collectState(viewModel)

        repository.state.value = AppSettings(ThemeMode.LIGHT, backgroundSyncEnabled = false, lastSuccessfulSync = 42)
        scheduler.state.value = SyncState.RUNNING
        advanceUntilIdle()

        assertEquals(repository.state.value, viewModel.uiState.value.settings)
        assertEquals(SyncState.RUNNING, viewModel.uiState.value.syncState)
    }

    @Test fun `background sync switch updates optimistically before persistence completes`() = runTest(main.dispatcher) {
        val repository = DelayedSettingsRepository()
        val viewModel = SettingsViewModel(repository, RecordingScheduler())
        collectState(viewModel)
        advanceUntilIdle()

        viewModel.setBackgroundSync(false)
        runCurrent()

        assertEquals(false, viewModel.uiState.value.settings.backgroundSyncEnabled)
        assertEquals(true, repository.state.value.backgroundSyncEnabled)

        repository.allowWrite.complete(Unit)
        advanceUntilIdle()
        assertEquals(false, repository.state.value.backgroundSyncEnabled)
        assertEquals(false, viewModel.uiState.value.settings.backgroundSyncEnabled)
    }

    @Test fun `failed persistence does not update scheduler and emits error`() = runTest(main.dispatcher) {
        val scheduler = RecordingScheduler()
        val viewModel = SettingsViewModel(FailingSettingsRepository(), scheduler)

        viewModel.events.test {
            viewModel.setBackgroundSync(false)
            advanceUntilIdle()

            assertEquals(R.string.error_storage, awaitItem().resourceId)
            assertEquals(0, scheduler.setEnabledCalls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun TestScope.collectState(viewModel: SettingsViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
    }
}

private class RecordingSettingsRepository : SettingsRepository {
    val state = MutableStateFlow(AppSettings())
    override val settings: Flow<AppSettings> = state
    override suspend fun setTheme(mode: ThemeMode) {
        state.value = state.value.copy(themeMode = mode)
    }
    override suspend fun setBackgroundSync(enabled: Boolean) {
        state.value = state.value.copy(backgroundSyncEnabled = enabled)
    }
    override suspend fun setLastSuccessfulSync(timestamp: Long) {
        state.value = state.value.copy(lastSuccessfulSync = timestamp)
    }
}

private class FailingSettingsRepository : SettingsRepository {
    override val settings: Flow<AppSettings> = MutableStateFlow(AppSettings())
    override suspend fun setTheme(mode: ThemeMode) = throw IOException("write failed")
    override suspend fun setBackgroundSync(enabled: Boolean) = throw IOException("write failed")
    override suspend fun setLastSuccessfulSync(timestamp: Long) = Unit
}

private class DelayedSettingsRepository : SettingsRepository {
    val state = MutableStateFlow(AppSettings())
    val allowWrite = CompletableDeferred<Unit>()
    override val settings: Flow<AppSettings> = state
    override suspend fun setTheme(mode: ThemeMode) = Unit
    override suspend fun setBackgroundSync(enabled: Boolean) {
        allowWrite.await()
        state.value = state.value.copy(backgroundSyncEnabled = enabled)
    }
    override suspend fun setLastSuccessfulSync(timestamp: Long) = Unit
}

private class RecordingScheduler : SyncScheduler {
    val enabledValues = mutableListOf<Boolean>()
    val setEnabledCalls: Int get() = enabledValues.size
    val state = MutableStateFlow(SyncState.IDLE)
    override fun observeState(): Flow<SyncState> = state
    override fun setEnabled(enabled: Boolean) {
        enabledValues += enabled
    }
}
