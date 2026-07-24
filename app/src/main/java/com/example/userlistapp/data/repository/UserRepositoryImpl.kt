package com.example.userlistapp.data.repository

import com.example.userlistapp.core.common.AppError
import com.example.userlistapp.core.common.AppResult
import com.example.userlistapp.core.common.DefaultDispatcher
import com.example.userlistapp.data.local.UserEntity
import com.example.userlistapp.data.local.UserLocalDataSource
import com.example.userlistapp.data.local.UserWithLocal
import com.example.userlistapp.data.remote.UserDto
import com.example.userlistapp.data.remote.UserRemoteDataSource
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
) : UserRepository {
    override fun observeUsers(): Flow<List<User>> =
        local.observeUsers().map { list -> list.map(UserWithLocal::toDomain) }

    override fun observeUser(userId: Int): Flow<User?> =
        local.observeUser(userId).map { it?.toDomain() }

    override suspend fun refreshUsers(): AppResult<Unit> = operation {
        val remoteUsers = remote.getUsers()
        val entities = withContext(mappingDispatcher) { remoteUsers.map(UserDto::toEntity) }
        require(entities.map { it.id }.distinct().size == entities.size) { "Duplicate user ids" }
        local.replaceRemoteSnapshot(entities)
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
