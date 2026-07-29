package com.example.userlistapp.settings

import android.app.Activity
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import kotlinx.coroutines.flow.StateFlow

internal class SettingsFeatureLauncher(
    private val activity: Activity,
    confirmationLauncher: ActivityResultLauncher<IntentSenderRequest>,
    gateway: SettingsInstallGateway = PlaySettingsInstallGateway(
        SplitInstallManagerFactory.create(activity),
        confirmationLauncher,
        activity.classLoader,
    ),
) : DefaultLifecycleObserver {
    private val coordinator = SettingsInstallCoordinator(
        gateway = gateway,
        launchSettings = {
            activity.startActivity(Intent().setClassName(activity, SETTINGS_ACTIVITY_CLASS))
        },
    )

    val state: StateFlow<SettingsInstallState> = coordinator.state

    init {
        (activity as? LifecycleOwner)?.lifecycle?.addObserver(this)
    }

    fun open() = coordinator.open()
    fun retry() = coordinator.retry()
    fun cancel() = coordinator.cancel()
    fun dismiss() = coordinator.dismiss()
    fun onConfirmationResult(accepted: Boolean) = coordinator.confirmationResult(accepted)

    override fun onStart(owner: LifecycleOwner) = coordinator.start()

    override fun onStop(owner: LifecycleOwner) = coordinator.stop()

    override fun onDestroy(owner: LifecycleOwner) {
        coordinator.stop()
        owner.lifecycle.removeObserver(this)
    }
}

private const val SETTINGS_ACTIVITY_CLASS =
    "com.example.userlistapp.feature.settings.SettingsActivity"
