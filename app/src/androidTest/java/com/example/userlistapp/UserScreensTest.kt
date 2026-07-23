package com.example.userlistapp

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.platform.app.InstrumentationRegistry
import com.example.userlistapp.domain.model.User
import com.example.userlistapp.feature.users.details.UserDetailsScreen
import com.example.userlistapp.feature.users.details.UserDetailsUiState
import com.example.userlistapp.feature.users.list.UserListScreen
import com.example.userlistapp.feature.users.list.UserListUiState
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
