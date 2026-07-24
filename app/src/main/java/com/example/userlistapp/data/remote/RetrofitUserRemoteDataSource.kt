package com.example.userlistapp.data.remote

class RetrofitUserRemoteDataSource(
    private val api: UserApi,
) : UserRemoteDataSource {
    override suspend fun getUsers(): List<UserDto> = api.getUsers(limit = ALL_USERS_LIMIT).users

    private companion object {
        const val ALL_USERS_LIMIT = 0
    }
}
