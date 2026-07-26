package com.example.userlistapp.settings

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.userlistapp.R
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus

class SettingsFeatureLauncher(
    private val activity: Activity,
    private val manager: SplitInstallManager = SplitInstallManagerFactory.create(activity),
) : DefaultLifecycleObserver {
    private var installSessionId: Int? = null
    private val listener = SplitInstallStateUpdatedListener(::onInstallStateChanged)

    init {
        (activity as? LifecycleOwner)?.lifecycle?.addObserver(this)
    }

    fun open() {
        if (isInstalled()) {
            launchSettings()
            return
        }
        if (installSessionId != null) return

        Toast.makeText(activity, R.string.settings_installing, Toast.LENGTH_SHORT).show()
        manager.registerListener(listener)
        manager.startInstall(
            SplitInstallRequest.newBuilder().addModule(MODULE_NAME).build()
        ).addOnSuccessListener { sessionId ->
            installSessionId = sessionId
        }.addOnFailureListener {
            finishInstallWithError()
        }
    }

    private fun onInstallStateChanged(state: SplitInstallSessionState) {
        if (state.sessionId() != installSessionId) return
        when (state.status()) {
            SplitInstallSessionStatus.INSTALLED -> {
                finishInstall()
                launchSettings()
            }

            SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION -> {
                if (!manager.startConfirmationDialogForResult(
                        state,
                        activity,
                        CONFIRMATION_REQUEST_CODE,
                    )
                ) {
                    finishInstallWithError()
                }
            }

            SplitInstallSessionStatus.CANCELED,
            SplitInstallSessionStatus.CANCELING,
            SplitInstallSessionStatus.FAILED,
            -> finishInstallWithError()
        }
    }

    private fun isInstalled(): Boolean =
        MODULE_NAME in manager.installedModules ||
            runCatching { activity.classLoader.loadClass(SETTINGS_ACTIVITY_CLASS) }.isSuccess

    private fun launchSettings() {
        activity.startActivity(Intent().setClassName(activity, SETTINGS_ACTIVITY_CLASS))
    }

    private fun finishInstallWithError() {
        finishInstall()
        Toast.makeText(activity, R.string.settings_install_failed, Toast.LENGTH_LONG).show()
    }

    private fun finishInstall() {
        installSessionId = null
        manager.unregisterListener(listener)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        manager.unregisterListener(listener)
        owner.lifecycle.removeObserver(this)
    }

    private companion object {
        const val MODULE_NAME = "settings"
        const val SETTINGS_ACTIVITY_CLASS =
            "com.example.userlistapp.feature.settings.SettingsActivity"
        const val CONFIRMATION_REQUEST_CODE = 8_101
    }
}
