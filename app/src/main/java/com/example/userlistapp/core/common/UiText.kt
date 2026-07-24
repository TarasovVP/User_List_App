package com.example.userlistapp.core.common

import android.content.Context
import androidx.annotation.StringRes
import com.example.userlistapp.R

data class UiText(
    @StringRes val resourceId: Int,
    val args: List<Any> = emptyList(),
) {
    fun resolve(context: Context): String = context.getString(resourceId, *args.toTypedArray())
}

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
