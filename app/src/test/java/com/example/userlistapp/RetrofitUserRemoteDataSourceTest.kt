package com.example.userlistapp

import com.example.userlistapp.data.remote.RetrofitUserRemoteDataSource
import com.example.userlistapp.data.remote.UserApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers.Companion.headersOf
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit.MILLISECONDS

class RetrofitUserRemoteDataSourceTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `successful response is deserialized and request matches the users contract`() = runTest {
        server.enqueue(jsonResponse(SUCCESS_BODY))

        val users = dataSource().getUsers()
        val request = server.takeRequest()

        assertEquals(1, users.single().id)
        assertEquals("Ada", users.single().firstName)
        assertEquals("Analytical Engines", users.single().company.name)
        assertEquals("GET", request.method)
        assertEquals("/users?limit=0", request.target)
    }

    @Test
    fun `HTTP errors are propagated`() = runTest {
        server.enqueue(jsonResponse("""{"message":"unavailable"}""", code = 503))

        val error = runCatching { dataSource().getUsers() }.exceptionOrNull()

        assertTrue(error is HttpException)
        assertEquals(503, (error as HttpException).code())
    }

    @Test
    fun `malformed JSON is propagated`() = runTest {
        server.enqueue(jsonResponse("""{"users":[}"""))

        val error = runCatching { dataSource().getUsers() }.exceptionOrNull()

        assertTrue(error is SerializationException)
    }

    @Test
    fun `delayed body exceeds the test client timeout`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .addHeader("Content-Type", "application/json")
                .body(SUCCESS_BODY)
                .bodyDelay(500, MILLISECONDS)
                .build()
        )
        val shortTimeoutClient = OkHttpClient.Builder()
            .readTimeout(100, MILLISECONDS)
            .build()

        val error = runCatching { dataSource(shortTimeoutClient).getUsers() }.exceptionOrNull()

        assertTrue(error is SocketTimeoutException)
        assertEquals(1, server.requestCount)
    }

    private fun dataSource(client: OkHttpClient = OkHttpClient()): RetrofitUserRemoteDataSource {
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(JSON.asConverterFactory(JSON_MEDIA_TYPE.toMediaType()))
            .build()
            .create(UserApi::class.java)
        return RetrofitUserRemoteDataSource(api)
    }

    private fun jsonResponse(body: String, code: Int = 200) = MockResponse(
        code = code,
        headers = headersOf("Content-Type", JSON_MEDIA_TYPE),
        body = body,
    )

    private companion object {
        const val JSON_MEDIA_TYPE = "application/json"
        const val SUCCESS_BODY = """
            {
              "users": [{
                "id": 1,
                "firstName": "Ada",
                "lastName": "Lovelace",
                "company": {"name": "Analytical Engines"}
              }]
            }
        """
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
