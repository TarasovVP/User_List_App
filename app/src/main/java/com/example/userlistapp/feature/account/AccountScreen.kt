package com.example.userlistapp.feature.account

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.userlistapp.R
import com.example.userlistapp.domain.model.SessionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    state: AuthUiState,
    onOpenSignIn: () -> Unit,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
    onImportAvatar: (String) -> Unit,
    onRemoveAvatar: () -> Unit,
    onClearAvatarError: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            onClearAvatarError()
            uri?.let { onImportAvatar(it.toString()) }
        }
    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.shadow(4.dp),
                expandedHeight = 56.dp,
                title = { Text(stringResource(R.string.account_title)) },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, stringResource(R.string.settings))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (state.session) {
                SessionState.Initializing -> CircularProgressIndicator()
                SessionState.SignedOut -> {
                    GuestPrompt(onSignIn = onOpenSignIn)
                }

                is SessionState.SignedIn -> when {
                    state.isAccountLoading && state.account == null -> CircularProgressIndicator()
                    state.accountError != null && state.account == null -> {
                        Text(state.accountError.resolve(context))
                        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                    }

                    state.account != null -> {
                        val account = state.account
                        var localImageFailed by remember(state.localAvatarUri) {
                            mutableStateOf(
                                false
                            )
                        }
                        Box(Modifier.size(128.dp)) {
                            AsyncImage(
                                model = state.localAvatarUri?.takeUnless { localImageFailed }
                                    ?: account.remoteImageUrl,
                                contentDescription = stringResource(
                                    if (state.localAvatarUri == null) {
                                        R.string.choose_local_photo
                                    } else {
                                        R.string.change_local_photo
                                    },
                                ),
                                onError = {
                                    if (state.localAvatarUri != null) localImageFailed = true
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .clickable {
                                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    },
                            )
                            if (state.localAvatarUri != null) {
                                FilledTonalIconButton(
                                    onClick = onRemoveAvatar,
                                    modifier = Modifier.align(Alignment.BottomEnd),
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        stringResource(R.string.remove_local_photo),
                                    )
                                }
                            }
                        }
                        Text(
                            account.fullName,
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                        Text("@${account.username}")
                        Text(account.email)
                        Button(
                            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            modifier = Modifier.padding(top = 20.dp),
                        ) {
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
                        state.avatarError?.let { avatarError ->
                            Text(
                                avatarError.resolve(context),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        OutlinedButton(
                            onClick = onSignOut,
                            modifier = Modifier.padding(top = 12.dp)
                        ) { Text(stringResource(R.string.sign_out)) }
                    }

                    else -> Unit
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInSheet(
    state: AuthUiState,
    onDismiss: () -> Unit,
    onCredentialsChanged: () -> Unit,
    onSubmit: (String, String) -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = { if (!state.isSigningIn) onDismiss() }) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.sign_in), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.test_credentials_hint))
            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it
                    onCredentialsChanged()
                },
                enabled = !state.isSigningIn,
                label = { Text(stringResource(R.string.username)) }, singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_username"),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    onCredentialsChanged()
                },
                enabled = !state.isSigningIn,
                label = { Text(stringResource(R.string.password)) }, singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = stringResource(
                                if (passwordVisible) R.string.hide_password else R.string.show_password,
                            ),
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_password"),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (!state.isSigningIn) onSubmit(
                        username,
                        password
                    )
                }),
            )
            state.loginError?.let {
                Text(
                    it.resolve(LocalContext.current),
                    color = MaterialTheme.colorScheme.error
                )
            }
            Button(
                onClick = { onSubmit(username, password) },
                enabled = !state.isSigningIn && username.isNotBlank() && password.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_submit"),
            ) {
                if (state.isSigningIn) CircularProgressIndicator(Modifier.size(20.dp))
                else Text(stringResource(R.string.sign_in))
            }
            OutlinedButton(
                onClick = onDismiss,
                enabled = !state.isSigningIn,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticationRequired(
    onSignIn: () -> Unit,
    onSettings: (() -> Unit)? = null,
) {
    if (onSettings != null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    modifier = Modifier.shadow(4.dp),
                    expandedHeight = 56.dp,
                    title = { Text(stringResource(R.string.users_title)) },
                    actions = {
                        IconButton(onClick = onSettings) {
                            Icon(Icons.Default.Settings, stringResource(R.string.settings))
                        }
                    },
                )
            },
        ) { padding ->
            AuthenticationRequiredContent(onSignIn, Modifier.padding(padding))
        }
    } else {
        AuthenticationRequiredContent(onSignIn)
    }
}

@Composable
private fun AuthenticationRequiredContent(
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        GuestPrompt(onSignIn = onSignIn)
    }
}

@Composable
private fun GuestPrompt(onSignIn: () -> Unit) {
    Text(stringResource(R.string.guest_title), style = MaterialTheme.typography.headlineMedium)
    Text(
        stringResource(R.string.guest_explanation),
        modifier = Modifier.padding(vertical = 16.dp),
    )
    Button(onClick = onSignIn, modifier = Modifier.testTag("sign_in_open")) {
        Text(stringResource(R.string.sign_in))
    }
}
