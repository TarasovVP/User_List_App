package com.example.userlistapp.core.ui

object UiTestTags {
    const val LOGIN_USERNAME = "login_username"
    const val LOGIN_PASSWORD = "login_password"
    const val LOGIN_SUBMIT = "login_submit"
    const val SIGN_IN_OPEN = "sign_in_open"
    const val SEARCH = "search"
    const val USER_LIST = "user_list"
    const val USER_LIST_LOADING = "user_list_loading"
    const val USER_LIST_ERROR = "user_list_error"
    const val USER_LIST_EMPTY = "user_list_empty"
    const val FAVORITE_BUTTON = "favorite_button"
    const val NOTE_FIELD = "note_field"
    const val DELETE_NOTE = "delete_note"
    const val SAVE_NOTE = "save_note"

    private const val USER_PREFIX = "user_"
    private const val FAVORITE_PREFIX = "favorite_"

    fun user(userId: Int): String = USER_PREFIX + userId

    fun favorite(userId: Int): String = FAVORITE_PREFIX + userId
}

object UiAnimationLabels {
    const val SEARCH_TITLE = "search_title"
    const val FAVORITE_ICON = "favorite_icon"
    const val USER_LIST_CONTENT = "user_list_content"
}
