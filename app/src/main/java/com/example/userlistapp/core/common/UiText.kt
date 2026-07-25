package com.example.userlistapp.core.common

import android.content.Context
import androidx.annotation.StringRes

data class UiText(
    @StringRes val resourceId: Int,
    val args: List<Any> = emptyList(),
) {
    fun resolve(context: Context): String = context.getString(resourceId, *args.toTypedArray())
}
