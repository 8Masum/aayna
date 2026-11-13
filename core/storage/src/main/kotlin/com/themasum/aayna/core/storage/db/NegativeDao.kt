package com.themasum.aayna.core.storage.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NegativeDao {
    @Query("SELECT * FROM negatives ORDER BY capturedAtEpochMs DESC")
    fun observeAll(): Flow<List<NegativeEntity>>

    @Query("SELECT * FROM negatives WHERE rollId = :rollId ORDER BY frameNumber ASC, capturedAtEpochMs ASC")
    fun observeByRoll(rollId: String): Flow<List<NegativeEntity>>

    @Query("SELECT * FROM negatives WHERE recipeId = :recipeId LIMIT 1")
    suspend fun getById(recipeId: String): NegativeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: NegativeEntity)

    @Query(
        "UPDATE negatives SET recipeJson = :recipeJson, cameraId = :cameraId, caption = :caption " +
            "WHERE recipeId = :recipeId"
    )
    suspend fun updateRecipe(recipeId: String, cameraId: String, recipeJson: String, caption: String)

    @Query("UPDATE negatives SET exportedUri = :exportedUri WHERE recipeId = :recipeId")
    suspend fun updateExportedUri(recipeId: String, exportedUri: String?)

    @Query("DELETE FROM negatives WHERE recipeId = :recipeId")
    suspend fun deleteById(recipeId: String)
}
