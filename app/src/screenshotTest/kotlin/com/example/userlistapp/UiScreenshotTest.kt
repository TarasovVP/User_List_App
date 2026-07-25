package com.example.userlistapp

import android.content.res.Configuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.example.userlistapp.core.common.UiText
import com.example.userlistapp.domain.model.SessionState
import com.example.userlistapp.domain.model.ThemeMode
import com.example.userlistapp.domain.model.User
import com.example.userlistapp.feature.account.AuthUiState
import com.example.userlistapp.feature.account.SignInContent
import com.example.userlistapp.feature.users.details.UserDetailsScreen
import com.example.userlistapp.feature.users.details.UserDetailsUiState
import com.example.userlistapp.feature.users.list.UserListScreen
import com.example.userlistapp.feature.users.list.UserListUiState
import com.example.userlistapp.ui.theme.UserListTheme

@PreviewTest
@Preview(name = "Users light", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
fun usersLightScreenshot() {
    ScreenshotTheme(ThemeMode.LIGHT) {
        UserListScreen(
            state = UserListUiState(
                users = screenshotUsers,
                hasCachedUsers = true,
                isInitialLoading = false,
            ),
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

@PreviewTest
@Preview(
    name = "Users dark",
    widthDp = 393,
    heightDp = 852,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun usersDarkScreenshot() {
    ScreenshotTheme(ThemeMode.DARK) {
        UserListScreen(
            state = UserListUiState(
                users = screenshotUsers,
                hasCachedUsers = true,
                isInitialLoading = false,
            ),
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

@PreviewTest
@Preview(name = "Sign in error", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
fun signInErrorScreenshot() {
    ScreenshotTheme(ThemeMode.LIGHT) {
        SignInContent(
            state = AuthUiState(
                session = SessionState.SignedOut,
                loginError = UiText(R.string.error_invalid_credentials),
            ),
            onDismiss = {},
            onCredentialsChanged = {},
            onSubmit = { _, _ -> },
        )
    }
}

@PreviewTest
@Preview(name = "Details with note", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
fun detailsWithNoteScreenshot() {
    ScreenshotTheme(ThemeMode.LIGHT) {
        UserDetailsScreen(
            state = UserDetailsUiState(
                user = screenshotUsers.first().copy(note = "Discuss analytical engine diagrams"),
                noteDraft = "Discuss analytical engine diagrams",
                isLoading = false,
            ),
            onBack = {},
            onFavorite = {},
            onNoteChanged = {},
            onSaveNote = {},
            onDeleteNote = {},
            snackbar = SnackbarHostState(),
        )
    }
}

@Composable
private fun ScreenshotTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    UserListTheme(mode = mode, content = content)
}

private val screenshotUsers = listOf(
    screenshotUser(1, "Ada", "Lovelace", favorite = true),
    screenshotUser(2, "Grace", "Hopper"),
)

private fun screenshotUser(
    id: Int,
    firstName: String,
    lastName: String,
    favorite: Boolean = false,
) = User(
    id = id,
    firstName = firstName,
    lastName = lastName,
    age = 36,
    email = "${firstName.lowercase()}@example.com",
    phone = "+1 555 0100",
    username = firstName.lowercase(),
    imageUrl = "",
    role = "admin",
    companyName = "Computing",
    department = "Research",
    jobTitle = "Engineer",
    street = "1 Main Street",
    city = "London",
    state = "England",
    country = "UK",
    isFavorite = favorite,
)
