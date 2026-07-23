package com.example.userlistapp.feature.users.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.userlistapp.core.common.AppResult
import com.example.userlistapp.core.common.UiText
import com.example.userlistapp.core.common.toUiText
import com.example.userlistapp.domain.model.User
import com.example.userlistapp.domain.usecase.DeleteUserNoteUseCase
import com.example.userlistapp.domain.usecase.ObserveUserDetailsUseCase
import com.example.userlistapp.domain.usecase.SaveUserNoteUseCase
import com.example.userlistapp.domain.usecase.ToggleFavoriteUseCase
import com.example.userlistapp.navigation.UserDetailsDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UserDetailsUiState(
    val user: User? = null,
    val noteDraft: String = "",
    val isSaving: Boolean = false,
    val isLoading: Boolean = true,
) {
    val canSave: Boolean get() = !isSaving && noteDraft.isNotBlank() && noteDraft.trim() != user?.note.orEmpty()
    val canDelete: Boolean get() = !isSaving && !user?.note.isNullOrBlank()
    val canToggleFavorite: Boolean get() = !isSaving && user != null
}

@HiltViewModel
class UserDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeUser: ObserveUserDetailsUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
    private val saveNote: SaveUserNoteUseCase,
    private val deleteNote: DeleteUserNoteUseCase,
) : ViewModel() {
    private val userId: Int = savedStateHandle.toRoute<UserDetailsDestination>().userId
    private val draft = MutableStateFlow<String?>(null)
    private val activeOperations = MutableStateFlow(0)
    private val _events = MutableSharedFlow<UiText>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    // Keeps the nullable user flow structurally distinct from the nullable draft flow in combine.
    val uiState: StateFlow<UserDetailsUiState> = combine(
        observeUser(userId).map(::ObservedUser),
        draft,
        activeOperations,
    ) { observed, edited, operationCount ->
        UserDetailsUiState(
            user = observed.user,
            noteDraft = edited ?: observed.user?.note.orEmpty(),
            isSaving = operationCount > 0,
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserDetailsUiState())

    fun setNoteDraft(value: String) { draft.value = value }

    fun toggleFavorite() {
        if (!uiState.value.canToggleFavorite) return
        runOperation { user -> toggleFavorite(user.id, user.isFavorite) }
    }

    fun saveNote() {
        val state = uiState.value
        if (!state.canSave) return
        val note = state.noteDraft.trim()
        runOperation(success = { draft.value = null }) { user -> saveNote(user.id, note) }
    }

    fun deleteNote() {
        if (!uiState.value.canDelete) return
        runOperation(success = { draft.value = null }) { user -> deleteNote(user.id) }
    }

    private fun runOperation(
        success: () -> Unit = {},
        operation: suspend (User) -> AppResult<Unit>,
    ) {
        val user = uiState.value.user ?: return
        if (!activeOperations.compareAndSet(expect = 0, update = 1)) return
        viewModelScope.launch {
            try {
                when (val result = operation(user)) {
                    is AppResult.Success -> success()
                    is AppResult.Failure -> _events.tryEmit(result.error.toUiText())
                }
            } finally {
                activeOperations.value = 0
            }
        }
    }

    private data class ObservedUser(val user: User?)
}
