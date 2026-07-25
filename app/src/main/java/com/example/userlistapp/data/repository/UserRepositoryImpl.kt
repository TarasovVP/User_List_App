package com.example.userlistapp.data.repository

import com.example.userlistapp.core.common.AppError
import com.example.userlistapp.core.common.AppResult
import com.example.userlistapp.core.common.DefaultDispatcher
import com.example.userlistapp.core.quality.AppQualityMonitor
import com.example.userlistapp.core.quality.NoOpAppQualityMonitor
import com.example.userlistapp.data.local.UserEntity
import com.example.userlistapp.data.local.UserLocalDataSource
import com.example.userlistapp.data.local.UserWithLocal
import com.example.userlistapp.data.remote.UserDto
import com.example.userlistapp.data.remote.UserRemoteDataSource
import com.example.userlistapp.domain.model.User
import com.example.userlistapp.domain.model.RefreshSource
import com.example.userlistapp.domain.repository.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

class UserRepositoryImpl(
    private val remote: UserRemoteDataSource,
    private val local: UserLocalDataSource,
    @DefaultDispatcher private val mappingDispatcher: CoroutineDispatcher,
    private val qualityMonitor: AppQualityMonitor = NoOpAppQualityMonitor,
) : UserRepository {
    override fun observeUsers(): Flow<List<User>> =
        local.observeUsers().map { list -> list.map(UserWithLocal::toDomain) }

    override fun observeUser(userId: Int): Flow<User?> =
        local.observeUser(userId).map { it?.toDomain() }

    override suspend fun refreshUsers(source: RefreshSource): AppResult<Unit> {
        val trace = qualityMonitor.startTrace(USERS_REFRESH_TRACE)
        trace.putAttribute("trigger", source.qualityValue)
        qualityMonitor.setCustomKey("operation", USERS_REFRESH_TRACE)
        qualityMonitor.setCustomKey("refresh_trigger", source.qualityValue)
        qualityMonitor.log("users_refresh_started trigger=${source.qualityValue}")
        return try {
            val remoteUsers = remote.getUsers()
            trace.putMetric("users_received", remoteUsers.size.toLong())
            val entities = withContext(mappingDispatcher) { remoteUsers.map(UserDto::toEntity) }
            require(entities.map { it.id }.distinct().size == entities.size) {
                "Duplicate user ids"
            }
            local.replaceRemoteSnapshot(entities)
            trace.putAttribute("result", "success")
            qualityMonitor.setCustomKey("refresh_result", "success")
            qualityMonitor.log("users_refresh_succeeded count=${entities.size}")
            AppResult.Success(Unit)
        } catch (cancelled: CancellationException) {
            trace.putAttribute("result", "cancelled")
            throw cancelled
        } catch (error: Throwable) {
            val appError = error.toAppError()
            val errorType = appError.qualityValue
            trace.putAttribute("result", "failure")
            trace.putAttribute("error_type", errorType)
            qualityMonitor.setCustomKey("refresh_result", "failure")
            qualityMonitor.setCustomKey("refresh_error_type", errorType)
            qualityMonitor.log("users_refresh_failed type=$errorType")
            if (appError.shouldReportAsNonFatal) qualityMonitor.recordNonFatal(error)
            AppResult.Failure(appError)
        } finally {
            trace.stop()
        }
    }

    override suspend fun setFavorite(userId: Int, favorite: Boolean) =
        operation { local.setFavorite(userId, favorite) }

    override suspend fun saveNote(userId: Int, note: String) =
        operation { local.saveNote(userId, note) }

    override suspend fun deleteNote(userId: Int) = operation { local.deleteNote(userId) }
}

private suspend fun operation(block: suspend () -> Unit): AppResult<Unit> = try {
    block()
    AppResult.Success(Unit)
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Throwable) {
    AppResult.Failure(error.toAppError())
}

private fun Throwable.toAppError(): AppError = when (this) {
    is IOException -> AppError.Network
    is HttpException -> AppError.Http(code())
    is SerializationException, is IllegalArgumentException -> AppError.InvalidData
    is android.database.sqlite.SQLiteException -> AppError.Storage
    else -> AppError.Unknown
}

private val RefreshSource.qualityValue: String get() = name.lowercase()

private val AppError.qualityValue: String
    get() = when (this) {
        AppError.Network -> "network"
        is AppError.Http -> "http_${code}"
        AppError.InvalidData -> "invalid_data"
        AppError.InvalidNote -> "invalid_note"
        AppError.AuthenticationRequired -> "authentication_required"
        AppError.InvalidCredentials -> "invalid_credentials"
        AppError.Storage -> "storage"
        AppError.Unknown -> "unknown"
    }

private val AppError.shouldReportAsNonFatal: Boolean
    get() = this == AppError.InvalidData || this == AppError.Storage || this == AppError.Unknown

private const val USERS_REFRESH_TRACE = "users_refresh"

fun UserDto.toEntity(): UserEntity = UserEntity(
    id = id,
    firstName = firstName,
    lastName = lastName,
    age = age,
    email = email,
    phone = phone,
    username = username,
    imageUrl = image,
    role = role,
    companyName = company.name,
    department = company.department,
    jobTitle = company.title,
    street = address.address,
    city = address.city,
    state = address.state,
    country = address.country,
)

fun UserWithLocal.toDomain(): User = User(
    id, firstName, lastName, age, email, phone, username, imageUrl, role,
    companyName, department, jobTitle, street, city, state, country,
    favoriteCreatedAt != null, note, noteModifiedAt,
)
