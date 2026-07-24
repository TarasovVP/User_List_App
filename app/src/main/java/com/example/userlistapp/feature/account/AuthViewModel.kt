package com.example.userlistapp.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.userlistapp.core.common.AppResult
import com.example.userlistapp.core.common.UiText
import com.example.userlistapp.core.common.toUiText
import com.example.userlistapp.domain.model.Account
import com.example.userlistapp.domain.model.SessionState
import com.example.userlistapp.domain.repository.AuthSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

data class AuthUiState(
    val session: SessionState = SessionState.Initializing,
    val account: Account? = null,
    val localAvatarUri: String? = null,
    val isSigningIn: Boolean = false,
    val isAccountLoading: Boolean = false,
    val loginError: UiText? = null,
    val accountError: UiText? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthSessionRepository,
) : ViewModel() {
    private val operation = MutableStateFlow(OperationState())
    private val signingIn = AtomicBoolean(false)

    val uiState: StateFlow<AuthUiState> = combine(
        repository.sessionState,
        repository.localAvatarUri,
        operation,
    ) { session, avatar, op ->
        AuthUiState(
            session,
            op.account,
            avatar,
            op.signingIn,
            op.loadingAccount,
            op.loginError,
            op.accountError
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthUiState())

    init {
        viewModelScope.launch {
            repository.sessionState.collect { session ->
                if (session is SessionState.SignedIn && operation.value.account?.id != session.userId) {
                    loadAccount(session.userId)
                }
            }
        }
    }

    fun signIn(username: String, password: String) {
        if (!signingIn.compareAndSet(false, true)) return
        if (username.isBlank() || password.isBlank()) {
            operation.value =
                operation.value.copy(loginError = com.example.userlistapp.core.common.AppError.InvalidCredentials.toUiText())
            signingIn.set(false)
            return
        }
        viewModelScope.launch {
            operation.value = operation.value.copy(signingIn = true, loginError = null)
            when (val result = repository.signIn(username, password)) {
                is AppResult.Success -> operation.value =
                    operation.value.copy(account = result.value, signingIn = false)

                is AppResult.Failure -> operation.value =
                    operation.value.copy(signingIn = false, loginError = result.error.toUiText())
            }
            signingIn.set(false)
        }
    }

    fun retryAccount() {
        val signedIn = uiState.value.session as? SessionState.SignedIn ?: return
        viewModelScope.launch { loadAccount(signedIn.userId) }
    }

    fun signOut() {
        if (!signingIn.compareAndSet(false, true)) return
        viewModelScope.launch {
            repository.signOut()
            operation.value = OperationState()
            signingIn.set(false)
        }
    }

    fun setLocalAvatar(uri: String?) {
        viewModelScope.launch { repository.setLocalAvatar(uri) }
    }

    fun clearLoginError() {
        operation.value = operation.value.copy(loginError = null)
    }

    private suspend fun loadAccount(userId: Int) {
        operation.value = operation.value.copy(loadingAccount = true, accountError = null)
        when (val result = repository.loadAccount(userId)) {
            is AppResult.Success -> operation.value =
                operation.value.copy(account = result.value, loadingAccount = false)

            is AppResult.Failure -> operation.value =
                operation.value.copy(loadingAccount = false, accountError = result.error.toUiText())
        }
    }

    private data class OperationState(
        val account: Account? = null,
        val signingIn: Boolean = false,
        val loadingAccount: Boolean = false,
        val loginError: UiText? = null,
        val accountError: UiText? = null,
    )
}
