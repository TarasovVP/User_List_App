package com.example.userlistapp.feature.account.modular

/**
 * The complete public surface used by the application composition root.
 *
 * Types from the base application deliberately do not cross this boundary.
 */
data class AccountFeatureState(
    val session: AccountSession = AccountSession.Initializing,
    val account: AccountContent? = null,
    val localAvatarUri: String? = null,
    val isAccountLoading: Boolean = false,
    val accountError: String? = null,
    val avatarError: String? = null,
)

sealed interface AccountSession {
    data object Initializing : AccountSession
    data object SignedOut : AccountSession
    data object SignedIn : AccountSession
}

data class AccountContent(
    val username: String,
    val fullName: String,
    val email: String,
    val remoteImageUrl: String,
)

data class AccountFeatureActions(
    val onOpenSignIn: () -> Unit,
    val onRetry: () -> Unit,
    val onSignOut: () -> Unit,
    val onImportAvatar: (String) -> Unit,
    val onRemoveAvatar: () -> Unit,
    val onClearAvatarError: () -> Unit,
    val onSettings: () -> Unit,
)

object AccountFeatureTestTags {
    const val SIGN_IN_OPEN = "sign_in_open"
}
