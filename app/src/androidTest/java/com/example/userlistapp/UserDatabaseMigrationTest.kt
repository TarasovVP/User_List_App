package com.example.userlistapp

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

    private companion object {
        const val TEST_DATABASE = "migration-test.db"
    }
}
