package com.example.userlistapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.userlistapp.domain.model.AppSettings
import com.example.userlistapp.domain.model.SessionState
import com.example.userlistapp.domain.usecase.ObserveAuthSessionUseCase
import com.example.userlistapp.domain.usecase.ObserveSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    observeSettings: ObserveSettingsUseCase,
    observeSession: ObserveAuthSessionUseCase,
) : ViewModel() {
    val settings = observeSettings().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )
    val sessionState = observeSession().stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SessionState.Initializing,
    )
}
