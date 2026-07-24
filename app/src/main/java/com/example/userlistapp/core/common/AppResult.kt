package com.example.userlistapp.core.common

sealed interface AppError {
    data object Network : AppError
    data class Http(val code: Int) : AppError
    data object InvalidData : AppError
    data object InvalidNote : AppError
    data object AuthenticationRequired : AppError
    data object InvalidCredentials : AppError
    data object Storage : AppError
    data object Unknown : AppError
}

sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}
