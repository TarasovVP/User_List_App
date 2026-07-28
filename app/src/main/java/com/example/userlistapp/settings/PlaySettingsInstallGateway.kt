package com.example.userlistapp.settings

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus

internal class PlaySettingsInstallGateway(
    private val manager: SplitInstallManager,
    private val confirmationLauncher: ActivityResultLauncher<IntentSenderRequest>,
    private val classLoader: ClassLoader,
) : SettingsInstallGateway {
    private val listeners =
        mutableMapOf<(InstallSession) -> Unit, SplitInstallStateUpdatedListener>()
    private val confirmationStates = mutableMapOf<Int, SplitInstallSessionState>()

    override fun isInstalled(): Boolean =
        SettingsInstallCoordinator.MODULE_NAME in manager.installedModules ||
                runCatching { classLoader.loadClass(SETTINGS_ACTIVITY_CLASS) }.isSuccess

    override fun registerListener(listener: (InstallSession) -> Unit) {
        val playListener = SplitInstallStateUpdatedListener { state ->
            confirmationStates[state.sessionId()] = state
            listener(state.toInstallSession())
        }
        listeners[listener] = playListener
        manager.registerListener(playListener)
    }

    override fun unregisterListener(listener: (InstallSession) -> Unit) {
        listeners.remove(listener)?.let(manager::unregisterListener)
    }

    override fun startInstall(onSuccess: (Int) -> Unit, onFailure: (Int?) -> Unit) {
        manager.startInstall(
            SplitInstallRequest.newBuilder()
                .addModule(SettingsInstallCoordinator.MODULE_NAME)
                .build(),
        ).addOnSuccessListener(onSuccess)
            .addOnFailureListener { onFailure(it.playErrorCode()) }
    }

    override fun activeSessions(
        onSuccess: (List<InstallSession>) -> Unit,
        onFailure: (Int?) -> Unit,
    ) {
        manager.sessionStates
            .addOnSuccessListener { states ->
                states.forEach { confirmationStates[it.sessionId()] = it }
                onSuccess(states.map(SplitInstallSessionState::toInstallSession))
            }
            .addOnFailureListener { onFailure(it.playErrorCode()) }
    }

    override fun requestConfirmation(sessionId: Int): Boolean {
        val state = confirmationStates[sessionId] ?: return false
        return manager.startConfirmationDialogForResult(state, confirmationLauncher)
    }

    override fun cancel(sessionId: Int, onFailure: (Int?) -> Unit) {
        manager.cancelInstall(sessionId).addOnFailureListener { onFailure(it.playErrorCode()) }
    }
}

private fun SplitInstallSessionState.toInstallSession() = InstallSession(
    id = sessionId(),
    status = when (status()) {
        SplitInstallSessionStatus.PENDING -> InstallStatus.PENDING
        SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION ->
            InstallStatus.REQUIRES_USER_CONFIRMATION

        SplitInstallSessionStatus.DOWNLOADING -> InstallStatus.DOWNLOADING
        SplitInstallSessionStatus.DOWNLOADED -> InstallStatus.DOWNLOADED
        SplitInstallSessionStatus.INSTALLING -> InstallStatus.INSTALLING
        SplitInstallSessionStatus.INSTALLED -> InstallStatus.INSTALLED
        SplitInstallSessionStatus.CANCELING -> InstallStatus.CANCELING
        SplitInstallSessionStatus.CANCELED -> InstallStatus.CANCELED
        SplitInstallSessionStatus.FAILED -> InstallStatus.FAILED
        else -> InstallStatus.UNKNOWN
    },
    modules = moduleNames().toSet(),
    downloadedBytes = bytesDownloaded(),
    totalBytes = totalBytesToDownload(),
    errorCode = errorCode(),
)

private fun Throwable.playErrorCode(): Int? =
    (this as? com.google.android.play.core.splitinstall.SplitInstallException)?.errorCode

private const val SETTINGS_ACTIVITY_CLASS =
    "com.example.userlistapp.feature.settings.SettingsActivity"
