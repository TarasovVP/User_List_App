package com.example.userlistapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [UserEntity::class, FavoriteEntity::class, UserNoteEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class UserDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `user_notes` (`userId` INTEGER NOT NULL, `note` TEXT NOT NULL, `modifiedAt` INTEGER NOT NULL, PRIMARY KEY(`userId`), FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_notes_userId` ON `user_notes` (`userId`)")
            }
        }
    }
}
