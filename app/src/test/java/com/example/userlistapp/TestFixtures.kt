package com.example.userlistapp

import com.example.userlistapp.domain.model.User
import com.example.userlistapp.domain.repository.AuthSessionRepository
import com.example.userlistapp.domain.usecase.AuthUseCases
import com.example.userlistapp.domain.usecase.ImportLocalAvatarUseCase
import com.example.userlistapp.domain.usecase.LoadAccountUseCase
import com.example.userlistapp.domain.usecase.ObserveAuthSessionUseCase
import com.example.userlistapp.domain.usecase.ObserveLocalAvatarUseCase
import com.example.userlistapp.domain.usecase.RemoveLocalAvatarUseCase
import com.example.userlistapp.domain.usecase.SignInUseCase
import com.example.userlistapp.domain.usecase.SignOutUseCase

fun authUseCases(repository: AuthSessionRepository) = AuthUseCases(
    observeSession = ObserveAuthSessionUseCase(repository),
    observeLocalAvatar = ObserveLocalAvatarUseCase(repository),
    signIn = SignInUseCase(repository),
    signOut = SignOutUseCase(repository),
    loadAccount = LoadAccountUseCase(repository),
    importLocalAvatar = ImportLocalAvatarUseCase(repository),
    removeLocalAvatar = RemoveLocalAvatarUseCase(repository),
)

fun sampleUser(
    id: Int = 1,
    firstName: String = "Ada",
    lastName: String = "Lovelace",
    favorite: Boolean = false,
    note: String? = null,
) = User(
    id = id,
    firstName = firstName,
    lastName = lastName,
    age = 36,
    email = "${firstName.lowercase()}@example.com",
    phone = "123",
    username = firstName.lowercase(),
    imageUrl = "",
    role = "user",
    companyName = "Analytical",
    department = "R&D",
    jobTitle = "Engineer",
    street = "1 Main",
    city = "London",
    state = "England",
    country = "UK",
    isFavorite = favorite,
    note = note,
)
