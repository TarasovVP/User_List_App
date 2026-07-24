package com.example.userlistapp.data.remote

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
    val username: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val image: String = "",
)

interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequestDto): AccountDto

    @GET("users/{id}")
    suspend fun getAccount(@Path("id") id: Int): AccountDto
}
