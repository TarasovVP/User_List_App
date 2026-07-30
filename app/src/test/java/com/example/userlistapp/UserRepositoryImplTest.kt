package com.example.userlistapp

import com.example.userlistapp.core.common.AppError
import com.example.userlistapp.core.common.AppResult
import com.example.userlistapp.core.common.EMPTY
import com.example.userlistapp.core.quality.AppQualityMonitor
import com.example.userlistapp.core.quality.QualityTrace
import com.example.userlistapp.data.local.UserEntity
import com.example.userlistapp.data.local.UserLocalDataSource
import com.example.userlistapp.data.local.UserWithLocal
import com.example.userlistapp.data.remote.CompanyDto
import com.example.userlistapp.data.remote.UserDto
import com.example.userlistapp.data.remote.UserRemoteDataSource
import com.example.userlistapp.data.repository.UserRepositoryImpl
import com.example.userlistapp.domain.model.RefreshSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import retrofit2.HttpException
import retrofit2.Response

class UserRepositoryImplTest {
    @Test
    fun `refresh maps and persists complete snapshot`() = runTest {
        val local = FakeLocal()
        val monitor = RecordingQualityMonitor()
        val repo = UserRepositoryImpl(
            FakeRemote(
                listOf(
                    UserDto(
                        id = 5,
                        firstName = "Ada",
                        company = CompanyDto(name = "Math")
                    )
                )
            ), local, Dispatchers.Unconfined, monitor
        )
        assertTrue(repo.refreshUsers(RefreshSource.INITIAL) is AppResult.Success)
        assertEquals(5, local.saved.single().id)
        assertEquals("Math", local.saved.single().companyName)
        assertEquals("initial", monitor.trace.attributes["trigger"])
        assertEquals("success", monitor.trace.attributes["result"])
        assertEquals(1L, monitor.trace.metrics["users_received"])
        assertTrue(monitor.trace.stopped)
    }

    @Test
    fun `network failure preserves cached content and maps error`() = runTest {
        val local = FakeLocal().apply { saved = listOf(entity(9)) }
        val monitor = RecordingQualityMonitor()
        val repo = UserRepositoryImpl(object : UserRemoteDataSource {
            override suspend fun getUsers(): List<UserDto> = throw IOException()
        }, local, Dispatchers.Unconfined, monitor)
        val result = repo.refreshUsers(RefreshSource.MANUAL)
        assertEquals(AppResult.Failure(AppError.Network), result)
        assertEquals(9, local.saved.single().id)
        assertEquals("network", monitor.trace.attributes["error_type"])
        assertTrue(monitor.nonFatals.isEmpty())
    }

    @Test
    fun `unsuccessful HTTP response preserves cached content and maps status`() = runTest {
        val local = FakeLocal().apply { saved = listOf(entity(9)) }
        val repo = UserRepositoryImpl(object : UserRemoteDataSource {
            override suspend fun getUsers(): List<UserDto> = throw HttpException(
                Response.error<Unit>(500, "server error".toResponseBody()),
            )
        }, local, Dispatchers.Unconfined)

        val result = repo.refreshUsers(RefreshSource.MANUAL)

        assertEquals(AppResult.Failure(AppError.Http(500)), result)
        assertEquals(listOf(9), local.saved.map { it.id })
    }

    @Test
    fun `malformed response preserves cached content and maps invalid data`() = runTest {
        val local = FakeLocal().apply { saved = listOf(entity(9)) }
        val repo = UserRepositoryImpl(object : UserRemoteDataSource {
            override suspend fun getUsers(): List<UserDto> =
                throw SerializationException("Malformed users response")
        }, local, Dispatchers.Unconfined)

        val result = repo.refreshUsers(RefreshSource.RETRY)

        assertEquals(AppResult.Failure(AppError.InvalidData), result)
        assertEquals(listOf(9), local.saved.map { it.id })
    }

    @Test
    fun `refresh preserves favorite and note metadata owned by local storage`() = runTest {
        val local = FakeLocal().apply {
            saved = listOf(entity(1))
            favoriteIds += 1
            notes[1] = "Keep this"
        }
        val remote = FakeRemote(listOf(UserDto(id = 1, firstName = "Updated")))

        val result = UserRepositoryImpl(remote, local, Dispatchers.Unconfined).refreshUsers()

        assertTrue(result is AppResult.Success)
        assertEquals("Updated", local.saved.single().firstName)
        assertEquals(setOf(1), local.favoriteIds)
        assertEquals(mapOf(1 to "Keep this"), local.notes)
    }

    @Test
    fun `large snapshot is accepted without SQLite parameter assumptions`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote(List(1_000) { index -> UserDto(id = index + 1) })
        val result = UserRepositoryImpl(remote, local, Dispatchers.Unconfined).refreshUsers()

        assertTrue(result is AppResult.Success)
        assertEquals(1_000, local.saved.size)
    }

    @Test
    fun `duplicate remote IDs are rejected without changing local cache`() = runTest {
        val local = FakeLocal().apply { saved = listOf(entity(9)) }
        val monitor = RecordingQualityMonitor()
        val remote = FakeRemote(
            listOf(
                UserDto(id = 1, firstName = "Ada"),
                UserDto(id = 1, firstName = "Duplicate"),
            ),
        )

        val result = UserRepositoryImpl(
            remote,
            local,
            Dispatchers.Unconfined,
            monitor,
        ).refreshUsers(RefreshSource.RETRY)

        assertEquals(AppResult.Failure(AppError.InvalidData), result)
        assertEquals(listOf(9), local.saved.map { it.id })
        assertEquals("retry", monitor.trace.attributes["trigger"])
        assertEquals(1, monitor.nonFatals.size)
    }

    @Test
    fun `empty remote snapshot never discards a populated cache`() = runTest {
        val local = FakeLocal().apply { saved = listOf(entity(9)) }
        val monitor = RecordingQualityMonitor()

        val result = UserRepositoryImpl(
            FakeRemote(emptyList()),
            local,
            Dispatchers.Unconfined,
            monitor,
        ).refreshUsers()

        assertEquals(AppResult.Failure(AppError.InvalidData), result)
        assertEquals(listOf(9), local.saved.map { it.id })
        assertEquals("invalid_data", monitor.trace.attributes["error_type"])
    }

    @Test
    fun `empty remote snapshot is a valid result while the cache is empty`() = runTest {
        val local = FakeLocal()

        val result = UserRepositoryImpl(
            FakeRemote(emptyList()),
            local,
            Dispatchers.Unconfined,
        ).refreshUsers()

        assertTrue(result is AppResult.Success)
        assertTrue(local.saved.isEmpty())
    }

    @Test(expected = CancellationException::class)
    fun `refresh never hides cancellation`() = runTest {
        UserRepositoryImpl(object : UserRemoteDataSource {
            override suspend fun getUsers(): List<UserDto> = throw CancellationException()
        }, FakeLocal(), Dispatchers.Unconfined).refreshUsers()
    }

    @Test
    fun `note save and delete reach local storage`() = runTest {
        val local = FakeLocal()
        val repo = UserRepositoryImpl(FakeRemote(emptyList()), local, Dispatchers.Unconfined)
        assertTrue(repo.saveNote(4, "remember") is AppResult.Success)
        assertEquals("remember", local.note)
        assertTrue(repo.deleteNote(4) is AppResult.Success)
        assertEquals(null, local.note)
    }
}

private class RecordingQualityMonitor : AppQualityMonitor {
    override val isEnabled = true
    val trace = RecordingQualityTrace()
    val nonFatals = mutableListOf<Throwable>()
    val keys = mutableMapOf<String, String>()
    val logs = mutableListOf<String>()

    override fun startTrace(name: String): QualityTrace = trace
    override fun log(message: String) {
        logs += message
    }

    override fun setCustomKey(name: String, value: String) {
        keys[name] = value
    }

    override fun recordNonFatal(error: Throwable) {
        nonFatals += error
    }
}

private class RecordingQualityTrace : QualityTrace {
    val attributes = mutableMapOf<String, String>()
    val metrics = mutableMapOf<String, Long>()
    var stopped = false

    override fun putAttribute(name: String, value: String) {
        attributes[name] = value
    }

    override fun putMetric(name: String, value: Long) {
        metrics[name] = value
    }

    override fun stop() {
        stopped = true
    }
}

private class FakeRemote(private val result: List<UserDto>) : UserRemoteDataSource {
    override suspend fun getUsers() = result
}

private class FakeLocal : UserLocalDataSource {
    var saved: List<UserEntity> = emptyList()
    var note: String? = null
    val favoriteIds = mutableSetOf<Int>()
    val notes = mutableMapOf<Int, String>()
    private val rows = MutableStateFlow<List<UserWithLocal>>(emptyList())
    override fun observeUsers(): Flow<List<UserWithLocal>> = rows
    override fun observeUser(userId: Int): Flow<UserWithLocal?> =
        rows.map { it.firstOrNull { row -> row.id == userId } }

    override suspend fun countUsers() = saved.size

    override suspend fun replaceRemoteSnapshot(users: List<UserEntity>) {
        saved = users
    }

    override suspend fun setFavorite(userId: Int, favorite: Boolean) {
        if (favorite) favoriteIds += userId else favoriteIds -= userId
    }

    override suspend fun saveNote(userId: Int, note: String) {
        this.note = note
        notes[userId] = note
    }

    override suspend fun deleteNote(userId: Int) {
        note = null
        notes.remove(userId)
    }
}

private fun entity(id: Int) = UserEntity(
    id,
    "A",
    "B",
    1,
    "e",
    "p",
    "u",
    String.EMPTY,
    "r",
    "c",
    "d",
    "t",
    "s",
    "city",
    "state",
    "country",
    0
)
