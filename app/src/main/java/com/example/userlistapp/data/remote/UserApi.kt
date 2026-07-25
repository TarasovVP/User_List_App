package com.example.userlistapp.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface UserApi {
    @GET(USERS_PATH)
    suspend fun getUsers(@Query(LIMIT_QUERY_PARAMETER) limit: Int): UsersResponseDto
}

private const val USERS_PATH = "users"
private const val LIMIT_QUERY_PARAMETER = "limit"
