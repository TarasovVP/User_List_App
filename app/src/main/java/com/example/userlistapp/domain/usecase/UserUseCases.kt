package com.example.userlistapp.domain.usecase

import com.example.userlistapp.core.common.AppError
import com.example.userlistapp.core.common.AppResult
import com.example.userlistapp.domain.repository.UserRepository
import javax.inject.Inject

// Use cases are the deliberate application boundary between feature code and repositories.
// Simple delegates stay here so validation and orchestration can evolve without changing ViewModels.
class ObserveUsersUseCase @Inject constructor(private val repository: UserRepository) {
    operator fun invoke() = repository.observeUsers()
}
class RefreshUsersUseCase @Inject constructor(private val repository: UserRepository) {
    suspend operator fun invoke() = repository.refreshUsers()
}
class ObserveUserDetailsUseCase @Inject constructor(private val repository: UserRepository) {
    operator fun invoke(userId: Int) = repository.observeUser(userId)
}
class ToggleFavoriteUseCase @Inject constructor(private val repository: UserRepository) {
    suspend operator fun invoke(userId: Int, current: Boolean) = repository.setFavorite(userId, !current)
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
