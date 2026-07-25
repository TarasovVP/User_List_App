package com.example.userlistapp

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.example.userlistapp.core.common.UiText
import com.example.userlistapp.domain.model.Account
import com.example.userlistapp.domain.model.SessionState
import com.example.userlistapp.domain.model.ThemeMode
import com.example.userlistapp.domain.model.User
import com.example.userlistapp.feature.account.AccountScreen
import com.example.userlistapp.feature.account.AuthUiState
import com.example.userlistapp.feature.account.SignInSheet
import com.example.userlistapp.feature.users.details.UserDetailsScreen
import com.example.userlistapp.feature.users.details.UserDetailsUiState
import com.example.userlistapp.feature.users.list.UserListScreen
import com.example.userlistapp.feature.users.list.UserListUiState
import com.example.userlistapp.ui.theme.UserListTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class UserScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun listSearchesUpdatesFavoriteAndExposesNavigationActions() {
        var query by mutableStateOf("")
        var opened: Int? = null
        var settings = false
        var users by mutableStateOf(listOf(user(1, "Ada"), user(2, "Grace")))
        compose.setContent {
            UserListTheme(ThemeMode.LIGHT) {
                UserListScreen(
                    UserListUiState(
                        users = users.filter { it.fullName.contains(query, true) },
                        hasCachedUsers = true,
                        isInitialLoading = false,
                        query = query,
                    ),
                    onQuery = { query = it },
                    onSort = {},
                    onFavoritesOnly = {},
                    onRefresh = {},
                    onUser = { opened = it },
                    onFavorite = { selected ->
                        users = users.map {
                            if (it.id == selected.id) it.copy(isFavorite = !it.isFavorite) else it
                        }
                    },
                    onSettings = { settings = true },
                    snackbar = SnackbarHostState(),
                )
            }
        }

        compose.onNodeWithText("Ada User").assertIsDisplayed()
        compose.onNodeWithTag("favorite_1").performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.favorite)).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.search_users)).performClick()
        val search = compose.onNodeWithTag("search")
        search.performTextInput("Grace")
        search.performImeAction()
        search.assertIsNotFocused()
        compose.onNodeWithText("Grace User").performClick()
        compose.runOnIdle { assertEquals(2, opened) }
        compose.onNodeWithContentDescription(context.getString(R.string.settings)).performClick()
        compose.runOnIdle { assertTrue(settings) }
    }

    @Test
    fun detailsUpdatesFavoriteAndPersistsRenderedNoteState() {
        var favorite by mutableStateOf(false)
        var draft by mutableStateOf("")
        var savedNote by mutableStateOf<String?>(null)
        compose.setContent {
            UserListTheme(ThemeMode.LIGHT) {
                UserDetailsScreen(
                    state = UserDetailsUiState(
                        user = user(1, "Ada", favorite).copy(note = savedNote),
                        noteDraft = draft,
                        isLoading = false,
                    ),
                    onBack = {},
                    onFavorite = { favorite = !favorite },
                    onNoteChanged = { draft = it },
                    onSaveNote = { savedNote = draft },
                    onDeleteNote = {},
                    snackbar = SnackbarHostState(),
                )
            }
        }

        compose.onNodeWithTag("favorite_button").performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.favorite)).assertIsDisplayed()
        compose.onNodeWithTag("note_field").performTextInput("Remember this")
        compose.onNodeWithTag("save_note").assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("save_note").assertIsNotEnabled()
        compose.onNodeWithTag("note_field").assertIsDisplayed()
    }

    @Test
    fun detailsDeletesExistingNoteAndUpdatesUi() {
        var note by mutableStateOf<String?>("Stored")
        compose.setContent {
            UserListTheme(ThemeMode.LIGHT) {
                UserDetailsScreen(
                    state = UserDetailsUiState(
                        user = user(1, "Ada").copy(note = note),
                        noteDraft = note.orEmpty(),
                        isLoading = false,
                    ),
                    onBack = {},
                    onFavorite = {},
                    onNoteChanged = {},
                    onSaveNote = {},
                    onDeleteNote = { note = null },
                    snackbar = SnackbarHostState(),
                )
            }
        }

        compose.onNodeWithTag("delete_note").performScrollTo().performClick()

        compose.onAllNodesWithTag("delete_note").assertCountEquals(0)
    }

    @Test
    fun missingUserShowsNotFoundStateAndBackAction() {
        var wentBack = false
        compose.setContent {
            UserListTheme(ThemeMode.LIGHT) {
                UserDetailsScreen(
                    state = UserDetailsUiState(isLoading = false),
                    onBack = { wentBack = true },
                    onFavorite = {},
                    onNoteChanged = {},
                    onSaveNote = {},
                    onDeleteNote = {},
                    snackbar = SnackbarHostState(),
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.user_not_found)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.back)).performClick()
        compose.runOnIdle { assertTrue(wentBack) }
    }

    @Test
    fun guestAccountExposesSignInAndSettingsActions() {
        var opened = false
        var settingsOpened = false
        compose.setContent {
            UserListTheme(ThemeMode.LIGHT) {
                AccountScreen(
                    state = AuthUiState(session = SessionState.SignedOut),
                    onOpenSignIn = { opened = true },
                    onRetry = {},
                    onSignOut = {},
                    onImportAvatar = {},
                    onRemoveAvatar = {},
                    onClearAvatarError = {},
                    onSettings = { settingsOpened = true },
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.account_title)).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.settings)).performClick()
        compose.runOnIdle { assertTrue(settingsOpened) }
        compose.onNodeWithText(context.getString(R.string.guest_title)).assertIsDisplayed()
        compose.onNodeWithTag("sign_in_open").performClick()
        compose.runOnIdle { assertTrue(opened) }
    }

    @Test
    fun signInSheetClearsErrorAndSubmitsCredentialsFromIme() {
        var submitted: Pair<String, String>? = null
        var sheetState by mutableStateOf(
            AuthUiState(
                session = SessionState.SignedOut,
                loginError = UiText(R.string.error_invalid_credentials),
            ),
        )
        compose.setContent {
            UserListTheme(ThemeMode.LIGHT) {
                SignInSheet(
                    state = sheetState,
                    onDismiss = {},
                    onCredentialsChanged = { sheetState = sheetState.copy(loginError = null) },
                    onSubmit = { username, password -> submitted = username to password },
                )
            }
        }

        val error = context.getString(R.string.error_invalid_credentials)
        compose.onNodeWithText(error).assertIsDisplayed()
        compose.onNodeWithTag("login_submit").assertIsNotEnabled()
        compose.onNodeWithTag("login_username").performTextInput("emilys")
        compose.onAllNodesWithText(error).assertCountEquals(0)
        val password = compose.onNodeWithTag("login_password")
        password.performTextInput("emilyspass")
        compose.onNodeWithContentDescription(context.getString(R.string.show_password)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.hide_password)).assertIsDisplayed()
        compose.onNodeWithTag("login_submit").assertIsEnabled()
        password.performImeAction()
        compose.runOnIdle { assertEquals("emilys" to "emilyspass", submitted) }
    }

    @Test
    fun signInSheetImeDoesNotSubmitBlankCredentials() {
        var submitted = false
        compose.setContent {
            UserListTheme(ThemeMode.LIGHT) {
                SignInSheet(
                    state = AuthUiState(session = SessionState.SignedOut),
                    onDismiss = {},
                    onCredentialsChanged = {},
                    onSubmit = { _, _ -> submitted = true },
                )
            }
        }

        compose.onNodeWithTag("login_password").performClick().performImeAction()

        compose.runOnIdle { assertEquals(false, submitted) }
        compose.onNodeWithTag("login_submit").assertIsNotEnabled()
    }

    @Test
    fun accountContentExposesPhotoRemovalAndSignOutActions() {
        var removed = false
        var signedOut = false
        compose.setContent {
            UserListTheme(ThemeMode.LIGHT) {
                AccountScreen(
                    state = AuthUiState(
                        session = SessionState.SignedIn(1),
                        account = Account(1, "emilys", "Emily", "Johnson", "emily@example.com", ""),
                        localAvatarUri = "content://local/avatar",
                    ),
                    onOpenSignIn = {},
                    onRetry = {},
                    onSignOut = { signedOut = true },
                    onImportAvatar = {},
                    onRemoveAvatar = { removed = true },
                    onClearAvatarError = {},
                    onSettings = {},
                )
            }
        }

        compose.onNodeWithText("Emily Johnson").assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.remove_local_photo)).performClick()
        compose.onNodeWithText(context.getString(R.string.sign_out)).performClick()
        compose.runOnIdle {
            assertTrue(removed)
            assertTrue(signedOut)
        }
    }
}

private fun user(id: Int, name: String, favorite: Boolean = false) = User(
    id = id,
    firstName = name,
    lastName = "User",
    age = 30,
    email = "$name@example.com",
    phone = "123",
    username = name.lowercase(),
    imageUrl = "",
    role = "user",
    companyName = "Company",
    department = "Dept",
    jobTitle = "Title",
    street = "Street",
    city = "City",
    state = "State",
    country = "Country",
    isFavorite = favorite,
)
