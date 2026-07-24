package com.example.userlistapp.domain.usecase

import com.example.userlistapp.core.common.AppError
import com.example.userlistapp.core.common.AppResult
import com.example.userlistapp.domain.model.SessionState
import com.example.userlistapp.domain.repository.AuthSessionRepository
import com.example.userlistapp.domain.repository.UserRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ObserveUsersUseCase @Inject constructor(private val repository: UserRepository) {
    operator fun invoke() = repository.observeUsers()
}

class RefreshUsersUseCase @Inject constructor(
    private val repository: UserRepository,
    private val sessionRepository: AuthSessionRepository,
) {
    suspend operator fun invoke(): AppResult<Unit> =
        if (sessionRepository.sessionState.first() is SessionState.SignedIn) repository.refreshUsers()
        else AppResult.Failure(AppError.AuthenticationRequired)
}

class ObserveUserDetailsUseCase @Inject constructor(private val repository: UserRepository) {
    operator fun invoke(userId: Int) = repository.observeUser(userId)
}

class ToggleFavoriteUseCase @Inject constructor(private val repository: UserRepository) {
    suspend operator fun invoke(userId: Int, current: Boolean) =
        repository.setFavorite(userId, !current)
}

class SaveUserNoteUseCase @Inject constructor(private val repository: UserRepository) {
    suspend operator fun invoke(userId: Int, note: String): AppResult<Unit> {
        val normalized = note.trim()
        if (normalized.isEmpty()) return AppResult.Failure(AppError.InvalidNote)
        return repository.saveNote(userId, normalized)
    }
}

class DeleteUserNoteUseCase @Inject constructor(private val repository: UserRepository) {
    suspend operator fun invoke(userId: Int) = repository.deleteNote(userId)
}
