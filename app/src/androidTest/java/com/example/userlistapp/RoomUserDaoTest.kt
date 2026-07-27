package com.example.userlistapp

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.userlistapp.core.common.EMPTY
import com.example.userlistapp.data.local.RoomUserLocalDataSource
import com.example.userlistapp.data.local.NoteCipher
import com.example.userlistapp.data.local.TinkNoteCipher
import com.example.userlistapp.data.local.UserDatabase
import com.example.userlistapp.data.local.UserEntity
import com.example.userlistapp.data.local.UserNoteEntity
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomUserDaoTest {
    private lateinit var db: UserDatabase
    private lateinit var noteCipher: NoteCipher
    private val dao get() = db.userDao()

    @Before
    fun setup() {
        AeadConfig.register()
        noteCipher = TinkNoteCipher(
            KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"))
                .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        )
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            UserDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun close() = db.close()

    @Test
    fun usersFavoritesAndNotesAreObservedUpdatedAndDeleted() = runTest {
        val local = RoomUserLocalDataSource(db, dao, noteCipher)
        local.replaceRemoteSnapshot(listOf(entity(1, "Ada")))
        assertEquals("Ada", dao.observeUsers().first().single().firstName)
        local.replaceRemoteSnapshot(listOf(entity(1, "Grace")))
        assertEquals("Grace", dao.observeUsers().first().single().firstName)
        local.setFavorite(1, true)
        local.saveNote(1, "first")
        val firstNote = local.observeUser(1).first()
        assertEquals("first", firstNote?.note)
        assertTrue(firstNote?.noteModifiedAt != null && firstNote.noteModifiedAt > 0)
        local.saveNote(1, "updated")
        assertEquals("updated", local.observeUser(1).first()?.note)
        local.setFavorite(1, false)
        local.deleteNote(1)
        val row = dao.observeUser(1).first()!!
        assertNull(row.favoriteCreatedAt)
        assertNull(row.note)
    }

    @Test
    fun notePayloadIsEncryptedAtRest() = runTest {
        val local = RoomUserLocalDataSource(db, dao, noteCipher)
        local.replaceRemoteSnapshot(listOf(entity(1, "Ada")))

        local.saveNote(1, "confidential note")

        val stored = requireNotNull(dao.notePayload(1))
        assertTrue(stored.startsWith(TinkNoteCipher.FORMAT_PREFIX))
        assertTrue("confidential note" !in stored)
        assertEquals("confidential note", local.observeUser(1).first()?.note)
    }

    @Test
    fun plaintextMigrationIsIdempotentAndPreservesTheNote() = runTest {
        val local = RoomUserLocalDataSource(db, dao, noteCipher)
        local.replaceRemoteSnapshot(listOf(entity(1, "Ada")))
        dao.upsertNote(UserNoteEntity(1, "legacy plaintext", 123))

        assertEquals("legacy plaintext", local.observeUser(1).first()?.note)
        val encrypted = requireNotNull(dao.notePayload(1))
        assertTrue(encrypted.startsWith(TinkNoteCipher.FORMAT_PREFIX))
        assertEquals("legacy plaintext", local.observeUser(1).first()?.note)
        assertEquals(encrypted, dao.notePayload(1))
    }

    @Test
    fun refreshPreservesStaleUserWithLocalInformation() = runTest {
        val local = RoomUserLocalDataSource(db, dao, noteCipher)
        local.replaceRemoteSnapshot(
            listOf(
                entity(1, "Ada"),
                entity(2, "Grace"),
                entity(3, "Linus")
            )
        )
        local.setFavorite(1, true)
        local.saveNote(2, "Keep")
        local.replaceRemoteSnapshot(listOf(entity(3, "Updated")))
        assertEquals(listOf(1, 2, 3), dao.observeUsers().first().map { it.id }.sorted())
        assertEquals("Updated", dao.observeUser(3).first()?.firstName)
    }

    @Test
    fun removingLastLocalInformationImmediatelyDeletesAStaleUser() = runTest {
        val local = RoomUserLocalDataSource(db, dao, noteCipher)
        local.replaceRemoteSnapshot(
            listOf(
                entity(1, "Favorite"),
                entity(2, "Noted"),
                entity(3, "Current"),
            )
        )
        local.setFavorite(1, true)
        local.saveNote(2, "Keep")
        local.replaceRemoteSnapshot(listOf(entity(3, "Current")))

        local.setFavorite(1, false)
        local.deleteNote(2)

        assertNull(dao.observeUser(1).first())
        assertNull(dao.observeUser(2).first())
        assertEquals(listOf(3), dao.observeUsers().first().map { it.id })
    }

    @Test
    fun removingLastLocalInformationDeletesAStaleUserAfterAnEmptySnapshot() = runTest {
        val local = RoomUserLocalDataSource(db, dao, noteCipher)
        local.replaceRemoteSnapshot(listOf(entity(1, "Favorite")))
        local.setFavorite(1, true)
        local.replaceRemoteSnapshot(emptyList())

        local.setFavorite(1, false)

        assertNull(dao.observeUser(1).first())
    }

    @Test
    fun removingLocalInformationDeletesAStaleUserCreatedBeforeSentinelSnapshots() = runTest {
        val local = RoomUserLocalDataSource(db, dao, noteCipher)
        dao.upsertUsers(
            listOf(
                entity(1, "Legacy stale").copy(snapshotBatchId = 50),
                entity(2, "Latest").copy(snapshotBatchId = 100),
            )
        )
        local.setFavorite(1, true)

        local.setFavorite(1, false)

        assertNull(dao.observeUser(1).first())
        assertEquals(listOf(2), dao.observeUsers().first().map { it.id })
    }

    @Test
    fun emptyRefreshRemovesRemoteOnlyUsersAndPreservesLocalInformation() = runTest {
        val local = RoomUserLocalDataSource(db, dao, noteCipher)
        local.replaceRemoteSnapshot(
            listOf(
                entity(1, "Favorite"),
                entity(2, "Noted"),
                entity(3, "Remote only")
            )
        )
        local.setFavorite(1, true)
        local.saveNote(2, "Keep")

        local.replaceRemoteSnapshot(emptyList())

        assertEquals(listOf(1, 2), dao.observeUsers().first().map { it.id }.sorted())
        assertTrue(dao.observeUser(1).first()?.favoriteCreatedAt != null)
        assertEquals("Keep", local.observeUser(2).first()?.note)
        assertNull(dao.observeUser(3).first())
    }

    @Test
    fun largeSnapshotRefreshDoesNotDependOnSQLiteHostParameterLimit() = runTest {
        val local = RoomUserLocalDataSource(db, dao, noteCipher)
        local.replaceRemoteSnapshot(List(1_200) { index -> entity(index + 1, "User $index") })
        assertEquals(1_200, dao.observeUsers().first().size)

        local.replaceRemoteSnapshot(listOf(entity(1, "Only remaining user")))
        assertEquals(listOf(1), dao.observeUsers().first().map { it.id })
    }

    @Test
    fun viewedAtIsStoredAndObserved() = runTest {
        val local = RoomUserLocalDataSource(db, dao, noteCipher)
        local.replaceRemoteSnapshot(listOf(entity(1, "Ada")))
        local.markUserAsViewed(1, 1000L)
        assertEquals(1000L, dao.observeUser(1).first()?.viewedAt)
    }

    @Test
    fun viewedUserAbsentFromBackendSnapshotIsRetained() = runTest {
        val local = RoomUserLocalDataSource(db, dao, noteCipher)
        local.replaceRemoteSnapshot(listOf(entity(1, "Viewed"), entity(2, "Remote Only")))
        local.markUserAsViewed(1, 1000L)
        local.replaceRemoteSnapshot(emptyList())
        val users = dao.observeUsers().first()
        assertEquals(listOf(1), users.map { it.id })
        assertEquals(1000L, users.single().viewedAt)
    }

    @Test
    fun unviewedRemoteOnlyStaleUserIsDeleted() = runTest {
        val local = RoomUserLocalDataSource(db, dao, noteCipher)
        local.replaceRemoteSnapshot(listOf(entity(1, "Remote Only")))
        local.replaceRemoteSnapshot(emptyList())
        assertNull(dao.observeUser(1).first())
    }

    @Test
    fun deleteStaleUserWithoutLocalDataDoesNotDeleteRecentlyViewedStaleUser() = runTest {
        val local = RoomUserLocalDataSource(db, dao, noteCipher)
        local.replaceRemoteSnapshot(listOf(entity(1, "Viewed"), entity(2, "Current")))
        local.markUserAsViewed(1, 1000L)
        local.replaceRemoteSnapshot(listOf(entity(2, "Current")))
        // User 1 is now stale
        local.deleteNote(1) // This triggers deleteStaleUserWithoutLocalData internally
        assertEquals(1, dao.observeUser(1).first()?.id)
    }
}

private fun entity(id: Int, name: String) = UserEntity(
    id,
    name,
    "User",
    30,
    "e$id",
    "p",
    "u$id",
    String.EMPTY,
    "user",
    "Company",
    "Dept",
    "Title",
    "Street",
    "City",
    "State",
    "Country",
    1
)
