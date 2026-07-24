package com.example.userlistapp.feature.users.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.userlistapp.R
import com.example.userlistapp.domain.model.User
import com.example.userlistapp.feature.users.components.UserAvatar
import java.text.DateFormat
import java.util.Date

@Composable
fun UserDetailsRoute(onBack: () -> Unit, viewModel: UserDetailsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.events.collect { snackbar.showSnackbar(it.resolve(context)) } }
    UserDetailsScreen(state, onBack, viewModel::toggleFavorite, viewModel::setNoteDraft, viewModel::saveNote, viewModel::deleteNote, snackbar)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDetailsScreen(
    state: UserDetailsUiState,
    onBack: () -> Unit,
    onFavorite: () -> Unit,
    onNoteChanged: (String) -> Unit,
    onSaveNote: () -> Unit,
    onDeleteNote: () -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        topBar = { TopAppBar(modifier = Modifier.shadow(4.dp), expandedHeight = 56.dp, title = { Text(stringResource(R.string.user_details)) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
        }) }, snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val user = state.user
        when {
            state.isLoading -> {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            user == null -> {
                Column(
                    Modifier.fillMaxSize().padding(padding).padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.user_not_found), style = MaterialTheme.typography.titleMedium)
                    Button(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
                        Text(stringResource(R.string.back))
                    }
                }
            }
            else -> DetailsContent(user, state, onFavorite, onNoteChanged, onSaveNote, onDeleteNote, Modifier.padding(padding))
        }
    }
}

@Composable
private fun DetailsContent(
    user: User,
    state: UserDetailsUiState,
    onFavorite: () -> Unit,
    onNoteChanged: (String) -> Unit,
    onSaveNote: () -> Unit,
    onDeleteNote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        UserAvatar(user.imageUrl, user.fullName, Modifier.size(160.dp).clip(CircleShape))
        Text(user.fullName, style = MaterialTheme.typography.headlineMedium)
        IconButton(
            onClick = onFavorite,
            enabled = state.canToggleFavorite,
            modifier = Modifier.testTag("favorite_button"),
        ) {
            Icon(if (user.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder, stringResource(if (user.isFavorite) R.string.favorite else R.string.not_favorite), tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Detail(stringResource(R.string.username), user.username)
            Detail(stringResource(R.string.age), user.age.toString())
            Detail(stringResource(R.string.email), user.email)
            Detail(stringResource(R.string.phone), user.phone)
            Detail(stringResource(R.string.role), user.role)
            Detail(stringResource(R.string.company), user.companyName)
            Detail(stringResource(R.string.department), user.department)
            Detail(stringResource(R.string.job_title), user.jobTitle)
            Detail(stringResource(R.string.address), user.fullAddress)
        }
        OutlinedTextField(
            value = state.noteDraft,
            onValueChange = onNoteChanged,
            label = { Text(stringResource(R.string.note)) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth().testTag("note_field"),
        )
        user.noteModifiedAt?.let { modifiedAt ->
            val formattedModifiedAt = remember(modifiedAt) {
                DateFormat.getDateTimeInstance().format(Date(modifiedAt))
            }
            Text(
                text = stringResource(
                    R.string.note_last_updated,
                    formattedModifiedAt,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (state.canDelete) {
                TextButton(onClick = onDeleteNote, modifier = Modifier.testTag("delete_note")) {
                    Icon(Icons.Default.Delete, null)
                    Text(stringResource(R.string.delete_note))
                }
            }
            Button(onClick = onSaveNote, enabled = state.canSave, modifier = Modifier.testTag("save_note")) { Text(stringResource(R.string.save_note)) }
        }
    }
}

@Composable private fun Detail(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifBlank { stringResource(R.string.not_available) }, style = MaterialTheme.typography.bodyMedium)
    }
}
