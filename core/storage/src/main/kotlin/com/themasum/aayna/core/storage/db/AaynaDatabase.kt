package com.themasum.aayna.core.storage.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [NegativeEntity::class, FilmRollEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AaynaDatabase : RoomDatabase() {
    abstract fun negativeDao(): NegativeDao

    abstract fun filmRollDao(): FilmRollDao

    companion object {
        private const val DATABASE_NAME = "aayna.db"

        @Volatile
        private var instance: AaynaDatabase? = null

        fun getInstance(context: Context): AaynaDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AaynaDatabase::class.java,
                DATABASE_NAME
            ).build().also { db ->
                instance = db
            }
        }
    }
}
