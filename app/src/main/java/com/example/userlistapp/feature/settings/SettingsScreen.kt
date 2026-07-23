package com.example.userlistapp.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.userlistapp.R
import com.example.userlistapp.domain.model.SyncState
import com.example.userlistapp.domain.model.ThemeMode
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsRoute(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbar.showSnackbar(it.resolve(context)) }
    }
    SettingsScreen(state, onBack, viewModel::setTheme, viewModel::setBackgroundSync, snackbar)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onSync: (Boolean) -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val lastSuccessfulSync = state.settings.lastSuccessfulSync
    val formattedLastSuccessfulSync = remember(lastSuccessfulSync) {
        lastSuccessfulSync?.let { timestamp ->
            DateFormat.getDateTimeInstance().format(Date(timestamp))
        }
    }
    val lastSuccessfulSyncText = formattedLastSuccessfulSync ?: stringResource(R.string.never)
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.settings)) }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
    }) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleMedium)
            ThemeMode.entries.forEach { mode ->
                Row(
                    Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = state.settings.themeMode == mode, onClick = { onTheme(mode) })
                    Text(stringResource(when (mode) { ThemeMode.SYSTEM -> R.string.theme_system; ThemeMode.LIGHT -> R.string.theme_light; ThemeMode.DARK -> R.string.theme_dark }))
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.background_sync), modifier = Modifier.weight(1f))
                Switch(state.settings.backgroundSyncEnabled, onSync)
            }
            Text(
                stringResource(
                    R.string.label_value,
                    stringResource(R.string.last_sync),
                    lastSuccessfulSyncText,
                ),
            )
            Text(
                stringResource(
                    R.string.label_value,
                    stringResource(R.string.work_state),
                    syncStateText(state.syncState),
                ),
            )
        }
    }
}

@Composable
private fun syncStateText(state: SyncState): String = stringResource(when (state) {
    SyncState.IDLE -> R.string.work_idle
    SyncState.RUNNING -> R.string.work_running
    SyncState.SUCCEEDED -> R.string.work_succeeded
    SyncState.FAILED -> R.string.work_failed
})
