package com.example.userlistapp.data.local

import androidx.room.withTransaction

class RoomUserLocalDataSource(
    private val database: UserDatabase,
    private val dao: UserDao,
) : UserLocalDataSource {
    override fun observeUsers() = dao.observeUsers()
    override fun observeUser(userId: Int) = dao.observeUser(userId)

    override suspend fun replaceRemoteSnapshot(users: List<UserEntity>) {
        database.withTransaction {
            val candidateBatchId = System.currentTimeMillis()
            val latestBatchId = dao.latestSnapshotBatchId()
            val snapshotBatchId = nextSnapshotBatchId(candidateBatchId, latestBatchId)
            dao.upsertUsers(users.map { it.copy(snapshotBatchId = snapshotBatchId) })
            dao.deleteStale(snapshotBatchId)
        }
    }

    override suspend fun setFavorite(userId: Int, favorite: Boolean) {
        if (favorite) {
            dao.upsertFavorite(FavoriteEntity(userId, System.currentTimeMillis()))
        } else {
            dao.deleteFavorite(userId)
        }
    }

    override suspend fun saveNote(userId: Int, note: String) =
        dao.upsertNote(UserNoteEntity(userId, note, System.currentTimeMillis()))

    override suspend fun deleteNote(userId: Int) = dao.deleteNote(userId)
}
