package com.example.userlistapp

import com.example.userlistapp.core.common.UiText
import com.example.userlistapp.feature.users.list.UserListContentState
import com.example.userlistapp.feature.users.list.UserListUiState
import com.example.userlistapp.feature.users.list.toContentState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UserListContentStateTest {
    @Test
    fun `maps rendering states independently from refresh and filter details`() {
        assertEquals(
            UserListContentState.InitialLoading,
            UserListUiState().toContentState(initialErrorMessage = null),
        )
        assertEquals(
            UserListContentState.Empty,
            UserListUiState(isInitialLoading = false, isRefreshing = true)
                .toContentState(initialErrorMessage = null),
        )
        assertEquals(
            UserListContentState.Loaded,
            UserListUiState(
                users = listOf(sampleUser()),
                isInitialLoading = false,
                isRefreshing = true,
            ).toContentState(initialErrorMessage = null),
        )
    }

    @Test
    fun `requires Android error text to be resolved before rendering`() {
        val state = UserListUiState(
            isInitialLoading = false,
            initialError = UiText(R.string.error_network),
        )

        assertEquals(
            UserListContentState.InitialError("Network unavailable"),
            state.toContentState(initialErrorMessage = "Network unavailable"),
        )
        assertThrows(IllegalArgumentException::class.java) {
            state.toContentState(initialErrorMessage = null)
        }
    }
}
