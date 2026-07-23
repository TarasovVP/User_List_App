package com.example.userlistapp.navigation

import kotlinx.serialization.Serializable

@Serializable
data object UsersDestination

@Serializable
data class UserDetailsDestination(val userId: Int)

@Serializable
data object SettingsDestination
