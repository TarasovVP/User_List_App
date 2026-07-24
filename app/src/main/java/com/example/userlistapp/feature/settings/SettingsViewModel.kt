package com.example.userlistapp.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.userlistapp.core.common.AppError
import com.example.userlistapp.core.common.UiText
import com.example.userlistapp.core.common.toUiText
import com.example.userlistapp.domain.model.AppSettings
import com.example.userlistapp.domain.model.SyncState
import com.example.userlistapp.domain.model.ThemeMode
import com.example.userlistapp.domain.repository.SettingsRepository
import com.example.userlistapp.domain.repository.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(val settings: AppSettings = AppSettings(), val syncState: SyncState = SyncState.IDLE)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val scheduler: SyncScheduler,
) : ViewModel() {
    private val _events = MutableSharedFlow<UiText>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()
    private val pendingBackgroundSync = MutableStateFlow<Boolean?>(null)
    private var backgroundSyncWrite: Job? = null

    val uiState: StateFlow<SettingsUiState> = combine(
        repository.settings,
        scheduler.observeState(),
        pendingBackgroundSync,
    ) { settings, syncState, pendingSync ->
        SettingsUiState(
            settings = pendingSync?.let { settings.copy(backgroundSyncEnabled = it) } ?: settings,
            syncState = syncState,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setTheme(mode: ThemeMode) = persistSetting(update = { repository.setTheme(mode) })

    fun setBackgroundSync(enabled: Boolean) {
        backgroundSyncWrite?.cancel()
        pendingBackgroundSync.value = enabled
        backgroundSyncWrite = viewModelScope.launch {
            try {
                repository.setBackgroundSync(enabled)
                repository.settings.first { it.backgroundSyncEnabled == enabled }
                if (pendingBackgroundSync.value == enabled) pendingBackgroundSync.value = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                if (pendingBackgroundSync.value == enabled) pendingBackgroundSync.value = null
                _events.tryEmit(AppError.Storage.toUiText())
            }
        }
    }

    private fun persistSetting(update: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                update()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                _events.tryEmit(AppError.Storage.toUiText())
            }
        }
    }
}
