package com.example.userlistapp.feature.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.userlistapp.settings.SettingsFeatureDependencies
import com.example.userlistapp.ui.theme.UserListTheme
import dagger.hilt.android.EntryPointAccessors

class SettingsActivity : ComponentActivity() {
    private val dependencies: SettingsFeatureDependencies by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            SettingsFeatureDependencies::class.java,
        )
    }
    private val viewModel: SettingsViewModel by viewModels {
        settingsViewModelFactory(dependencies)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            UserListTheme(state.settings.themeMode) {
                SettingsRoute(onBack = ::finish, viewModel = viewModel)
            }
        }
    }
}

private fun settingsViewModelFactory(
    dependencies: SettingsFeatureDependencies,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        SettingsViewModel(
            dependencies.observeSettings(),
            dependencies.observeSyncState(),
            dependencies.setTheme(),
            dependencies.setBackgroundSync(),
        )
    }
}
