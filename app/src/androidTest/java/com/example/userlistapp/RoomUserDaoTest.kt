package com.example.userlistapp

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.userlistapp.data.local.RoomUserLocalDataSource
import com.example.userlistapp.data.local.UserDatabase
import com.example.userlistapp.data.local.UserEntity
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
    private val dao get() = db.userDao()

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            UserDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun close() = db.close()

    @Test
    fun usersFavoritesAndNotesAreObservedUpdatedAndDeleted() = runTest {
        val local = RoomUserLocalDataSource(db, dao)
        local.replaceRemoteSnapshot(listOf(entity(1, "Ada")))
        assertEquals("Ada", dao.observeUsers().first().single().firstName)
        local.replaceRemoteSnapshot(listOf(entity(1, "Grace")))
        assertEquals("Grace", dao.observeUsers().first().single().firstName)
        local.setFavorite(1, true)
        local.saveNote(1, "first")
        val firstNote = dao.observeUser(1).first()
        assertEquals("first", firstNote?.note)
        assertTrue(firstNote?.noteModifiedAt != null && firstNote.noteModifiedAt > 0)
        local.saveNote(1, "updated")
        assertEquals("updated", dao.observeUser(1).first()?.note)
        local.setFavorite(1, false)
        local.deleteNote(1)
        val row = dao.observeUser(1).first()!!
        assertNull(row.favoriteCreatedAt)
        assertNull(row.note)
    }

    @Test
    fun refreshPreservesStaleUserWithLocalInformation() = runTest {
        val local = RoomUserLocalDataSource(db, dao)
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
    fun emptyRefreshRemovesRemoteOnlyUsersAndPreservesLocalInformation() = runTest {
        val local = RoomUserLocalDataSource(db, dao)
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
        assertEquals("Keep", dao.observeUser(2).first()?.note)
        assertNull(dao.observeUser(3).first())
    }

    @Test
    fun largeSnapshotRefreshDoesNotDependOnSQLiteHostParameterLimit() = runTest {
        val local = RoomUserLocalDataSource(db, dao)
        local.replaceRemoteSnapshot(List(1_200) { index -> entity(index + 1, "User $index") })
        assertEquals(1_200, dao.observeUsers().first().size)

        local.replaceRemoteSnapshot(listOf(entity(1, "Only remaining user")))
        assertEquals(listOf(1), dao.observeUsers().first().map { it.id })
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
    "",
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
