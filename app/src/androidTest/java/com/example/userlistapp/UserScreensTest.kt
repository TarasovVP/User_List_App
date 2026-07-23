package com.example.userlistapp

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import com.example.userlistapp.domain.model.User
import com.example.userlistapp.feature.users.details.UserDetailsScreen
import com.example.userlistapp.feature.users.details.UserDetailsUiState
import com.example.userlistapp.feature.users.list.UserListScreen
import com.example.userlistapp.feature.users.list.UserListUiState
import com.example.userlistapp.navigation.SettingsDestination
import com.example.userlistapp.navigation.UserDetailsDestination
import com.example.userlistapp.navigation.UsersDestination
import com.example.userlistapp.ui.theme.UserListTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class UserScreensTest {
    @get:Rule val compose = createComposeRule()

    @Test fun listRendersSearchesOpensDetailsAndSettings() {
        var query by mutableStateOf("")
        var opened: Int? = null
        var settings = false
        val users = listOf(user(1, "Ada"), user(2, "Grace"))
        compose.setContent { UserListTheme(com.example.userlistapp.domain.model.ThemeMode.LIGHT) {
            UserListScreen(UserListUiState(users.filter { it.fullName.contains(query, true) }, true, isInitialLoading = false, query = query), { query = it }, {}, {}, {}, { opened = it }, { settings = true }, SnackbarHostState())
        } }
        compose.onNodeWithText("Ada User").assertIsDisplayed()
        val search = compose.onNodeWithTag("search")
        search.performTextInput("Grace")
        search.performImeAction()
        search.assertIsNotFocused()
        compose.onNodeWithText("Grace User").performClick()
        assertEquals(2, opened)
        compose.onNodeWithContentDescription("Settings").performClick()
        assertTrue(settings)
    }

    @Test fun detailsChangesFavoriteAndEditsNote() {
        val favorite = AtomicBoolean(false)
        var draft by mutableStateOf("")
        val saved = AtomicBoolean(false)
        compose.setContent { UserListTheme(com.example.userlistapp.domain.model.ThemeMode.LIGHT) {
            UserDetailsScreen(UserDetailsUiState(user(1, "Ada", favorite.get()), draft, isLoading = false), {}, { favorite.set(true) }, { draft = it }, { saved.set(true) }, {}, SnackbarHostState())
        } }
        compose.onNodeWithTag("favorite_button").performClick()
        compose.onNodeWithTag("note_field").performTextInput("Remember this")
        compose.onNodeWithTag("save_note").assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(5_000) { favorite.get() && saved.get() }
    }

    @Test fun detailsDeletesExistingNote() {
        var deleted = false
        compose.setContent { UserListTheme(com.example.userlistapp.domain.model.ThemeMode.LIGHT) {
            UserDetailsScreen(
                state = UserDetailsUiState(user = user(1, "Ada").copy(note = "Stored"), noteDraft = "Stored", isLoading = false),
                onBack = {},
                onFavorite = {},
                onNoteChanged = {},
                onSaveNote = {},
                onDeleteNote = { deleted = true },
                snackbar = SnackbarHostState(),
            )
        } }

        compose.onNodeWithTag("delete_note").performScrollTo().performClick()

        compose.waitUntil(5_000) { deleted }
    }

    @Test fun typedNavigationOpensDetailsSettingsAndNavigatesBack() {
        compose.setContent {
            val nav = rememberNavController()
            NavHost(navController = nav, startDestination = UsersDestination) {
                composable<UsersDestination> {
                    Column {
                        Button(onClick = { nav.navigate(UserDetailsDestination(7)) }) {
                            Text("Open typed details")
                        }
                        Button(onClick = { nav.navigate(SettingsDestination) }) {
                            Text("Open typed settings")
                        }
                    }
                }
                composable<UserDetailsDestination> {
                    Button(onClick = nav::navigateUp) { Text("Back from typed details") }
                }
                composable<SettingsDestination> {
                    Button(onClick = nav::navigateUp) { Text("Back from typed settings") }
                }
            }
        }

        compose.onNodeWithText("Open typed details").performClick()
        compose.onNodeWithText("Back from typed details").assertIsDisplayed().performClick()
        compose.onNodeWithText("Open typed settings").performClick()
        compose.onNodeWithText("Back from typed settings").assertIsDisplayed().performClick()
        compose.onNodeWithText("Open typed details").assertIsDisplayed()
    }

    @Test fun missingUserShowsNotFoundStateAndBackAction() {
        var wentBack = false
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent { UserListTheme(com.example.userlistapp.domain.model.ThemeMode.LIGHT) {
            UserDetailsScreen(UserDetailsUiState(isLoading = false), { wentBack = true }, {}, {}, {}, {}, SnackbarHostState())
        } }

        compose.onNodeWithText(context.getString(R.string.user_not_found)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.back)).performClick()
        assertTrue(wentBack)
    }
}

private fun user(id: Int, name: String, favorite: Boolean = false) = User(id, name, "User", 30, "$name@example.com", "123", name.lowercase(), "", "user", "Company", "Dept", "Title", "Street", "City", "State", "Country", favorite)
