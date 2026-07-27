package com.example.userlistapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        UserEntity::class,
        FavoriteEntity::class,
        UserNoteEntity::class,
        RecentlyViewedEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class UserDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(CREATE_USER_NOTES_TABLE)
                db.execSQL(CREATE_USER_NOTES_INDEX)
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(CREATE_RECENTLY_VIEWED_USERS_TABLE)
                db.execSQL(CREATE_RECENTLY_VIEWED_USERS_INDEX)
            }
        }
    }
}

private const val CREATE_USER_NOTES_TABLE =
    """CREATE TABLE IF NOT EXISTS `user_notes` (`userId` INTEGER NOT NULL, `note` TEXT NOT NULL, `modifiedAt` INTEGER NOT NULL, PRIMARY KEY(`userId`), FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
private const val CREATE_USER_NOTES_INDEX =
    "CREATE INDEX IF NOT EXISTS `index_user_notes_userId` ON `user_notes` (`userId`)"
private const val CREATE_RECENTLY_VIEWED_USERS_TABLE =
    """CREATE TABLE IF NOT EXISTS `recently_viewed_users` (`userId` INTEGER NOT NULL, `viewedAt` INTEGER NOT NULL, PRIMARY KEY(`userId`), FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
private const val CREATE_RECENTLY_VIEWED_USERS_INDEX =
    "CREATE INDEX IF NOT EXISTS `index_recently_viewed_users_userId` ON `recently_viewed_users` (`userId`)"
