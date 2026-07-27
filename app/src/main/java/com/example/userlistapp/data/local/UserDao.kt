package com.example.userlistapp.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

private const val SELECT_WITH_LOCAL = """
    SELECT users.*, favorite_users.createdAt AS favoriteCreatedAt,
        user_notes.note AS note, user_notes.modifiedAt AS noteModifiedAt,
        recently_viewed_users.viewedAt AS viewedAt
    FROM users
    LEFT JOIN favorite_users ON users.id = favorite_users.userId
    LEFT JOIN user_notes ON users.id = user_notes.userId
    LEFT JOIN recently_viewed_users ON users.id = recently_viewed_users.userId
"""
private const val SELECT_USER_BY_ID = "$SELECT_WITH_LOCAL WHERE users.id = :userId"
private const val DELETE_FAVORITE =
    "DELETE FROM favorite_users WHERE userId = :userId"
private const val DELETE_NOTE =
    "DELETE FROM user_notes WHERE userId = :userId"
private const val REPLACE_NOTE_PAYLOAD =
    "UPDATE user_notes SET note = :newPayload WHERE userId = :userId AND note = :expectedPayload"
private const val SELECT_LATEST_SNAPSHOT =
    "SELECT MAX(remoteUpdatedAt) FROM users"
private const val COUNT_USERS = "SELECT COUNT(*) FROM users"
private const val SELECT_NOTE_PAYLOAD =
    "SELECT note FROM user_notes WHERE userId = :userId"
private const val DELETE_STALE_USERS = """
    DELETE FROM users WHERE remoteUpdatedAt != :snapshotBatchId
    AND id NOT IN (SELECT userId FROM favorite_users)
    AND id NOT IN (SELECT userId FROM user_notes)
    AND id NOT IN (SELECT userId FROM recently_viewed_users)
"""
private const val MARK_USERS_STALE =
    "UPDATE users SET remoteUpdatedAt = :staleBatchId"
private const val DELETE_STALE_USER_WITHOUT_LOCAL_DATA = """
    DELETE FROM users WHERE id = :userId
    AND (
        remoteUpdatedAt = :staleBatchId
        OR remoteUpdatedAt != (SELECT MAX(remoteUpdatedAt) FROM users)
    )
    AND id NOT IN (SELECT userId FROM favorite_users)
    AND id NOT IN (SELECT userId FROM user_notes)
    AND id NOT IN (SELECT userId FROM recently_viewed_users)
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

    @Upsert
    suspend fun upsertRecentlyViewed(recentlyViewed: RecentlyViewedEntity)

    @Query(REPLACE_NOTE_PAYLOAD)
    suspend fun replaceNotePayload(
        userId: Int,
        expectedPayload: String,
        newPayload: String,
    ): Int

    @Query(DELETE_FAVORITE)
    suspend fun deleteFavorite(userId: Int)

    @Query(DELETE_NOTE)
    suspend fun deleteNote(userId: Int)

    @Query(SELECT_LATEST_SNAPSHOT)
    suspend fun latestSnapshotBatchId(): Long?

    @Query(COUNT_USERS)
    suspend fun countUsers(): Int

    @Query(SELECT_NOTE_PAYLOAD)
    suspend fun notePayload(userId: Int): String?

    @Query(DELETE_STALE_USERS)
    suspend fun deleteStale(snapshotBatchId: Long)

    @Query(MARK_USERS_STALE)
    suspend fun markUsersStale(staleBatchId: Long)

    @Query(DELETE_STALE_USER_WITHOUT_LOCAL_DATA)
    suspend fun deleteStaleUserWithoutLocalData(userId: Int, staleBatchId: Long)
}
