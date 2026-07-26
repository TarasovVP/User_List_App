package com.example.userlistapp.core.navigation

import kotlinx.serialization.Serializable

@Serializable
data object UsersDestination

@Serializable
data object AccountDestination

@Serializable
data class UserDetailsDestination(val userId: Int)
