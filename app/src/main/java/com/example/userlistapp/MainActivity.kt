package com.example.userlistapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.userlistapp.domain.model.SessionState
import com.example.userlistapp.feature.account.AccountScreen
import com.example.userlistapp.feature.account.AuthViewModel
import com.example.userlistapp.feature.account.AuthenticationRequired
import com.example.userlistapp.feature.account.SignInSheet
import com.example.userlistapp.feature.settings.SettingsRoute
import com.example.userlistapp.feature.users.details.UserDetailsRoute
import com.example.userlistapp.feature.users.list.UserListRoute
import com.example.userlistapp.navigation.AccountDestination
import com.example.userlistapp.navigation.SettingsDestination
import com.example.userlistapp.navigation.UserDetailsDestination
import com.example.userlistapp.navigation.UsersDestination
import com.example.userlistapp.ui.theme.UserListTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

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
    }
}

@Composable
private fun AppNavigation(session: SessionState) {
    val nav = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()
    val auth by authViewModel.uiState.collectAsStateWithLifecycle()
    var showSignIn by remember { mutableStateOf(false) }
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val topLevel =
        destination?.hasRoute<UsersDestination>() == true || destination?.hasRoute<AccountDestination>() == true

    LaunchedEffect(session) {
        when (session) {
            is SessionState.SignedIn -> {
                showSignIn = false
                nav.navigate(UsersDestination) {
                    popUpTo(nav.graph.findStartDestination().id) { inclusive = true }
                    launchSingleTop = true
                }
            }

            SessionState.SignedOut -> nav.navigate(AccountDestination) {
                popUpTo(nav.graph.findStartDestination().id) { inclusive = true }
                launchSingleTop = true
            }

            SessionState.Initializing -> Unit
        }
    }

    Scaffold(
        bottomBar = {
            if (topLevel) NavigationBar {
                NavigationBarItem(
                    selected = destination.hasRoute<UsersDestination>(),
                    onClick = { nav.navigateTopLevel(UsersDestination) },
                    icon = { Icon(Icons.Default.People, null) },
                    label = { Text(stringResource(R.string.users_nav)) },
                )
                NavigationBarItem(
                    selected = destination.hasRoute<AccountDestination>(),
                    onClick = { nav.navigateTopLevel(AccountDestination) },
                    icon = { Icon(Icons.Default.AccountCircle, null) },
                    label = { Text(stringResource(R.string.account_title)) },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = if (session is SessionState.SignedIn) UsersDestination else AccountDestination,
            modifier = Modifier.padding(padding),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable<UsersDestination> {
                if (session is SessionState.SignedIn) {
                    UserListRoute(
                        onUser = { nav.navigate(UserDetailsDestination(it)) },
                        onSettings = { nav.navigate(SettingsDestination) },
                    )
                } else AuthenticationRequired(
                    onSignIn = { showSignIn = true },
                    onSettings = { nav.navigate(SettingsDestination) },
                )
            }
            composable<AccountDestination> {
                AccountScreen(
                    state = auth,
                    onOpenSignIn = { authViewModel.clearLoginError(); showSignIn = true },
                    onRetry = authViewModel::retryAccount,
                    onSignOut = authViewModel::signOut,
                    onImportAvatar = authViewModel::importLocalAvatar,
                    onRemoveAvatar = authViewModel::removeLocalAvatar,
                    onClearAvatarError = authViewModel::clearAvatarError,
                    onSettings = { nav.navigate(SettingsDestination) },
                )
            }
            composable<UserDetailsDestination> {
                if (session is SessionState.SignedIn) UserDetailsRoute(onBack = nav::navigateUp)
                else AuthenticationRequired(onSignIn = { showSignIn = true })
            }
            composable<SettingsDestination> { SettingsRoute(onBack = nav::navigateUp) }
        }
    }
    if (showSignIn) {
        SignInSheet(
            state = auth,
            onDismiss = { showSignIn = false; authViewModel.clearLoginError() },
            onCredentialsChanged = authViewModel::clearLoginError,
            onSubmit = authViewModel::signIn,
        )
    }
}

private fun androidx.navigation.NavHostController.navigateTopLevel(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
