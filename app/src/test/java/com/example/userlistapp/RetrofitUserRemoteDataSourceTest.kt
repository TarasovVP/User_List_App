package com.example.userlistapp

import com.example.userlistapp.data.remote.RetrofitUserRemoteDataSource
import com.example.userlistapp.data.remote.UserApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class RetrofitUserRemoteDataSourceTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `complete user request uses the contract limit query parameter`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"users":[]}"""),
        )
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(
                Json { ignoreUnknownKeys = true }
                    .asConverterFactory("application/json".toMediaType()),
            )
            .build()
            .create(UserApi::class.java)

        RetrofitUserRemoteDataSource(api).getUsers()

        assertEquals("/users?limit=0", server.takeRequest().path)
    }
}
