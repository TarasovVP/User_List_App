package com.example.userlistapp

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import com.example.userlistapp.core.common.EMPTY
import com.example.userlistapp.domain.model.SessionState
import com.example.userlistapp.domain.model.ThemeMode
import com.example.userlistapp.domain.model.User
import com.example.userlistapp.feature.account.AccountScreen
import com.example.userlistapp.feature.account.AuthUiState
import com.example.userlistapp.feature.account.SignInContent
import com.example.userlistapp.feature.account.modular.AccountFeatureActions
import com.example.userlistapp.feature.account.modular.AccountFeatureState
import com.example.userlistapp.feature.account.modular.AccountSession
import com.example.userlistapp.feature.account.modular.ModularAccountScreen
import com.example.userlistapp.feature.users.details.UserDetailsScreen
import com.example.userlistapp.feature.users.details.UserDetailsUiState
import com.example.userlistapp.feature.users.list.UserListContentState
import com.example.userlistapp.feature.users.list.UserListScreen
import com.example.userlistapp.feature.users.list.UserListUiState
import com.example.userlistapp.ui.theme.UserListTheme
import org.junit.Rule
import org.junit.Test

class AccessibilityChecksTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun userListAndDetailsPassAutomatedAccessibilityChecks() {
        val user = accessibleUser()
        var showDetails by mutableStateOf(false)
        compose.setContent {
            UserListTheme(if (showDetails) ThemeMode.DARK else ThemeMode.LIGHT) {
                if (showDetails) {
                    UserDetailsScreen(
                        state = UserDetailsUiState(user = user, isLoading = false),
                        onBack = {},
                        onFavorite = {},
                        onNoteChanged = {},
                        onSaveNote = {},
                        onDeleteNote = {},
                    )
                } else {
                    UserListScreen(
                        state = UserListUiState(
                            users = listOf(user),
                            hasCachedUsers = true,
                            isInitialLoading = false,
                        ),
                        contentState = UserListContentState.Loaded,
                        onQuery = {},
                        onSort = {},
                        onFavoritesOnly = {},
                        onRefresh = {},
                        onUser = {},
                        onFavorite = {},
                        onSettings = {},
                        snackbar = SnackbarHostState(),
                    )
                }
            }
        }
        compose.enableAccessibilityChecks()
        compose.onRoot().tryPerformAccessibilityChecks()

        compose.runOnIdle { showDetails = true }
        compose.waitForIdle()
        compose.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun accountAndSignInPassAutomatedAccessibilityChecks() {
        var screen by mutableStateOf(AccountTestScreen.LEGACY)
        compose.setContent {
            UserListTheme(ThemeMode.LIGHT) {
                when (screen) {
                    AccountTestScreen.LEGACY -> AccountScreen(
                        state = AuthUiState(session = SessionState.SignedOut),
                        onOpenSignIn = {},
                        onRetry = {},
                        onSignOut = {},
                        onImportAvatar = {},
                        onRemoveAvatar = {},
                        onClearAvatarError = {},
                        onSettings = {},
                    )

                    AccountTestScreen.SIGN_IN -> SignInContent(
                        state = AuthUiState(session = SessionState.SignedOut),
                        onDismiss = {},
                        onCredentialsChanged = {},
                        onSubmit = { _, _ -> },
                    )

                    AccountTestScreen.MODULAR -> ModularAccountScreen(
                        state = AccountFeatureState(session = AccountSession.SignedOut),
                        actions = AccountFeatureActions(
                            onOpenSignIn = {},
                            onRetry = {},
                            onSignOut = {},
                            onImportAvatar = {},
                            onRemoveAvatar = {},
                            onClearAvatarError = {},
                            onSettings = {},
                        ),
                    )
                }
            }
        }
        compose.enableAccessibilityChecks()
        compose.onRoot().tryPerformAccessibilityChecks()

        compose.runOnIdle { screen = AccountTestScreen.SIGN_IN }
        compose.waitForIdle()
        compose.onRoot().tryPerformAccessibilityChecks()

        compose.runOnIdle { screen = AccountTestScreen.MODULAR }
        compose.waitForIdle()
        compose.onRoot().tryPerformAccessibilityChecks()
    }
}

private enum class AccountTestScreen { LEGACY, SIGN_IN, MODULAR }

private fun accessibleUser() = User(
    id = 1,
    firstName = "Ada",
    lastName = "Lovelace",
    age = 36,
    email = "ada@example.com",
    phone = "123",
    username = "ada",
    imageUrl = String.EMPTY,
    role = "user",
    companyName = "Analytical Engines",
    department = "Research",
    jobTitle = "Engineer",
    street = "One Street",
    city = "London",
    state = "London",
    country = "United Kingdom",
)
