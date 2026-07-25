package com.example.userlistapp.core.ui

import com.example.userlistapp.R
import com.example.userlistapp.core.common.AppError
import com.example.userlistapp.core.common.UiText

fun AppError.toUiText(): UiText = when (this) {
    AppError.Network -> UiText(R.string.error_network)
    is AppError.Http -> UiText(
        resourceId = if (code >= 500) R.string.error_service else R.string.error_request,
        args = listOf(code),
    )

    AppError.InvalidData -> UiText(R.string.error_data)
    AppError.InvalidNote -> UiText(R.string.error_invalid_note)
    AppError.AuthenticationRequired -> UiText(R.string.authentication_required)
    AppError.InvalidCredentials -> UiText(R.string.error_invalid_credentials)
    AppError.Storage -> UiText(R.string.error_storage)
    AppError.Unknown -> UiText(R.string.error_unknown)
}
