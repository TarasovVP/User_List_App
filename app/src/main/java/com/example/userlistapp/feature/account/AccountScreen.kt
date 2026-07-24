package com.example.userlistapp.feature.account

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.example.userlistapp.R
import com.example.userlistapp.domain.model.SessionState

@Composable
fun AccountScreen(
    state: AuthUiState,
    onOpenSignIn: () -> Unit,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
    onAvatar: (String?) -> Unit,
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                // Some picker providers grant durable access without exposing a persistable grant.
            }
            onAvatar(uri.toString())
        }
    }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (state.session) {
            SessionState.Initializing -> CircularProgressIndicator()
            SessionState.SignedOut -> {
                Text(stringResource(R.string.guest_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.guest_explanation), modifier = Modifier.padding(vertical = 16.dp))
                Button(onClick = onOpenSignIn, modifier = Modifier.testTag("sign_in_open")) { Text(stringResource(R.string.sign_in)) }
            }
            is SessionState.SignedIn -> when {
                state.isAccountLoading && state.account == null -> CircularProgressIndicator()
                state.accountError != null && state.account == null -> {
                    Text(state.accountError.resolve(context))
                    Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
                state.account != null -> {
                    val account = state.account
                    var localImageFailed by remember(state.localAvatarUri) { mutableStateOf(false) }
                    AsyncImage(
                        model = state.localAvatarUri?.takeUnless { localImageFailed } ?: account.remoteImageUrl,
                        contentDescription = stringResource(R.string.change_photo),
                        onError = { if (state.localAvatarUri != null) localImageFailed = true },
                        modifier = Modifier.size(128.dp).clip(CircleShape).clickable {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    )
                    Text(account.fullName, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp))
                    Text("@${account.username}")
                    Text(account.email)
                    Button(
                        onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.padding(top = 20.dp),
                    ) { Text(stringResource(R.string.change_photo)) }
                    if (state.localAvatarUri != null) {
                        OutlinedButton(onClick = {
                            releaseUri(context, state.localAvatarUri)
                            onAvatar(null)
                        }) { Text(stringResource(R.string.remove_local_photo)) }
                    }
                    OutlinedButton(onClick = {
                        state.localAvatarUri?.let { releaseUri(context, it) }
                        onSignOut()
                    }, modifier = Modifier.padding(top = 12.dp)) { Text(stringResource(R.string.sign_out)) }
                }
                else -> Unit
            }
        }
    }
}

private fun releaseUri(context: android.content.Context, value: String) {
    try {
        context.contentResolver.releasePersistableUriPermission(
            value.toUri(),
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    } catch (_: SecurityException) {
        // The picker provider may not have issued a persistable grant.
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInSheet(state: AuthUiState, onDismiss: () -> Unit, onSubmit: (String, String) -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = { if (!state.isSigningIn) onDismiss() }) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.sign_in), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.test_credentials_hint))
            OutlinedTextField(
                value = username, onValueChange = { username = it }, enabled = !state.isSigningIn,
                label = { Text(stringResource(R.string.username)) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("login_username"),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it }, enabled = !state.isSigningIn,
                label = { Text(stringResource(R.string.password)) }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().testTag("login_password"),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (!state.isSigningIn) onSubmit(username, password) }),
            )
            state.loginError?.let { Text(it.resolve(LocalContext.current), color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = { onSubmit(username, password) },
                enabled = !state.isSigningIn && username.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth().testTag("login_submit"),
            ) {
                if (state.isSigningIn) CircularProgressIndicator(Modifier.size(20.dp))
                else Text(stringResource(R.string.sign_in))
            }
            OutlinedButton(onClick = onDismiss, enabled = !state.isSigningIn, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}

@Composable
fun AuthenticationRequired(onSignIn: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.authentication_required), style = MaterialTheme.typography.titleLarge)
        Button(onClick = onSignIn, modifier = Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.sign_in)) }
    }
}
