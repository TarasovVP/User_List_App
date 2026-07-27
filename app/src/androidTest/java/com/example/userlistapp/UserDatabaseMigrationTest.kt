package com.example.userlistapp

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.userlistapp.data.local.UserDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserDatabaseMigrationTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        UserDatabase::class.java,
    )

    @Test
    fun migration1To2PreservesUsersAndFavoritesAndAddsNotes() {
        migrationHelper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL("INSERT INTO users VALUES (1,'Ada','Lovelace',36,'e','p','u','','user','C','D','T','S','City','State','Country',1)")
            execSQL("INSERT INTO favorite_users VALUES (1,2)")
            close()
        }

        val database = migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE,
            2,
            true,
            UserDatabase.MIGRATION_1_2,
        )

        database.query("SELECT firstName FROM users").use {
            it.moveToFirst()
            assertEquals("Ada", it.getString(0))
        }
        database.query("SELECT userId FROM favorite_users").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
        database.execSQL("INSERT INTO user_notes VALUES (1,'note',3)")
        database.query("SELECT note FROM user_notes").use {
            it.moveToFirst()
            assertEquals("note", it.getString(0))
        }
        database.close()
    }

    @Test
    fun migration2To3PreservesDataAndAddsRecentlyViewed() {
        migrationHelper.createDatabase(TEST_DATABASE, 2).apply {
            execSQL("INSERT INTO users VALUES (1,'Ada','Lovelace',36,'e','p','u','','user','C','D','T','S','City','State','Country',1)")
            execSQL("INSERT INTO favorite_users VALUES (1,2)")
            execSQL("INSERT INTO user_notes VALUES (1,'note',3)")
            close()
        }

        val database = migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE,
            3,
            true,
            UserDatabase.MIGRATION_2_3,
        )

        database.query("SELECT firstName FROM users").use {
            it.moveToFirst()
            assertEquals("Ada", it.getString(0))
        }
        database.query("SELECT userId FROM favorite_users").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
        database.query("SELECT note FROM user_notes").use {
            it.moveToFirst()
            assertEquals("note", it.getString(0))
        }

        database.execSQL("INSERT INTO recently_viewed_users VALUES (1, 123456789)")
        database.query("SELECT viewedAt FROM recently_viewed_users WHERE userId = 1").use {
            it.moveToFirst()
            assertEquals(123456789L, it.getLong(0))
        }
        database.close()
    }

    @Test
    fun migrateAll() {
        migrationHelper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL("INSERT INTO users VALUES (1,'Ada','Lovelace',36,'e','p','u','','user','C','D','T','S','City','State','Country',1)")
            execSQL("INSERT INTO favorite_users VALUES (1,2)")
            close()
        }

        val appDb = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            UserDatabase::class.java,
            TEST_DATABASE
        ).addMigrations(UserDatabase.MIGRATION_1_2, UserDatabase.MIGRATION_2_3).build()

        val db = appDb.openHelper.writableDatabase

        db.query("SELECT firstName FROM users WHERE id = 1").use {
            it.moveToFirst()
            assertEquals("Ada", it.getString(0))
        }

        db.query("SELECT userId FROM favorite_users").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }

        db.execSQL("INSERT INTO user_notes VALUES (1, 'all_note', 100)")
        db.query("SELECT note FROM user_notes WHERE userId = 1").use {
            it.moveToFirst()
            assertEquals("all_note", it.getString(0))
        }

        db.execSQL("INSERT INTO recently_viewed_users VALUES (1, 999)")
        db.query("SELECT viewedAt FROM recently_viewed_users WHERE userId = 1").use {
            it.moveToFirst()
            assertEquals(999L, it.getLong(0))
        }

        appDb.close()
    }

    private companion object {
        const val TEST_DATABASE = "migration-test.db"
    }
}
