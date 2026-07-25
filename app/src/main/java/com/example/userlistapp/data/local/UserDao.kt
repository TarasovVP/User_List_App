package com.example.userlistapp.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

private const val SELECT_WITH_LOCAL = """
    SELECT users.*, favorite_users.createdAt AS favoriteCreatedAt,
        user_notes.note AS note, user_notes.modifiedAt AS noteModifiedAt
    FROM users
    LEFT JOIN favorite_users ON users.id = favorite_users.userId
    LEFT JOIN user_notes ON users.id = user_notes.userId
"""
private const val SELECT_USER_BY_ID = "$SELECT_WITH_LOCAL WHERE users.id = :userId"
private const val DELETE_FAVORITE =
    "DELETE FROM favorite_users WHERE userId = :userId"
private const val DELETE_NOTE =
    "DELETE FROM user_notes WHERE userId = :userId"
private const val SELECT_LATEST_SNAPSHOT =
    "SELECT MAX(remoteUpdatedAt) FROM users"
private const val DELETE_STALE_USERS = """
    DELETE FROM users WHERE remoteUpdatedAt != :snapshotBatchId
    AND id NOT IN (SELECT userId FROM favorite_users)
    AND id NOT IN (SELECT userId FROM user_notes)
"""

@Dao
interface UserDao {
    @Query(SELECT_WITH_LOCAL)
    fun observeUsers(): Flow<List<UserWithLocal>>

    @Query(SELECT_USER_BY_ID)
    fun observeUser(userId: Int): Flow<UserWithLocal?>

    @Upsert
    suspend fun upsertUsers(users: List<UserEntity>)
    @Upsert
    suspend fun upsertFavorite(favorite: FavoriteEntity)
    @Upsert
    suspend fun upsertNote(note: UserNoteEntity)

    @Query(DELETE_FAVORITE)
    suspend fun deleteFavorite(userId: Int)
    @Query(DELETE_NOTE)
    suspend fun deleteNote(userId: Int)

    @Query(SELECT_LATEST_SNAPSHOT)
    suspend fun latestSnapshotBatchId(): Long?

    @Query(DELETE_STALE_USERS)
    suspend fun deleteStale(snapshotBatchId: Long)
}
