package com.example.userlistapp.core.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.example.userlistapp.core.common.UiText
import kotlinx.coroutines.flow.Flow

@Composable
fun UiTextSnackbarEffect(
    events: Flow<UiText>,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    LaunchedEffect(events, snackbarHostState, context) {
        events.collect { event ->
            snackbarHostState.showSnackbar(event.resolve(context))
        }
    }
}
