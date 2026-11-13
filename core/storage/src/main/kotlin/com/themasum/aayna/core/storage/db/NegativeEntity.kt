package com.themasum.aayna.core.storage.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.themasum.aayna.core.model.AaynaJson
import com.themasum.aayna.core.model.CaptureRecipe

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
    val caption: String = ""
) {
    fun toCaptureRecipe(): CaptureRecipe = AaynaJson.decodeRecipe(recipeJson)
}
