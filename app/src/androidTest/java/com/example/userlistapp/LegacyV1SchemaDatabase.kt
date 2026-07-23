package com.example.userlistapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/** Test-only declaration that keeps the exported version-1 schema reproducible. */
@Database(entities = [UserEntity::class, FavoriteEntity::class], version = 1, exportSchema = true)
abstract class LegacyV1SchemaDatabase : RoomDatabase()
