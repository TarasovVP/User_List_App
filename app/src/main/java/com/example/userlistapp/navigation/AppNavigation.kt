package com.example.userlistapp.navigation

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.userlistapp.R
import com.example.userlistapp.core.navigation.AccountDestination
import com.example.userlistapp.core.navigation.UserDetailsDestination
import com.example.userlistapp.core.navigation.UsersDestination
import com.example.userlistapp.core.quality.TrackJankStates
import com.example.userlistapp.domain.model.SessionState
import com.example.userlistapp.feature.account.AccountScreen
import com.example.userlistapp.feature.account.AccountImplementation
import com.example.userlistapp.feature.account.AuthViewModel
import com.example.userlistapp.feature.account.AuthUiState
import com.example.userlistapp.feature.account.AuthenticationRequired
import com.example.userlistapp.feature.account.SignInSheet
import com.example.userlistapp.feature.account.modular.AccountContent
import com.example.userlistapp.feature.account.modular.AccountFeatureActions
import com.example.userlistapp.feature.account.modular.AccountFeatureState
import com.example.userlistapp.feature.account.modular.AccountSession
import com.example.userlistapp.feature.account.modular.ModularAccountScreen
import com.example.userlistapp.feature.users.details.UserDetailsRoute
import com.example.userlistapp.feature.users.list.UserListRoute

@Composable
fun AppNavigation(
    session: SessionState,
    accountImplementation: AccountImplementation = AccountImplementation.LEGACY,
    onOpenSettings: () -> Unit,
) {
    val nav = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()
    val auth by authViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showSignIn by rememberSaveable { mutableStateOf(false) }
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val topLevel =
        destination?.hasRoute<UsersDestination>() == true || destination?.hasRoute<AccountDestination>() == true

    LaunchedEffect(session) {
        when (session) {
            is SessionState.SignedIn -> {
                showSignIn = false
                if (nav.currentDestination?.hasRoute<UsersDestination>() != true) {
                    nav.navigate(UsersDestination) {
                        popUpTo(nav.graph.findStartDestination().id) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }

            SessionState.SignedOut -> {
                if (nav.currentDestination?.hasRoute<AccountDestination>() != true) {
                    nav.navigate(AccountDestination) {
                        popUpTo(nav.graph.findStartDestination().id) { inclusive = true }
                        launchSingleTop = true
                    }
                }
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
        ) {
            composable<UsersDestination> {
                if (session is SessionState.SignedIn) {
                    UserListRoute(
                        onUser = { nav.navigate(UserDetailsDestination(it)) },
                        onSettings = onOpenSettings,
                    )
                } else AuthenticationRequired(
                    onSignIn = { showSignIn = true },
                    onSettings = onOpenSettings,
                )
            }
            composable<AccountDestination> {
                TrackJankStates(
                    mapOf(
                        SCREEN_STATE_KEY to ACCOUNT_SCREEN_VALUE,
                        PHASE_STATE_KEY to auth.accountQualityPhase,
                        INTERACTION_STATE_KEY to auth.accountQualityInteraction,
                        VISIBLE_USERS_STATE_KEY to NOT_APPLICABLE_VALUE,
                    ),
                )
                val openSignIn = {
                    authViewModel.clearLoginError()
                    showSignIn = true
                }
                if (accountImplementation == AccountImplementation.MODULAR) {
                    ModularAccountScreen(
                        state = AccountFeatureState(
                            session = when (auth.session) {
                                SessionState.Initializing -> AccountSession.Initializing
                                SessionState.SignedOut -> AccountSession.SignedOut
                                is SessionState.SignedIn -> AccountSession.SignedIn
                            },
                            account = auth.account?.let {
                                AccountContent(
                                    username = it.username,
                                    fullName = it.fullName,
                                    email = it.email,
                                    remoteImageUrl = it.remoteImageUrl,
                                )
                            },
                            localAvatarUri = auth.localAvatarUri,
                            isAccountLoading = auth.isAccountLoading,
                            accountError = auth.accountError?.resolve(context),
                            avatarError = auth.avatarError?.resolve(context),
                        ),
                        actions = AccountFeatureActions(
                            onOpenSignIn = openSignIn,
                            onRetry = authViewModel::retryAccount,
                            onSignOut = authViewModel::signOut,
                            onImportAvatar = authViewModel::importLocalAvatar,
                            onRemoveAvatar = authViewModel::removeLocalAvatar,
                            onClearAvatarError = authViewModel::clearAvatarError,
                            onSettings = onOpenSettings,
                        ),
                    )
                } else {
                    AccountScreen(
                        state = auth,
                        onOpenSignIn = openSignIn,
                        onRetry = authViewModel::retryAccount,
                        onSignOut = authViewModel::signOut,
                        onImportAvatar = authViewModel::importLocalAvatar,
                        onRemoveAvatar = authViewModel::removeLocalAvatar,
                        onClearAvatarError = authViewModel::clearAvatarError,
                        onSettings = onOpenSettings,
                    )
                }
            }
            composable<UserDetailsDestination> {
                if (session is SessionState.SignedIn) UserDetailsRoute(onBack = nav::navigateUp)
                else AuthenticationRequired(onSignIn = { showSignIn = true })
            }
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

private val AuthUiState.accountQualityPhase: String
    get() = when {
        session is SessionState.Initializing -> INITIALIZING_PHASE_VALUE
        isSigningIn -> SIGNING_IN_PHASE_VALUE
        session is SessionState.SignedOut -> SIGNED_OUT_PHASE_VALUE
        isAccountLoading -> LOADING_PHASE_VALUE
        accountError != null -> ERROR_PHASE_VALUE
        account != null -> CONTENT_PHASE_VALUE
        else -> EMPTY_PHASE_VALUE
    }

private val AuthUiState.accountQualityInteraction: String
    get() = when {
        isSigningIn -> SIGN_IN_INTERACTION_VALUE
        localAvatarUri != null -> LOCAL_AVATAR_INTERACTION_VALUE
        else -> BROWSING_INTERACTION_VALUE
    }

private fun NavHostController.navigateTopLevel(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private const val SCREEN_STATE_KEY = "screen"
private const val PHASE_STATE_KEY = "phase"
private const val INTERACTION_STATE_KEY = "interaction"
private const val VISIBLE_USERS_STATE_KEY = "visible_users"
private const val ACCOUNT_SCREEN_VALUE = "account"
private const val NOT_APPLICABLE_VALUE = "not_applicable"
private const val INITIALIZING_PHASE_VALUE = "initializing"
private const val SIGNING_IN_PHASE_VALUE = "signing_in"
private const val SIGNED_OUT_PHASE_VALUE = "signed_out"
private const val LOADING_PHASE_VALUE = "loading"
private const val ERROR_PHASE_VALUE = "error"
private const val CONTENT_PHASE_VALUE = "content"
private const val EMPTY_PHASE_VALUE = "empty"
private const val SIGN_IN_INTERACTION_VALUE = "sign_in"
private const val LOCAL_AVATAR_INTERACTION_VALUE = "local_avatar"
private const val BROWSING_INTERACTION_VALUE = "browsing"
