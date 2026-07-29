package com.example.userlistapp.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.userlistapp.R

@Composable
fun SettingsDeliveryFeedback(
    state: SettingsInstallState,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        SettingsInstallState.Idle,
        SettingsInstallState.Installed,
        SettingsInstallState.AwaitingConfirmation,
            -> Unit

        SettingsInstallState.Canceled,
        is SettingsInstallState.Failed,
            -> AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.settings_install_failed_title)) },
                text = {
                    Text(
                        stringResource(
                            if (state == SettingsInstallState.Canceled) {
                                R.string.settings_install_canceled
                            } else {
                                R.string.settings_install_failed
                            },
                        ),
                    )
                },
                confirmButton = {
                    Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                },
                dismissButton = {
                    OutlinedButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )

        else -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.settings_installing_title)) },
            text = {
                Column {
                    Text(deliveryStatusText(state))
                    when (state) {
                        is SettingsInstallState.Downloading -> {
                            val progress = if (state.totalBytes > 0) {
                                state.downloadedBytes.toFloat() / state.totalBytes
                            } else {
                                0f
                            }
                            LinearProgressIndicator(
                                progress = { progress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            )
                            Text(
                                stringResource(
                                    R.string.settings_install_progress,
                                    state.downloadedBytes,
                                    state.totalBytes,
                                ),
                            )
                        }

                        else -> LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                OutlinedButton(
                    onClick = onCancel,
                    enabled = state != SettingsInstallState.Canceling,
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun deliveryStatusText(state: SettingsInstallState): String = stringResource(
    when (state) {
        SettingsInstallState.Pending -> R.string.settings_install_pending
        is SettingsInstallState.Downloading -> R.string.settings_install_downloading
        SettingsInstallState.Installing -> R.string.settings_install_installing
        SettingsInstallState.Canceling -> R.string.settings_install_canceling
        else -> R.string.settings_installing
    },
)
