package com.example.userlistapp.domain.usecase

import com.example.userlistapp.core.common.AppError
import com.example.userlistapp.core.common.AppResult
import com.example.userlistapp.domain.repository.AuthSessionRepository
import javax.inject.Inject

class ObserveAuthSessionUseCase @Inject constructor(
    private val repository: AuthSessionRepository,
) {
    operator fun invoke() = repository.sessionState
}

class ObserveLocalAvatarUseCase @Inject constructor(
    private val repository: AuthSessionRepository,
) {
    operator fun invoke() = repository.localAvatarUri
}

class SignInUseCase @Inject constructor(
    private val repository: AuthSessionRepository,
) {
    suspend operator fun invoke(username: String, password: String) =
        if (username.isBlank() || password.isBlank()) {
            AppResult.Failure(AppError.InvalidCredentials)
        } else {
            repository.signIn(username, password)
        }
}

class SignOutUseCase @Inject constructor(
    private val repository: AuthSessionRepository,
) {
    suspend operator fun invoke() = repository.signOut()
}

class LoadAccountUseCase @Inject constructor(
    private val repository: AuthSessionRepository,
) {
    suspend operator fun invoke(userId: Int) = repository.loadAccount(userId)
}

class ImportLocalAvatarUseCase @Inject constructor(
    private val repository: AuthSessionRepository,
) {
    suspend operator fun invoke(sourceUri: String) = repository.importLocalAvatar(sourceUri)
}

class RemoveLocalAvatarUseCase @Inject constructor(
    private val repository: AuthSessionRepository,
) {
    suspend operator fun invoke() = repository.removeLocalAvatar()
}

class AuthUseCases @Inject constructor(
    val observeSession: ObserveAuthSessionUseCase,
    val observeLocalAvatar: ObserveLocalAvatarUseCase,
    val signIn: SignInUseCase,
    val signOut: SignOutUseCase,
    val loadAccount: LoadAccountUseCase,
    val importLocalAvatar: ImportLocalAvatarUseCase,
    val removeLocalAvatar: RemoveLocalAvatarUseCase,
)
