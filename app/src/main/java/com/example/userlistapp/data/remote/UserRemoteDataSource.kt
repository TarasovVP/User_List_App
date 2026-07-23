package com.example.userlistapp.data.remote

interface UserRemoteDataSource {
    suspend fun getUsers(): List<UserDto>
}
