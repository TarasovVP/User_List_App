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
import com.example.userlistapp.domain.model.RefreshSource
import com.example.userlistapp.domain.model.User
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
        trace.putAttribute(TRIGGER_ATTRIBUTE, source.qualityValue)
        qualityMonitor.setCustomKey(OPERATION_KEY, USERS_REFRESH_TRACE)
        qualityMonitor.setCustomKey(REFRESH_TRIGGER_KEY, source.qualityValue)
        qualityMonitor.log(REFRESH_STARTED_LOG_PREFIX + source.qualityValue)
        return try {
            val remoteUsers = remote.getUsers()
            trace.putMetric(USERS_RECEIVED_METRIC, remoteUsers.size.toLong())
            val entities = withContext(mappingDispatcher) { remoteUsers.map(UserDto::toEntity) }
            require(entities.map { it.id }.distinct().size == entities.size) {
                DUPLICATE_USER_IDS_ERROR
            }
            require(entities.isNotEmpty() || local.countUsers() == 0) { EMPTY_SNAPSHOT_ERROR }
            local.replaceRemoteSnapshot(entities)
            trace.putAttribute(RESULT_ATTRIBUTE, SUCCESS_VALUE)
            qualityMonitor.setCustomKey(REFRESH_RESULT_KEY, SUCCESS_VALUE)
            qualityMonitor.log(REFRESH_SUCCEEDED_LOG_PREFIX + entities.size)
            AppResult.Success(Unit)
        } catch (cancelled: CancellationException) {
            trace.putAttribute(RESULT_ATTRIBUTE, CANCELLED_VALUE)
            throw cancelled
        } catch (error: Throwable) {
            val appError = error.toAppError()
            val errorType = appError.qualityValue
            trace.putAttribute(RESULT_ATTRIBUTE, FAILURE_VALUE)
            trace.putAttribute(ERROR_TYPE_ATTRIBUTE, errorType)
            qualityMonitor.setCustomKey(REFRESH_RESULT_KEY, FAILURE_VALUE)
            qualityMonitor.setCustomKey(REFRESH_ERROR_TYPE_KEY, errorType)
            qualityMonitor.log(REFRESH_FAILED_LOG_PREFIX + errorType)
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

    override suspend fun markUserAsViewed(userId: Int, viewedAt: Long) =
        operation { local.markUserAsViewed(userId, viewedAt) }
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
        AppError.Network -> NETWORK_ERROR_VALUE
        is AppError.Http -> HTTP_ERROR_PREFIX + code
        AppError.InvalidData -> INVALID_DATA_ERROR_VALUE
        AppError.InvalidNote -> INVALID_NOTE_ERROR_VALUE
        AppError.AuthenticationRequired -> AUTHENTICATION_REQUIRED_ERROR_VALUE
        AppError.InvalidCredentials -> INVALID_CREDENTIALS_ERROR_VALUE
        AppError.Storage -> STORAGE_ERROR_VALUE
        AppError.Unknown -> UNKNOWN_ERROR_VALUE
    }

private val AppError.shouldReportAsNonFatal: Boolean
    get() = this == AppError.InvalidData || this == AppError.Storage || this == AppError.Unknown

private const val USERS_REFRESH_TRACE = "users_refresh"
private const val TRIGGER_ATTRIBUTE = "trigger"
private const val RESULT_ATTRIBUTE = "result"
private const val ERROR_TYPE_ATTRIBUTE = "error_type"
private const val USERS_RECEIVED_METRIC = "users_received"
private const val OPERATION_KEY = "operation"
private const val REFRESH_TRIGGER_KEY = "refresh_trigger"
private const val REFRESH_RESULT_KEY = "refresh_result"
private const val REFRESH_ERROR_TYPE_KEY = "refresh_error_type"
private const val SUCCESS_VALUE = "success"
private const val CANCELLED_VALUE = "cancelled"
private const val FAILURE_VALUE = "failure"
private const val NETWORK_ERROR_VALUE = "network"
private const val HTTP_ERROR_PREFIX = "http_"
private const val INVALID_DATA_ERROR_VALUE = "invalid_data"
private const val INVALID_NOTE_ERROR_VALUE = "invalid_note"
private const val AUTHENTICATION_REQUIRED_ERROR_VALUE = "authentication_required"
private const val INVALID_CREDENTIALS_ERROR_VALUE = "invalid_credentials"
private const val STORAGE_ERROR_VALUE = "storage"
private const val UNKNOWN_ERROR_VALUE = "unknown"
private const val REFRESH_STARTED_LOG_PREFIX = "users_refresh_started trigger="
private const val REFRESH_SUCCEEDED_LOG_PREFIX = "users_refresh_succeeded count="
private const val REFRESH_FAILED_LOG_PREFIX = "users_refresh_failed type="
private const val DUPLICATE_USER_IDS_ERROR = "Duplicate user ids"
private const val EMPTY_SNAPSHOT_ERROR = "Empty remote snapshot with a non-empty cache"

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
    id = id,
    firstName = firstName,
    lastName = lastName,
    age = age,
    email = email,
    phone = phone,
    username = username,
    imageUrl = imageUrl,
    role = role,
    companyName = companyName,
    department = department,
    jobTitle = jobTitle,
    street = street,
    city = city,
    state = state,
    country = country,
    isFavorite = favoriteCreatedAt != null,
    note = note,
    noteModifiedAt = noteModifiedAt,
    viewedAt = viewedAt,
)
