package com.example.userlistapp.data.remote

import com.example.userlistapp.core.common.EMPTY

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

@Serializable
data class LoginRequestDto(val username: String, val password: String)

@Serializable
data class AccountDto(
    val id: Int = 0,
    val username: String = String.EMPTY,
    val firstName: String = String.EMPTY,
    val lastName: String = String.EMPTY,
    val email: String = String.EMPTY,
    val image: String = String.EMPTY,
    val accessToken: String = String.EMPTY,
)

interface AuthApi {
    @POST(AUTH_LOGIN_PATH)
    suspend fun login(@Body request: LoginRequestDto): AccountDto

    @GET(USER_BY_ID_PATH)
    suspend fun getAccount(@Path(USER_ID_PATH_PARAMETER) id: Int): AccountDto
}

private const val AUTH_LOGIN_PATH = "auth/login"
private const val USER_BY_ID_PATH = "users/{id}"
private const val USER_ID_PATH_PARAMETER = "id"
