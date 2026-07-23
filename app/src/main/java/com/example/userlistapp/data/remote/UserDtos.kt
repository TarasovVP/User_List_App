package com.example.userlistapp.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UsersResponseDto(val users: List<UserDto> = emptyList())

@Serializable
data class UserDto(
    val id: Int,
    val firstName: String = "",
    val lastName: String = "",
    val age: Int = 0,
    val email: String = "",
    val phone: String = "",
    val username: String = "",
    val image: String = "",
    val role: String = "",
    val company: CompanyDto = CompanyDto(),
    val address: AddressDto = AddressDto(),
)

@Serializable
data class CompanyDto(
    val name: String = "",
    val department: String = "",
    @SerialName("title") val title: String = "",
)

@Serializable
data class AddressDto(
    val address: String = "",
    val city: String = "",
    val state: String = "",
    val country: String = "",
)
