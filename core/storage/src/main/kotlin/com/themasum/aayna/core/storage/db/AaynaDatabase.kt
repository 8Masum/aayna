package com.themasum.aayna.core.storage.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [NegativeEntity::class, FilmRollEntity::class],
    version = AaynaDatabase.DATABASE_VERSION,
    exportSchema = false
)
abstract class AaynaDatabase : RoomDatabase() {
    abstract fun negativeDao(): NegativeDao

    abstract fun filmRollDao(): FilmRollDao

    companion object {
        const val DATABASE_NAME = "aayna.db"
        const val DATABASE_VERSION = 2

        val MIGRATION_1_2_STATEMENTS: List<String> = listOf(
            "ALTER TABLE negatives ADD COLUMN mediaType TEXT NOT NULL DEFAULT 'STILL'",
            "ALTER TABLE negatives ADD COLUMN skinId TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE negatives ADD COLUMN videoDurationMs INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE negatives ADD COLUMN burstFrameCount INTEGER NOT NULL DEFAULT 1",
            "ALTER TABLE negatives ADD COLUMN mediaUri TEXT",
            "ALTER TABLE negatives ADD COLUMN isTrashed INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE negatives ADD COLUMN trashedAtEpochMs INTEGER"
        )

        val MIGRATION_1_2: Migration =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    MIGRATION_1_2_STATEMENTS.forEach(db::execSQL)
                }
            }

        @Volatile
        private var instance: AaynaDatabase? = null

        fun getInstance(context: Context): AaynaDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AaynaDatabase::class.java,
                DATABASE_NAME
            ).addMigrations(MIGRATION_1_2).build().also { db ->
                instance = db
            }
        }
    }
}
