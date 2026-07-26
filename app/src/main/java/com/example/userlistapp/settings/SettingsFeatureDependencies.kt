package com.example.userlistapp.settings

import com.example.userlistapp.domain.usecase.ObserveSettingsUseCase
import com.example.userlistapp.domain.usecase.ObserveSyncStateUseCase
import com.example.userlistapp.domain.usecase.SetBackgroundSyncUseCase
import com.example.userlistapp.domain.usecase.SetThemeUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SettingsFeatureDependencies {
    fun observeSettings(): ObserveSettingsUseCase
    fun observeSyncState(): ObserveSyncStateUseCase
    fun setBackgroundSync(): SetBackgroundSyncUseCase
    fun setTheme(): SetThemeUseCase
}
