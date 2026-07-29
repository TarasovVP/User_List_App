package com.example.userlistapp.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface SettingsInstallState {
    data object Idle : SettingsInstallState
    data object Pending : SettingsInstallState
    data object AwaitingConfirmation : SettingsInstallState
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) :
        SettingsInstallState
    data object Installing : SettingsInstallState
    data object Canceling : SettingsInstallState
    data object Installed : SettingsInstallState
    data object Canceled : SettingsInstallState
    data class Failed(val errorCode: Int?) : SettingsInstallState
}

internal enum class InstallStatus {
    PENDING,
    REQUIRES_USER_CONFIRMATION,
    DOWNLOADING,
    DOWNLOADED,
    INSTALLING,
    INSTALLED,
    CANCELING,
    CANCELED,
    FAILED,
    UNKNOWN,
}

internal data class InstallSession(
    val id: Int,
    val status: InstallStatus,
    val modules: Set<String>,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val errorCode: Int? = null,
)

internal interface SettingsInstallGateway {
    fun isInstalled(): Boolean
    fun registerListener(listener: (InstallSession) -> Unit)
    fun unregisterListener(listener: (InstallSession) -> Unit)
    fun startInstall(onSuccess: (Int) -> Unit, onFailure: (Int?) -> Unit)
    fun activeSessions(onSuccess: (List<InstallSession>) -> Unit, onFailure: (Int?) -> Unit)
    fun requestConfirmation(sessionId: Int): Boolean
    fun cancel(sessionId: Int, onFailure: (Int?) -> Unit)
}

internal class SettingsInstallCoordinator(
    private val gateway: SettingsInstallGateway,
    private val launchSettings: () -> Unit,
) {
    private val mutableState = MutableStateFlow<SettingsInstallState>(SettingsInstallState.Idle)
    val state: StateFlow<SettingsInstallState> = mutableState.asStateFlow()

    private var sessionId: Int? = null
    private var requestInFlight = false
    private var cancelWhenSessionStarts = false
    private var confirmationRequested = false
    private var openWhenInstalled = false
    private var listening = false
    private val listener: (InstallSession) -> Unit = ::onSession

    fun start() {
        if (!listening) {
            gateway.registerListener(listener)
            listening = true
        }
        recover()
    }

    fun stop() {
        if (listening) {
            gateway.unregisterListener(listener)
            listening = false
        }
    }

    fun open() {
        if (gateway.isInstalled()) {
            mutableState.value = SettingsInstallState.Installed
            launchSettings()
            return
        }
        openWhenInstalled = true
        if (sessionId != null || requestInFlight) return
        requestInstall()
    }

    fun retry() {
        if (mutableState.value is SettingsInstallState.Failed ||
            mutableState.value == SettingsInstallState.Canceled
        ) {
            open()
        }
    }

    fun dismiss() {
        if (mutableState.value is SettingsInstallState.Failed ||
            mutableState.value == SettingsInstallState.Canceled
        ) {
            mutableState.value = SettingsInstallState.Idle
        }
    }

    fun cancel() {
        val activeSession = sessionId
        if (activeSession == null && requestInFlight) {
            cancelWhenSessionStarts = true
            mutableState.value = SettingsInstallState.Canceling
            return
        }
        if (activeSession == null) return
        mutableState.value = SettingsInstallState.Canceling
        gateway.cancel(activeSession) { error -> fail(error) }
    }

    fun confirmationResult(accepted: Boolean) {
        confirmationRequested = false
        if (!accepted) {
            sessionId = null
            openWhenInstalled = false
            mutableState.value = SettingsInstallState.Canceled
        }
    }

    private fun requestInstall() {
        requestInFlight = true
        mutableState.value = SettingsInstallState.Pending
        gateway.startInstall(
            onSuccess = { id ->
                requestInFlight = false
                sessionId = id
                if (cancelWhenSessionStarts) {
                    cancelWhenSessionStarts = false
                    gateway.cancel(id) { error -> fail(error) }
                }
            },
            onFailure = { error ->
                requestInFlight = false
                fail(error)
            },
        )
    }

    private fun recover() {
        if (gateway.isInstalled()) {
            sessionId = null
            requestInFlight = false
            mutableState.value = SettingsInstallState.Installed
            return
        }
        gateway.activeSessions(
            onSuccess = { sessions ->
                sessions.firstOrNull { MODULE_NAME in it.modules && it.status.isActive }
                    ?.let {
                        sessionId = it.id
                        openWhenInstalled = true
                        onSession(it)
                    }
                    ?: run {
                        if (sessionId == null && !requestInFlight) {
                            mutableState.value = SettingsInstallState.Idle
                        }
                    }
            },
            onFailure = { error ->
                if (sessionId == null && !requestInFlight && openWhenInstalled) fail(error)
            },
        )
    }

    private fun onSession(session: InstallSession) {
        if (MODULE_NAME !in session.modules) return
        val activeSession = sessionId
        if (activeSession != null && session.id != activeSession) return
        if (activeSession == null && !requestInFlight) return
        sessionId = session.id

        when (session.status) {
            InstallStatus.PENDING -> mutableState.value = SettingsInstallState.Pending
            InstallStatus.REQUIRES_USER_CONFIRMATION -> {
                mutableState.value = SettingsInstallState.AwaitingConfirmation
                if (!confirmationRequested) {
                    confirmationRequested = true
                    if (!gateway.requestConfirmation(session.id)) fail(null)
                }
            }

            InstallStatus.DOWNLOADING -> mutableState.value = SettingsInstallState.Downloading(
                session.downloadedBytes,
                session.totalBytes,
            )

            InstallStatus.DOWNLOADED,
            InstallStatus.INSTALLING,
                -> mutableState.value = SettingsInstallState.Installing

            InstallStatus.INSTALLED -> {
                sessionId = null
                requestInFlight = false
                confirmationRequested = false
                mutableState.value = SettingsInstallState.Installed
                if (openWhenInstalled) {
                    openWhenInstalled = false
                    launchSettings()
                }
            }

            InstallStatus.CANCELING -> mutableState.value = SettingsInstallState.Canceling
            InstallStatus.CANCELED -> {
                sessionId = null
                openWhenInstalled = false
                confirmationRequested = false
                mutableState.value = SettingsInstallState.Canceled
            }

            InstallStatus.FAILED -> fail(session.errorCode)
            InstallStatus.UNKNOWN -> Unit
        }
    }

    private fun fail(error: Int?) {
        sessionId = null
        requestInFlight = false
        cancelWhenSessionStarts = false
        confirmationRequested = false
        openWhenInstalled = false
        mutableState.value = SettingsInstallState.Failed(error)
    }

    private val InstallStatus.isActive: Boolean
        get() = this in setOf(
            InstallStatus.PENDING,
            InstallStatus.REQUIRES_USER_CONFIRMATION,
            InstallStatus.DOWNLOADING,
            InstallStatus.DOWNLOADED,
            InstallStatus.INSTALLING,
            InstallStatus.CANCELING,
        )

    internal companion object {
        const val MODULE_NAME = "settings"
    }
}
