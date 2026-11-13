package com.themasum.aayna.core.storage.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FilmRollDao {
    @Query("SELECT * FROM film_rolls ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<FilmRollEntity>>

    @Query("SELECT * FROM film_rolls WHERE isActive = 1 ORDER BY createdAtEpochMs DESC LIMIT 1")
    fun observeActiveRoll(): Flow<FilmRollEntity?>

    @Query("SELECT * FROM film_rolls WHERE isActive = 1 ORDER BY createdAtEpochMs DESC LIMIT 1")
    suspend fun getActiveRoll(): FilmRollEntity?

    @Query("SELECT * FROM film_rolls WHERE rollId = :rollId LIMIT 1")
    suspend fun getById(rollId: String): FilmRollEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(roll: FilmRollEntity)

    @Query("UPDATE film_rolls SET isActive = 0 WHERE isActive = 1")
    suspend fun deactivateAll()

    @Query("DELETE FROM film_rolls WHERE rollId = :rollId")
    suspend fun deleteById(rollId: String)
}
