package com.themasum.aayna.core.storage

import android.content.Context
import android.graphics.Bitmap
import com.themasum.aayna.core.model.AaynaJson
import com.themasum.aayna.core.model.CaptureRecipe
import com.themasum.aayna.core.storage.db.NegativeDao
import com.themasum.aayna.core.storage.db.NegativeEntity
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class NegativeRepository(private val context: Context, private val negativeDao: NegativeDao) {
    private val negativesDir: File
        get() = File(context.filesDir, NEGATIVES_DIR).apply { mkdirs() }

    private val thumbsDir: File
        get() = File(context.filesDir, THUMBS_DIR).apply { mkdirs() }

    val negativesFlow: Flow<List<NegativeEntity>> = negativeDao.observeAll()

    fun observeNegativesForRoll(rollId: String): Flow<List<NegativeEntity>> = negativeDao.observeByRoll(rollId)

    suspend fun getNegative(recipeId: String): NegativeEntity? = negativeDao.getById(recipeId)

    suspend fun saveNegative(
        cleanBitmap: Bitmap,
        thumbnailBitmap: Bitmap,
        recipe: CaptureRecipe,
        rollId: String? = null,
        frameNumber: Int? = null,
        exportedUri: String? = null
    ): NegativeEntity = withContext(Dispatchers.IO) {
        val safeId = recipe.recipeId.replace(Regex("[^A-Za-z0-9_-]+"), "_")
        val cleanFile = File(negativesDir, "$safeId.jpg")
        val thumbFile = File(thumbsDir, "${safeId}_thumb.jpg")

        FileOutputStream(cleanFile).use { out ->
            check(cleanBitmap.compress(Bitmap.CompressFormat.JPEG, CLEAN_JPEG_QUALITY, out)) {
                "Failed to compress clean negative bitmap for ${recipe.recipeId}"
            }
        }

        FileOutputStream(thumbFile).use { out ->
            check(thumbnailBitmap.compress(Bitmap.CompressFormat.JPEG, THUMB_JPEG_QUALITY, out)) {
                "Failed to compress thumbnail bitmap for ${recipe.recipeId}"
            }
        }

        val entity =
            NegativeEntity(
                recipeId = recipe.recipeId,
                cameraId = recipe.cameraId,
                capturedAtEpochMs = recipe.capturedAtEpochMs,
                cleanImagePath = cleanFile.absolutePath,
                thumbnailPath = thumbFile.absolutePath,
                recipeJson = AaynaJson.encodeRecipe(recipe),
                exportedUri = exportedUri,
                rollId = rollId,
                frameNumber = frameNumber,
                caption = recipe.caption
            )
        negativeDao.upsert(entity)
        entity
    }

    suspend fun updateRecipe(recipe: CaptureRecipe) {
        negativeDao.updateRecipe(
            recipeId = recipe.recipeId,
            cameraId = recipe.cameraId,
            recipeJson = AaynaJson.encodeRecipe(recipe),
            caption = recipe.caption
        )
    }

    suspend fun updateExportedUri(recipeId: String, exportedUri: String?) {
        negativeDao.updateExportedUri(recipeId = recipeId, exportedUri = exportedUri)
    }

    suspend fun deleteNegative(recipeId: String) {
        withContext(Dispatchers.IO) {
            val existing = negativeDao.getById(recipeId)
            if (existing != null) {
                File(existing.cleanImagePath).delete()
                File(existing.thumbnailPath).delete()
            }
            negativeDao.deleteById(recipeId)
        }
    }

    companion object {
        private const val NEGATIVES_DIR = "negatives"
        private const val THUMBS_DIR = "thumbs"
        private const val CLEAN_JPEG_QUALITY = 95
        private const val THUMB_JPEG_QUALITY = 88
    }
}
