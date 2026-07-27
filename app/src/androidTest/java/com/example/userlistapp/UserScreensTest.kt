package com.example.userlistapp

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertAny
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.example.userlistapp.core.common.EMPTY
import com.example.userlistapp.core.common.UiText
import com.example.userlistapp.core.ui.UiTestTags
import com.example.userlistapp.core.ui.UiTextSnackbarEffect
import com.example.userlistapp.data.realtime.RealtimeConnectionState
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
import com.example.userlistapp.feature.users.list.toContentState
import com.example.userlistapp.ui.theme.UserListTheme
import com.example.userlistapp.ui.theme.extendedColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class UserScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun listSearchesUpdatesFavoriteAndExposesNavigationActions() {
        var query by mutableStateOf(String.EMPTY)
        var opened: Int? = null
        var settings = false
        var users by mutableStateOf(listOf(user(1, "Ada"), user(2, "Grace")))
        compose.setContent {
            UserListTheme(ThemeMode.LIGHT) {
                val listState = UserListUiState(
                    users = users.filter { it.fullName.contains(query, true) },
                    hasCachedUsers = true,
                    isInitialLoading = false,
                    query = query,
                )
                UserListScreen(
                    listState,
                    listState.toContentState(initialErrorMessage = null),
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
        compose.onNodeWithTag(UiTestTags.favorite(1)).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                context.getString(R.string.not_favorite),
            ),
        )
        compose.onNodeWithTag(UiTestTags.favorite(1)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.remove_from_favorites))
            .assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.search_users))
            .performClick()
        val search = compose.onNodeWithTag(UiTestTags.SEARCH)
        search.performTextInput("Grace")
        search.performImeAction()
        search.assertIsNotFocused()
        compose.onNodeWithText("Grace User").performClick()
        compose.runOnIdle { assertEquals(2, opened) }
        compose.onNodeWithContentDescription(context.getString(R.string.settings)).performClick()
        compose.runOnIdle { assertTrue(settings) }
    }

    @Test
    fun listSortingExposesRecentlyViewedOption() {
        var selectedSort by mutableStateOf<com.example.userlistapp.domain.model.UserSort>(com.example.userlistapp.domain.model.UserSort.NAME_ASCENDING)
        compose.setContent {
            UserListTheme(ThemeMode.LIGHT) {
                val listState = UserListUiState(
                    users = listOf(user(1, "Ada")),
                    hasCachedUsers = true,
                    isInitialLoading = false,
                    sort = selectedSort,
                )
                UserListScreen(
                    state = listState,
                    contentState = listState.toContentState(initialErrorMessage = null),
                    onQuery = {},
                    onSort = { selectedSort = it },
                    onFavoritesOnly = {},
                    onRefresh = {},
                    onUser = {},
                    onFavorite = {},
                    onSettings = {},
                    snackbar = SnackbarHostState(),
                )
            }
        }

        val azLabel = context.getString(R.string.sort_az)
        val recentlyViewedLabel = context.getString(R.string.sort_recently_viewed)

        // Opens the sort dropdown by clicking the current “Name A–Z” label.
        compose.onNodeWithText(azLabel).performClick()

        // Verifies that “Recently viewed” is displayed as a menu option.
        compose.onNodeWithText(recentlyViewedLabel).assertIsDisplayed()

        // Clicks “Recently viewed”.
        compose.onNodeWithText(recentlyViewedLabel).performClick()

        // Verifies that onSort received UserSort.RECENTLY_VIEWED
        // and verifies the selected chip now displays “Recently viewed”.
        assertEquals(com.example.userlistapp.domain.model.UserSort.RECENTLY_VIEWED, selectedSort)
        compose.onNodeWithText(recentlyViewedLabel).assertIsDisplayed()
    }

    @Test
    fun listTransitionsThroughLoadingErrorEmptyAndLoadedStates() {
        var state by mutableStateOf(UserListUiState())
        var resolvedError by mutableStateOf<String?>(null)
        compose.setContent {
            UserListTheme(ThemeMode.LIGHT) {
                UserListScreen(
                    state = state,
                    contentState = state.toContentState(resolvedError),
                    onQuery = {},
                    onSort = {},
                    onFavoritesOnly = {},
                    onRefresh = {},
                    onUser = {},
                    onFavorite = {},
                    onSettings = {},
                )
            }
        }

        compose.onNodeWithTag(UiTestTags.USER_LIST_LOADING).assertIsDisplayed()

        compose.runOnIdle {
            resolvedError = "Network unavailable"
            state = UserListUiState(
                isInitialLoading = false,
                initialError = UiText(R.string.error_network),
            )
        }
        compose.waitForIdle()
        compose.onNodeWithTag(UiTestTags.USER_LIST_ERROR).assertIsDisplayed()

        compose.runOnIdle {
            resolvedError = null
            state = UserListUiState(isInitialLoading = false)
        }
        compose.waitForIdle()
        compose.onNodeWithTag(UiTestTags.USER_LIST_EMPTY).assertIsDisplayed()

        compose.runOnIdle {
            state = UserListUiState(
                users = listOf(user(1, "Ada")),
                hasCachedUsers = true,
                isInitialLoading = false,
            )
        }
        compose.waitForIdle()
        compose.onNodeWithTag(UiTestTags.USER_LIST).assertIsDisplayed()
    }

    @Test
    fun listDisplaysWebSocketStateAndSendsTestMessage() {
        var sent = false
        compose.setContent {
            UserListTheme(ThemeMode.LIGHT) {
                val state = UserListUiState(
                    users = listOf(user(1, "Ada")),
                    hasCachedUsers = true,
                    isInitialLoading = false,
                    realtimeConnection = RealtimeConnectionState.Connected,
                    realtimeMessages = listOf("""{"type":"echo","received":true}"""),
                )
                UserListScreen(
                    state = state,
                    contentState = state.toContentState(initialErrorMessage = null),
                    onQuery = {},
                    onSort = {},
                    onFavoritesOnly = {},
                    onRefresh = {},
                    onUser = {},
                    onFavorite = {},
                    onSettings = {},
                    onSendRealtime = { sent = true },
                )
            }
        }

        compose.onNodeWithText(
            context.getString(
                R.string.websocket_status,
                context.getString(R.string.websocket_connected),
            ),
        )
            .assertIsDisplayed()
        compose.onNodeWithText("""{"type":"echo","received":true}""")
            .assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.websocket_send_test))
            .assertIsEnabled()
            .performClick()
        compose.runOnIdle { assertTrue(sent) }
    }

    @Test
    fun extendedThemeProvidesDifferentFavoriteColorsForLightAndDarkModes() {
        var lightFavorite = androidx.compose.ui.graphics.Color.Unspecified
        var darkFavorite = androidx.compose.ui.graphics.Color.Unspecified
        compose.setContent {
            UserListTheme(ThemeMode.LIGHT) {
                val color = MaterialTheme.extendedColors.favoriteSelected
                SideEffect { lightFavorite = color }
            }
            UserListTheme(ThemeMode.DARK) {
                val color = MaterialTheme.extendedColors.favoriteSelected
                SideEffect { darkFavorite = color }
            }
        }

        compose.runOnIdle {
            assertNotEquals(androidx.compose.ui.graphics.Color.Unspecified, lightFavorite)
            assertNotEquals(androidx.compose.ui.graphics.Color.Unspecified, darkFavorite)
            assertNotEquals(lightFavorite, darkFavorite)
        }
    }

    @Test
    fun snackbarCollectionRestartsWhenEventSourceChanges() {
        val first = MutableSharedFlow<UiText>(extraBufferCapacity = 1)
        val second = MutableSharedFlow<UiText>(extraBufferCapacity = 1)
        var events by mutableStateOf<Flow<UiText>>(first)
        val snackbar = SnackbarHostState()
        compose.setContent {
            UiTextSnackbarEffect(events, snackbar)
        }

        compose.waitForIdle()
        first.tryEmit(UiText(R.string.error_network))
        compose.waitUntil {
            snackbar.currentSnackbarData?.visuals?.message ==
                context.getString(R.string.error_network)
        }

        compose.runOnIdle {
            snackbar.currentSnackbarData?.dismiss()
            events = second
        }
        compose.waitForIdle()
        first.tryEmit(UiText(R.string.error_storage))
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(null, snackbar.currentSnackbarData)
        }

        second.tryEmit(UiText(R.string.error_storage))
        compose.waitUntil {
            snackbar.currentSnackbarData?.visuals?.message ==
                context.getString(R.string.error_storage)
        }
    }

    @Test
    fun detailsUpdatesFavoriteAndPersistsRenderedNoteState() {
        var favorite by mutableStateOf(false)
        var draft by mutableStateOf(String.EMPTY)
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

        compose.onNodeWithTag(UiTestTags.FAVORITE_BUTTON).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.remove_from_favorites))
            .assertIsDisplayed()
        compose.onNodeWithTag(UiTestTags.NOTE_FIELD).performTextInput("Remember this")
        compose.onNodeWithTag(UiTestTags.SAVE_NOTE).assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag(UiTestTags.SAVE_NOTE).assertIsNotEnabled()
        compose.onNodeWithTag(UiTestTags.NOTE_FIELD).assertIsDisplayed()
        compose.onNodeWithText("Ada User").assert(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading),
        )
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

        compose.onNodeWithTag(UiTestTags.DELETE_NOTE).performScrollTo().performClick()

        compose.onAllNodesWithTag(UiTestTags.DELETE_NOTE).assertCountEquals(0)
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
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onNodeWithContentDescription(context.getString(R.string.settings)).performClick()
        compose.runOnIdle { assertTrue(settingsOpened) }
        compose.onNodeWithText(context.getString(R.string.guest_title)).assertIsDisplayed()
        compose.onNodeWithTag(UiTestTags.SIGN_IN_OPEN).performClick()
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
        compose.onAllNodesWithText(context.getString(R.string.sign_in))
            .assertAny(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onNodeWithText(error).assertIsDisplayed()
        compose.onNodeWithTag(UiTestTags.LOGIN_SUBMIT).assertIsNotEnabled()
        compose.onNodeWithTag(UiTestTags.LOGIN_USERNAME).performTextInput("emilys")
        compose.onAllNodesWithText(error).assertCountEquals(0)
        val password = compose.onNodeWithTag(UiTestTags.LOGIN_PASSWORD)
        password.performTextInput("emilyspass")
        compose.onNodeWithContentDescription(context.getString(R.string.show_password))
            .performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.hide_password))
            .assertIsDisplayed()
        compose.onNodeWithTag(UiTestTags.LOGIN_SUBMIT).assertIsEnabled()
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

        compose.onNodeWithTag(UiTestTags.LOGIN_PASSWORD).performClick().performImeAction()

        compose.runOnIdle { assertEquals(false, submitted) }
        compose.onNodeWithTag(UiTestTags.LOGIN_SUBMIT).assertIsNotEnabled()
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
                        account = Account(
                            1,
                            "emilys",
                            "Emily",
                            "Johnson",
                            "emily@example.com",
                            String.EMPTY
                        ),
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
        compose.onNodeWithContentDescription(context.getString(R.string.remove_local_photo))
            .performClick()
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
    imageUrl = String.EMPTY,
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
