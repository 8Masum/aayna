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

    @Query("SELECT * FROM negatives WHERE isTrashed = 0 ORDER BY capturedAtEpochMs DESC")
    fun observeActiveNegatives(): Flow<List<NegativeEntity>>

    @Query("SELECT * FROM negatives WHERE isTrashed = 1 ORDER BY trashedAtEpochMs DESC, capturedAtEpochMs DESC")
    fun observeTrashedNegatives(): Flow<List<NegativeEntity>>

    @Query(
        "SELECT * FROM negatives WHERE rollId = :rollId AND isTrashed = 0 " +
            "ORDER BY frameNumber ASC, capturedAtEpochMs ASC"
    )
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

    @Query(
        "UPDATE negatives SET recipeJson = :recipeJson, cameraId = :cameraId, caption = :caption, " +
            "skinId = :skinId, thumbnailPath = :thumbnailPath WHERE recipeId = :recipeId"
    )
    suspend fun updateRecipeAndThumbnail(
        recipeId: String,
        cameraId: String,
        recipeJson: String,
        caption: String,
        skinId: String,
        thumbnailPath: String
    )

    @Query("UPDATE negatives SET exportedUri = :exportedUri WHERE recipeId = :recipeId")
    suspend fun updateExportedUri(recipeId: String, exportedUri: String?)

    @Query("UPDATE negatives SET isTrashed = 1, trashedAtEpochMs = :trashedAtEpochMs WHERE recipeId = :recipeId")
    suspend fun softDelete(recipeId: String, trashedAtEpochMs: Long)

    @Query("UPDATE negatives SET isTrashed = 0, trashedAtEpochMs = NULL WHERE recipeId = :recipeId")
    suspend fun restoreFromTrash(recipeId: String)

    @Query(
        "SELECT * FROM negatives WHERE isTrashed = 1 " +
            "AND trashedAtEpochMs IS NOT NULL AND trashedAtEpochMs <= :cutoffEpochMs"
    )
    suspend fun getExpiredTrashed(cutoffEpochMs: Long): List<NegativeEntity>

    @Query(
        "DELETE FROM negatives WHERE isTrashed = 1 " +
            "AND trashedAtEpochMs IS NOT NULL AND trashedAtEpochMs <= :cutoffEpochMs"
    )
    suspend fun purgeExpiredTrash(cutoffEpochMs: Long): Int

    @Query("DELETE FROM negatives WHERE recipeId = :recipeId")
    suspend fun deleteById(recipeId: String)
}
