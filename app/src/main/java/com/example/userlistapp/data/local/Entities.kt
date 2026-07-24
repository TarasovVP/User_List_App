package com.example.userlistapp.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "users")
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
    @ColumnInfo(name = "remoteUpdatedAt") val snapshotBatchId: Long = 0,
)

@Entity(
    tableName = "favorite_users",
    foreignKeys = [ForeignKey(
        entity = UserEntity::class,
        parentColumns = ["id"],
        childColumns = ["userId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("userId")],
)
data class FavoriteEntity(@PrimaryKey val userId: Int, val createdAt: Long)

@Entity(
    tableName = "user_notes",
    foreignKeys = [ForeignKey(
        entity = UserEntity::class,
        parentColumns = ["id"],
        childColumns = ["userId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("userId")],
)
data class UserNoteEntity(@PrimaryKey val userId: Int, val note: String, val modifiedAt: Long)

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
    @ColumnInfo(name = "remoteUpdatedAt") val snapshotBatchId: Long,
    @ColumnInfo(name = "favoriteCreatedAt") val favoriteCreatedAt: Long?,
    val note: String?,
    val noteModifiedAt: Long?,
)
