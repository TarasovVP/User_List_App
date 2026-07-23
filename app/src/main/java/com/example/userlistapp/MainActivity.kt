package com.example.userlistapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.userlistapp.feature.settings.SettingsRoute
import com.example.userlistapp.feature.users.details.UserDetailsRoute
import com.example.userlistapp.feature.users.list.UserListRoute
import com.example.userlistapp.navigation.SettingsDestination
import com.example.userlistapp.navigation.UserDetailsDestination
import com.example.userlistapp.navigation.UsersDestination
import com.example.userlistapp.ui.theme.UserListTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            UserListTheme(settings.themeMode) {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = UsersDestination) {
                    composable<UsersDestination> {
                        UserListRoute(
                            onUser = { nav.navigate(UserDetailsDestination(it)) },
                            onSettings = { nav.navigate(SettingsDestination) },
                        )
                    }
                    composable<UserDetailsDestination> { UserDetailsRoute(onBack = nav::navigateUp) }
                    composable<SettingsDestination> { SettingsRoute(onBack = nav::navigateUp) }
                }
            }
        }
    }
}
