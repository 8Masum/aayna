package com.themasum.aayna.core.storage.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.themasum.aayna.core.model.AaynaJson
import com.themasum.aayna.core.model.CaptureRecipe
import com.themasum.aayna.core.model.MediaType

@Entity(tableName = "negatives")
data class NegativeEntity(
    @PrimaryKey
    val recipeId: String,
    val cameraId: String,
    val capturedAtEpochMs: Long,
    val cleanImagePath: String,
    val thumbnailPath: String,
    val recipeJson: String,
    val exportedUri: String? = null,
    val rollId: String? = null,
    val frameNumber: Int? = null,
    val caption: String = "",
    val mediaType: String = MediaType.STILL.name,
    val skinId: String = "",
    val videoDurationMs: Long = 0L,
    val burstFrameCount: Int = 1,
    val mediaUri: String? = null,
    val isTrashed: Boolean = false,
    val trashedAtEpochMs: Long? = null
) {
    fun toCaptureRecipe(): CaptureRecipe = AaynaJson.decodeRecipe(recipeJson)

    fun resolvedMediaType(): MediaType = MediaType.entries.firstOrNull { it.name == mediaType } ?: MediaType.STILL
}
