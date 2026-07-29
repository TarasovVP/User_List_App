package com.example.userlistapp.feature.account.modular

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModularAccountScreen(
    state: AccountFeatureState,
    actions: AccountFeatureActions,
) {
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            actions.onClearAvatarError()
            uri?.let { actions.onImportAvatar(it.toString()) }
        }
    val openPicker = {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.shadow(4.dp),
                expandedHeight = 56.dp,
                title = { Text(stringResource(R.string.account_title)) },
                actions = {
                    IconButton(onClick = actions.onSettings) {
                        Icon(Icons.Default.Settings, stringResource(R.string.settings))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (state.session) {
                AccountSession.Initializing -> CircularProgressIndicator()
                AccountSession.SignedOut -> GuestPrompt(actions.onOpenSignIn)
                AccountSession.SignedIn -> when {
                    state.isAccountLoading && state.account == null -> CircularProgressIndicator()
                    state.accountError != null && state.account == null -> {
                        Text(state.accountError)
                        Button(onClick = actions.onRetry) {
                            Text(stringResource(R.string.retry))
                        }
                    }

                    state.account != null -> AccountContent(
                        state = state,
                        actions = actions,
                        openPicker = openPicker,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountContent(
    state: AccountFeatureState,
    actions: AccountFeatureActions,
    openPicker: () -> Unit,
) {
    val account = requireNotNull(state.account)
    var localImageFailed by remember(state.localAvatarUri) { mutableStateOf(false) }
    val avatarClickLabel = stringResource(
        if (state.localAvatarUri == null) {
            R.string.choose_local_photo
        } else {
            R.string.change_local_photo
        },
    )

    Box(Modifier.size(128.dp)) {
        AsyncImage(
            model = state.localAvatarUri?.takeUnless { localImageFailed } ?: account.remoteImageUrl,
            contentDescription = avatarClickLabel,
            onError = { if (state.localAvatarUri != null) localImageFailed = true },
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .clickable(
                    role = Role.Button,
                    onClickLabel = avatarClickLabel,
                    onClick = openPicker,
                ),
        )
        if (state.localAvatarUri != null) {
            FilledTonalIconButton(
                onClick = actions.onRemoveAvatar,
                modifier = Modifier.align(Alignment.BottomEnd),
            ) {
                Icon(Icons.Default.Delete, stringResource(R.string.remove_local_photo))
            }
        }
    }
    Text(
        account.fullName,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(top = 16.dp),
    )
    Text("@${account.username}")
    Text(account.email)
    Button(onClick = openPicker, modifier = Modifier.padding(top = 20.dp)) {
        Text(
            stringResource(
                if (state.localAvatarUri == null) {
                    R.string.choose_local_photo
                } else {
                    R.string.change_local_photo
                },
            ),
        )
    }
    Text(
        stringResource(R.string.local_photo_explanation),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
    state.avatarError?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
    OutlinedButton(onClick = actions.onSignOut, modifier = Modifier.padding(top = 12.dp)) {
        Text(stringResource(R.string.sign_out))
    }
}

@Composable
private fun GuestPrompt(onSignIn: () -> Unit) {
    Text(stringResource(R.string.guest_title), style = MaterialTheme.typography.headlineSmall)
    Text(
        stringResource(R.string.guest_explanation),
        modifier = Modifier.padding(vertical = 16.dp),
    )
    Button(
        onClick = onSignIn,
        modifier = Modifier.testTag(AccountFeatureTestTags.SIGN_IN_OPEN),
    ) {
        Text(stringResource(R.string.sign_in))
    }
}
