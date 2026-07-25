package com.example.userlistapp.domain.usecase

import com.example.userlistapp.domain.model.ThemeMode
import com.example.userlistapp.domain.repository.SettingsRepository
import com.example.userlistapp.domain.repository.SyncScheduler
import javax.inject.Inject

class ObserveSettingsUseCase @Inject constructor(private val repository: SettingsRepository) {
    operator fun invoke() = repository.settings
}

class SetThemeUseCase @Inject constructor(private val repository: SettingsRepository) {
    suspend operator fun invoke(mode: ThemeMode) = repository.setTheme(mode)
}

class SetBackgroundSyncUseCase @Inject constructor(private val repository: SettingsRepository) {
    suspend operator fun invoke(enabled: Boolean) = repository.setBackgroundSync(enabled)
}

class ObserveSyncStateUseCase @Inject constructor(private val scheduler: SyncScheduler) {
    operator fun invoke() = scheduler.observeState()
}
