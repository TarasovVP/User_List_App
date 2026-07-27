package com.example.userlistapp.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = USERS_TABLE)
data class UserEntity(
    @PrimaryKey val id: Int,
    val firstName: String,
    val lastName: String,
    val age: Int,
    val email: String,
    val phone: String,
    val username: String,
    val imageUrl: String,
    val role: String,
    val companyName: String,
    val department: String,
    val jobTitle: String,
    val street: String,
    val city: String,
    val state: String,
    val country: String,
    @ColumnInfo(name = REMOTE_UPDATED_AT_COLUMN) val snapshotBatchId: Long = 0,
)

@Entity(
    tableName = FAVORITE_USERS_TABLE,
    foreignKeys = [ForeignKey(
        entity = UserEntity::class,
        parentColumns = [ID_COLUMN],
        childColumns = [USER_ID_COLUMN],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(USER_ID_COLUMN)],
)
data class FavoriteEntity(@PrimaryKey val userId: Int, val createdAt: Long)

@Entity(
    tableName = USER_NOTES_TABLE,
    foreignKeys = [ForeignKey(
        entity = UserEntity::class,
        parentColumns = [ID_COLUMN],
        childColumns = [USER_ID_COLUMN],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(USER_ID_COLUMN)],
)
data class UserNoteEntity(@PrimaryKey val userId: Int, val note: String, val modifiedAt: Long)

@Entity(
    tableName = RECENTLY_VIEWED_USERS_TABLE,
    foreignKeys = [ForeignKey(
        entity = UserEntity::class,
        parentColumns = [ID_COLUMN],
        childColumns = [USER_ID_COLUMN],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(USER_ID_COLUMN)],
)
data class RecentlyViewedEntity(@PrimaryKey val userId: Int, val viewedAt: Long)

data class UserWithLocal(
    val id: Int,
    val firstName: String,
    val lastName: String,
    val age: Int,
    val email: String,
    val phone: String,
    val username: String,
    val imageUrl: String,
    val role: String,
    val companyName: String,
    val department: String,
    val jobTitle: String,
    val street: String,
    val city: String,
    val state: String,
    val country: String,
    @ColumnInfo(name = REMOTE_UPDATED_AT_COLUMN) val snapshotBatchId: Long,
    @ColumnInfo(name = FAVORITE_CREATED_AT_COLUMN) val favoriteCreatedAt: Long?,
    val note: String?,
    val noteModifiedAt: Long?,
    @ColumnInfo(name = VIEWED_AT_COLUMN) val viewedAt: Long? = null,
)

internal const val USERS_TABLE = "users"
internal const val FAVORITE_USERS_TABLE = "favorite_users"
internal const val USER_NOTES_TABLE = "user_notes"
internal const val RECENTLY_VIEWED_USERS_TABLE = "recently_viewed_users"
internal const val ID_COLUMN = "id"
internal const val USER_ID_COLUMN = "userId"
internal const val REMOTE_UPDATED_AT_COLUMN = "remoteUpdatedAt"
internal const val FAVORITE_CREATED_AT_COLUMN = "favoriteCreatedAt"
internal const val VIEWED_AT_COLUMN = "viewedAt"
