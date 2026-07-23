package com.example.userlistapp.data.local

import kotlinx.coroutines.flow.Flow

interface UserLocalDataSource {
    fun observeUsers(): Flow<List<UserWithLocal>>
    fun observeUser(userId: Int): Flow<UserWithLocal?>
    suspend fun replaceRemoteSnapshot(users: List<UserEntity>)
    suspend fun setFavorite(userId: Int, favorite: Boolean)
    suspend fun saveNote(userId: Int, note: String)
    suspend fun deleteNote(userId: Int)
}
