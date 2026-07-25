package com.example.userlistapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.userlistapp.core.quality.AppQualityMonitor
import com.example.userlistapp.core.quality.JankMonitor
import com.example.userlistapp.domain.model.SessionState
import com.example.userlistapp.navigation.AppNavigation
import com.example.userlistapp.ui.theme.UserListTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()
    @Inject
    lateinit var qualityMonitor: AppQualityMonitor
    private lateinit var jankMonitor: JankMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { viewModel.sessionState.value is SessionState.Initializing }
        enableEdgeToEdge()
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val session by viewModel.sessionState.collectAsStateWithLifecycle()
            UserListTheme(settings.themeMode) {
                if (session !is SessionState.Initializing) AppNavigation(session)
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
