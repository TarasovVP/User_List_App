package com.example.userlistapp

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.userlistapp.core.quality.AppQualityMonitor
import com.example.userlistapp.core.quality.JankMonitor
import com.example.userlistapp.domain.model.SessionState
import com.example.userlistapp.feature.account.AccountImplementationSelector
import com.example.userlistapp.navigation.AppNavigation
import com.example.userlistapp.settings.SettingsFeatureLauncher
import com.example.userlistapp.settings.SettingsDeliveryFeedback
import com.example.userlistapp.ui.theme.UserListTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    @Inject
    lateinit var qualityMonitor: AppQualityMonitor
    @Inject
    lateinit var accountImplementationSelector: AccountImplementationSelector
    private lateinit var jankMonitor: JankMonitor
    private lateinit var settingsFeatureLauncher: SettingsFeatureLauncher
    private val settingsConfirmationLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (::settingsFeatureLauncher.isInitialized) {
            settingsFeatureLauncher.onConfirmationResult(result.resultCode == Activity.RESULT_OK)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { viewModel.sessionState.value is SessionState.Initializing }
        settingsFeatureLauncher = SettingsFeatureLauncher(this, settingsConfirmationLauncher)
        enableEdgeToEdge()
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val session by viewModel.sessionState.collectAsStateWithLifecycle()
            val settingsInstallState by
                settingsFeatureLauncher.state.collectAsStateWithLifecycle()
            UserListTheme(settings.themeMode) {
                if (session !is SessionState.Initializing) {
                    AppNavigation(
                        session = session,
                        accountImplementation = accountImplementationSelector.selected(),
                        onOpenSettings = settingsFeatureLauncher::open,
                    )
                }
                SettingsDeliveryFeedback(
                    state = settingsInstallState,
                    onRetry = settingsFeatureLauncher::retry,
                    onCancel = settingsFeatureLauncher::cancel,
                    onDismiss = settingsFeatureLauncher::dismiss,
                )
            }
        }
        jankMonitor = JankMonitor(window, qualityMonitor)
    }

    override fun onResume() {
        super.onResume()
        if (::jankMonitor.isInitialized) jankMonitor.resume()
    }

    override fun onPause() {
        if (::jankMonitor.isInitialized) jankMonitor.pause()
        super.onPause()
    }
}
