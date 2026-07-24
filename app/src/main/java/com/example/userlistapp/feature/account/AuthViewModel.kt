package com.example.userlistapp.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.userlistapp.core.common.AppResult
import com.example.userlistapp.core.common.UiText
import com.example.userlistapp.core.common.toUiText
import com.example.userlistapp.domain.model.Account
import com.example.userlistapp.domain.model.SessionState
import com.example.userlistapp.domain.usecase.AuthUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val session: SessionState = SessionState.Initializing,
    val account: Account? = null,
    val localAvatarUri: String? = null,
    val isSigningIn: Boolean = false,
    val isAccountLoading: Boolean = false,
    val loginError: UiText? = null,
    val accountError: UiText? = null,
    val avatarError: UiText? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val useCases: AuthUseCases,
) : ViewModel() {
    private val operation = MutableStateFlow(OperationState())
    private var signInJob: Job? = null
    private val sessionState = useCases.observeSession()
        .stateIn(viewModelScope, SharingStarted.Eagerly, SessionState.Initializing)

    val uiState: StateFlow<AuthUiState> = combine(
        sessionState,
        useCases.observeLocalAvatar(),
        operation,
    ) { session, avatar, op ->
        AuthUiState(
            session,
            op.account,
            avatar,
            op.signingIn,
            op.loadingAccount,
            op.loginError,
            op.accountError,
            op.avatarError,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthUiState())

    init {
        viewModelScope.launch {
            sessionState.collect { session ->
                if (session is SessionState.SignedIn && operation.value.account?.id != session.userId) {
                    loadAccount(session.userId)
                }
            }
        }
    }

    fun signIn(username: String, password: String) {
        if (signInJob?.isActive == true) return
        signInJob = viewModelScope.launch {
            try {
                operation.update { it.copy(signingIn = true, loginError = null) }
                when (val result = useCases.signIn(username, password)) {
                    is AppResult.Success -> operation.update {
                        it.copy(account = result.value, signingIn = false)
                    }

                    is AppResult.Failure -> operation.update {
                        it.copy(signingIn = false, loginError = result.error.toUiText())
                    }
                }
            } finally {
                operation.update { it.copy(signingIn = false) }
            }
        }
    }

    fun retryAccount() {
        val signedIn = uiState.value.session as? SessionState.SignedIn ?: return
        viewModelScope.launch { loadAccount(signedIn.userId) }
    }

    fun signOut() {
        val inFlightSignIn = signInJob
        signInJob = null
        viewModelScope.launch {
            inFlightSignIn?.cancelAndJoin()
            useCases.signOut()
            operation.update { OperationState() }
        }
    }

    fun importLocalAvatar(sourceUri: String) {
        viewModelScope.launch {
            when (val result = useCases.importLocalAvatar(sourceUri)) {
                is AppResult.Success -> operation.update { it.copy(avatarError = null) }
                is AppResult.Failure -> operation.update {
                    it.copy(avatarError = result.error.toUiText())
                }
            }
        }
    }

    fun removeLocalAvatar() {
        viewModelScope.launch {
            when (val result = useCases.removeLocalAvatar()) {
                is AppResult.Success -> operation.update { it.copy(avatarError = null) }
                is AppResult.Failure -> operation.update {
                    it.copy(avatarError = result.error.toUiText())
                }
            }
        }
    }

    fun clearLoginError() {
        operation.update { it.copy(loginError = null) }
    }

    fun clearAvatarError() {
        operation.update { it.copy(avatarError = null) }
    }

    private suspend fun loadAccount(userId: Int) {
        operation.update { it.copy(loadingAccount = true, accountError = null) }
        when (val result = useCases.loadAccount(userId)) {
            is AppResult.Success -> operation.update {
                it.copy(account = result.value, loadingAccount = false)
            }

            is AppResult.Failure -> operation.update {
                it.copy(loadingAccount = false, accountError = result.error.toUiText())
            }
        }
    }

    private data class OperationState(
        val account: Account? = null,
        val signingIn: Boolean = false,
        val loadingAccount: Boolean = false,
        val loginError: UiText? = null,
        val accountError: UiText? = null,
        val avatarError: UiText? = null,
    )
}
