package com.example.userlistapp.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface UserApi {
    @GET("users")
    suspend fun getUsers(@Query("limit") limit: Int): UsersResponseDto
}
