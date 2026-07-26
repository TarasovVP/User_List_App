package com.example.userlistapp.data.local

import androidx.room.withTransaction

class RoomUserLocalDataSource(
    private val database: UserDatabase,
    private val dao: UserDao,
) : UserLocalDataSource {
    override fun observeUsers() = dao.observeUsers()
    override fun observeUser(userId: Int) = dao.observeUser(userId)

    override suspend fun countUsers() = dao.countUsers()

    override suspend fun replaceRemoteSnapshot(users: List<UserEntity>) {
        database.withTransaction {
            val candidateBatchId = System.currentTimeMillis()
            val latestBatchId = dao.latestSnapshotBatchId()
            val snapshotBatchId = nextSnapshotBatchId(candidateBatchId, latestBatchId)
            dao.markUsersStale(STALE_SNAPSHOT_BATCH_ID)
            dao.upsertUsers(users.map { it.copy(snapshotBatchId = snapshotBatchId) })
            dao.deleteStale(snapshotBatchId)
        }
    }

    override suspend fun setFavorite(userId: Int, favorite: Boolean) {
        if (favorite) {
            dao.upsertFavorite(FavoriteEntity(userId, System.currentTimeMillis()))
        } else {
            database.withTransaction {
                dao.deleteFavorite(userId)
                dao.deleteStaleUserWithoutLocalData(userId, STALE_SNAPSHOT_BATCH_ID)
            }
        }
    }

    override suspend fun saveNote(userId: Int, note: String) =
        dao.upsertNote(UserNoteEntity(userId, note, System.currentTimeMillis()))

    override suspend fun deleteNote(userId: Int) {
        database.withTransaction {
            dao.deleteNote(userId)
            dao.deleteStaleUserWithoutLocalData(userId, STALE_SNAPSHOT_BATCH_ID)
        }
    }
}

internal fun nextSnapshotBatchId(candidateBatchId: Long, latestBatchId: Long?): Long = when {
    latestBatchId == null || latestBatchId < candidateBatchId -> candidateBatchId
    latestBatchId < Long.MAX_VALUE -> latestBatchId + 1
    else -> Long.MAX_VALUE
}

private const val STALE_SNAPSHOT_BATCH_ID = Long.MIN_VALUE
