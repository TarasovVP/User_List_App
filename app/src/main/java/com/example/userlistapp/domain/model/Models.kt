package com.example.userlistapp.domain.model

data class User(
    val id: Int,
    val firstName: String,
    val lastName: String,
    val age: Int,
    val email: String,
    val phone: String,
    val username: String,
    val imageUrl: String,
    val role: String,
    val companyName: String,
    val department: String,
    val jobTitle: String,
    val street: String,
    val city: String,
    val state: String,
    val country: String,
    val isFavorite: Boolean = false,
    val note: String? = null,
    val noteModifiedAt: Long? = null,
) {
    val fullName: String
        get() = listOf(firstName, lastName).filter(String::isNotBlank).joinToString(" ")
    val initials: String
        get() = listOf(firstName, lastName).mapNotNull {
            it.firstOrNull()?.uppercase()
        }.joinToString("").take(2)
    val fullAddress: String
        get() = listOf(street, city, state, country).filter(String::isNotBlank).joinToString(", ")
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val backgroundSyncEnabled: Boolean = true,
    val lastSuccessfulSync: Long? = null,
)

enum class UserSort { NAME_ASCENDING, NAME_DESCENDING }

enum class SyncState { IDLE, RUNNING, SUCCEEDED, FAILED }

sealed interface SessionState {
    data object Initializing : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val userId: Int) : SessionState
}

data class Account(
    val id: Int,
    val username: String,
    val firstName: String,
    val lastName: String,
    val email: String,
    val remoteImageUrl: String,
) {
    val fullName: String
        get() = listOf(firstName, lastName).filter(String::isNotBlank).joinToString(" ")
}
