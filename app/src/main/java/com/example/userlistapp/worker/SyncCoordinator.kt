package com.example.userlistapp.worker

import com.example.userlistapp.domain.model.SessionState
import com.example.userlistapp.domain.repository.AuthSessionRepository
import com.example.userlistapp.domain.repository.SettingsRepository
import com.example.userlistapp.domain.repository.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncCoordinator @Inject constructor(
    private val settings: SettingsRepository,
    private val session: AuthSessionRepository,
    private val scheduler: SyncScheduler,
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(settings.settings, session.sessionState) { appSettings, sessionState ->
                appSettings.backgroundSyncEnabled && sessionState is SessionState.SignedIn
            }.distinctUntilChanged().collect(scheduler::setEnabled)
        }
    }
}
