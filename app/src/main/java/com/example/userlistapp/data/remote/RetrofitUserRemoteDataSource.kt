package com.example.userlistapp.data.remote

class RetrofitUserRemoteDataSource(
    private val api: UserApi,
) : UserRemoteDataSource {
    override suspend fun getUsers(): List<UserDto> = api.getUsers(limit = ALL_USERS_LIMIT).users

    private companion object {
        // DummyJSON defines limit=0 as "return all users"; omitting it returns the default page.
        const val ALL_USERS_LIMIT = 0
    }
}
