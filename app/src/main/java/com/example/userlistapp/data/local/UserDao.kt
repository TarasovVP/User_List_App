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

@Dao
interface UserDao {
    @Query(SELECT_WITH_LOCAL)
    fun observeUsers(): Flow<List<UserWithLocal>>

    @Query("$SELECT_WITH_LOCAL WHERE users.id = :userId")
    fun observeUser(userId: Int): Flow<UserWithLocal?>

    @Upsert suspend fun upsertUsers(users: List<UserEntity>)
    @Upsert suspend fun upsertFavorite(favorite: FavoriteEntity)
    @Upsert suspend fun upsertNote(note: UserNoteEntity)

    @Query("DELETE FROM favorite_users WHERE userId = :userId") suspend fun deleteFavorite(userId: Int)
    @Query("DELETE FROM user_notes WHERE userId = :userId") suspend fun deleteNote(userId: Int)

    @Query("SELECT MAX(remoteUpdatedAt) FROM users")
    suspend fun latestSnapshotBatchId(): Long?

    @Query("""DELETE FROM users WHERE remoteUpdatedAt != :snapshotBatchId
        AND id NOT IN (SELECT userId FROM favorite_users)
        AND id NOT IN (SELECT userId FROM user_notes)""")
    suspend fun deleteStale(snapshotBatchId: Long)
}
