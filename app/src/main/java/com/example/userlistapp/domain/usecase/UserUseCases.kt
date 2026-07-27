package com.example.userlistapp.domain.usecase

import com.example.userlistapp.core.common.AppError
import com.example.userlistapp.core.common.AppResult
import com.example.userlistapp.core.common.TimeProvider
import com.example.userlistapp.domain.model.RefreshSource
import com.example.userlistapp.domain.model.SessionState
import com.example.userlistapp.domain.model.User
import com.example.userlistapp.domain.model.UserSort
import com.example.userlistapp.domain.repository.AuthSessionGuard
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
    private val sessionGuard: AuthSessionGuard,
) {
    suspend operator fun invoke(source: RefreshSource = RefreshSource.MANUAL): AppResult<Unit> =
        sessionGuard.withLock {
            if (sessionRepository.sessionState.first() is SessionState.SignedIn) {
                repository.refreshUsers(source)
            } else {
                AppResult.Failure(AppError.AuthenticationRequired)
            }
        }
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
        if (normalized.isEmpty() || normalized.length > MAX_NOTE_LENGTH) {
            return AppResult.Failure(AppError.InvalidNote)
        }
        return repository.saveNote(userId, normalized)
    }

    companion object {
        const val MAX_NOTE_LENGTH = 500
    }
}

class DeleteUserNoteUseCase @Inject constructor(private val repository: UserRepository) {
    suspend operator fun invoke(userId: Int) = repository.deleteNote(userId)
}

class MarkUserAsViewedUseCase @Inject constructor(
    private val repository: UserRepository,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(userId: Int) =
        repository.markUserAsViewed(userId, timeProvider.currentTimeMillis())
}

class FilterAndSortUsersUseCase @Inject constructor() {
    operator fun invoke(
        users: List<User>,
        query: String,
        sort: UserSort,
        favoritesOnly: Boolean,
    ): List<User> {
        val terms = query.trim().split(QUERY_WHITESPACE).filter(String::isNotEmpty)
        val nameComparator = compareBy<User> { it.fullName.lowercase() }
        val recentlyViewedComparator = compareByDescending<User> { it.viewedAt != null }
            .thenByDescending { it.viewedAt }
            .then(compareBy(String.CASE_INSENSITIVE_ORDER) { it.fullName })
            .thenBy { it.id }

        return users.asSequence()
            .filter { !favoritesOnly || it.isFavorite }
            .filter { user ->
                val searchableFields = listOf(user.fullName, user.email, user.companyName)
                terms.all { term ->
                    searchableFields.any { value -> value.contains(term, ignoreCase = true) }
                }
            }
            .sortedWith(
                when (sort) {
                    UserSort.NAME_ASCENDING -> nameComparator
                    UserSort.NAME_DESCENDING -> nameComparator.reversed()
                    UserSort.RECENTLY_VIEWED -> recentlyViewedComparator
                },
            )
            .toList()
    }

    private companion object {
        val QUERY_WHITESPACE = Regex("\\s+")
    }
}
