package com.example.userlistapp.data.remote

import com.example.userlistapp.core.common.EMPTY

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UsersResponseDto(val users: List<UserDto>)

@Serializable
data class UserDto(
    val id: Int,
    val firstName: String = String.EMPTY,
    val lastName: String = String.EMPTY,
    val age: Int = 0,
    val email: String = String.EMPTY,
    val phone: String = String.EMPTY,
    val username: String = String.EMPTY,
    val image: String = String.EMPTY,
    val role: String = String.EMPTY,
    val company: CompanyDto = CompanyDto(),
    val address: AddressDto = AddressDto(),
)

@Serializable
data class CompanyDto(
    val name: String = String.EMPTY,
    val department: String = String.EMPTY,
    @SerialName(COMPANY_TITLE_SERIAL_NAME) val title: String = String.EMPTY,
)

@Serializable
data class AddressDto(
    val address: String = String.EMPTY,
    val city: String = String.EMPTY,
    val state: String = String.EMPTY,
    val country: String = String.EMPTY,
)

private const val COMPANY_TITLE_SERIAL_NAME = "title"
