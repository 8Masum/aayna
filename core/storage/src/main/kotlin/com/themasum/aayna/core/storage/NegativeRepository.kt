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

class NegativeRepository(
    private val context: Context,
    private val negativeDao: NegativeDao,
    private val thumbnailCache: ThumbnailMemoryCache? = null
) {
    private val negativesDir: File
        get() = File(context.filesDir, NEGATIVES_DIR).apply { mkdirs() }

    private val thumbsDir: File
        get() = File(context.filesDir, THUMBS_DIR).apply { mkdirs() }

    val negativesFlow: Flow<List<NegativeEntity>> = negativeDao.observeActiveNegatives()

    val activeNegativesFlow: Flow<List<NegativeEntity>> = negativeDao.observeActiveNegatives()

    val trashedNegativesFlow: Flow<List<NegativeEntity>> = negativeDao.observeTrashedNegatives()

    fun observeActiveNegatives(): Flow<List<NegativeEntity>> = negativeDao.observeActiveNegatives()

    fun observeTrashedNegatives(): Flow<List<NegativeEntity>> = negativeDao.observeTrashedNegatives()

    fun observeNegativesForRoll(rollId: String): Flow<List<NegativeEntity>> = negativeDao.observeByRoll(rollId)

    suspend fun getNegative(recipeId: String): NegativeEntity? = negativeDao.getById(recipeId)

    suspend fun saveNegative(
        cleanBitmap: Bitmap,
        thumbnailBitmap: Bitmap,
        recipe: CaptureRecipe,
        rollId: String? = null,
        frameNumber: Int? = null,
        exportedUri: String? = null,
        mediaUri: String? = null
    ): NegativeEntity = withContext(Dispatchers.IO) {
        val safeId = sanitizeId(recipe.recipeId)
        val cleanFile = File(negativesDir, "$safeId.jpg")
        val thumbFile = File(thumbsDir, "${safeId}_thumb.jpg")

        FileOutputStream(cleanFile).use { out ->
            check(cleanBitmap.compress(Bitmap.CompressFormat.JPEG, CLEAN_JPEG_QUALITY, out)) {
                "Failed to compress clean negative bitmap for ${recipe.recipeId}"
            }
        }

        writeThumbnailFile(thumbFile, thumbnailBitmap, recipe.recipeId)

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
                caption = recipe.caption,
                mediaType = recipe.mediaType.name,
                skinId = recipe.skinId,
                videoDurationMs = recipe.videoDurationMs,
                burstFrameCount = recipe.burstFrameCount,
                mediaUri = mediaUri ?: exportedUri,
                isTrashed = false,
                trashedAtEpochMs = null
            )
        negativeDao.upsert(entity)
        cacheThumbnailCopy(thumbFile, thumbnailBitmap)
        entity
    }

    suspend fun saveMediaNegative(
        thumbnailBitmap: Bitmap,
        recipe: CaptureRecipe,
        mediaUri: String,
        rollId: String? = null,
        frameNumber: Int? = null
    ): NegativeEntity = withContext(Dispatchers.IO) {
        val safeId = sanitizeId(recipe.recipeId)
        val thumbFile = File(thumbsDir, "${safeId}_thumb.jpg")
        writeThumbnailFile(thumbFile, thumbnailBitmap, recipe.recipeId)

        val entity =
            NegativeEntity(
                recipeId = recipe.recipeId,
                cameraId = recipe.cameraId,
                capturedAtEpochMs = recipe.capturedAtEpochMs,
                cleanImagePath = "",
                thumbnailPath = thumbFile.absolutePath,
                recipeJson = AaynaJson.encodeRecipe(recipe),
                exportedUri = mediaUri,
                rollId = rollId,
                frameNumber = frameNumber,
                caption = recipe.caption,
                mediaType = recipe.mediaType.name,
                skinId = recipe.skinId,
                videoDurationMs = recipe.videoDurationMs,
                burstFrameCount = recipe.burstFrameCount,
                mediaUri = mediaUri,
                isTrashed = false,
                trashedAtEpochMs = null
            )
        negativeDao.upsert(entity)
        cacheThumbnailCopy(thumbFile, thumbnailBitmap)
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

    suspend fun updateRecipeAndThumbnail(recipe: CaptureRecipe, gradedThumbnailBitmap: Bitmap): String =
        withContext(Dispatchers.IO) {
            val safeId = sanitizeId(recipe.recipeId)
            val thumbFile = File(thumbsDir, "${safeId}_thumb.jpg")
            thumbnailCache?.invalidatePath(thumbFile.absolutePath)
            writeThumbnailFile(thumbFile, gradedThumbnailBitmap, recipe.recipeId)

            negativeDao.updateRecipeAndThumbnail(
                recipeId = recipe.recipeId,
                cameraId = recipe.cameraId,
                recipeJson = AaynaJson.encodeRecipe(recipe),
                caption = recipe.caption,
                skinId = recipe.skinId,
                thumbnailPath = thumbFile.absolutePath
            )
            cacheThumbnailCopy(thumbFile, gradedThumbnailBitmap)
            thumbFile.absolutePath
        }

    suspend fun updateExportedUri(recipeId: String, exportedUri: String?) {
        negativeDao.updateExportedUri(recipeId = recipeId, exportedUri = exportedUri)
    }

    suspend fun softDelete(recipeId: String, trashedAtEpochMs: Long = System.currentTimeMillis()) {
        negativeDao.softDelete(recipeId = recipeId, trashedAtEpochMs = trashedAtEpochMs)
    }

    suspend fun restoreFromTrash(recipeId: String) {
        negativeDao.restoreFromTrash(recipeId = recipeId)
    }

    suspend fun purgeExpiredTrash(
        retentionMs: Long = DEFAULT_TRASH_RETENTION_MS,
        nowEpochMs: Long = System.currentTimeMillis()
    ): Int = withContext(Dispatchers.IO) {
        val cutoff = nowEpochMs - retentionMs.coerceAtLeast(0L)
        val expired = negativeDao.getExpiredTrashed(cutoff)
        expired.forEach { entity ->
            deleteFilesForEntity(entity)
        }
        negativeDao.purgeExpiredTrash(cutoff)
    }

    suspend fun deleteNegative(recipeId: String) {
        withContext(Dispatchers.IO) {
            val existing = negativeDao.getById(recipeId)
            if (existing != null) {
                deleteFilesForEntity(existing)
            }
            negativeDao.deleteById(recipeId)
        }
    }

    private fun cacheThumbnailCopy(thumbFile: File, source: Bitmap) {
        val cache = thumbnailCache ?: return
        if (source.isRecycled) return
        val targetConfig =
            if (source.config == null || source.config == Bitmap.Config.HARDWARE) {
                Bitmap.Config.ARGB_8888
            } else {
                source.config!!
            }
        val copy = source.copy(targetConfig, false) ?: return
        cache.put(thumbFile, copy)
    }

    private fun writeThumbnailFile(thumbFile: File, bitmap: Bitmap, recipeId: String) {
        FileOutputStream(thumbFile).use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, THUMB_JPEG_QUALITY, out)) {
                "Failed to compress thumbnail bitmap for $recipeId"
            }
        }
    }

    private fun deleteFilesForEntity(entity: NegativeEntity) {
        if (entity.cleanImagePath.isNotBlank()) {
            File(entity.cleanImagePath).delete()
        }
        if (entity.thumbnailPath.isNotBlank()) {
            thumbnailCache?.invalidatePath(entity.thumbnailPath)
            File(entity.thumbnailPath).delete()
        }
    }

    private fun sanitizeId(recipeId: String): String = recipeId.replace(Regex("[^A-Za-z0-9_-]+"), "_")

    companion object {
        private const val NEGATIVES_DIR = "negatives"
        private const val THUMBS_DIR = "thumbs"
        private const val CLEAN_JPEG_QUALITY = 95
        private const val THUMB_JPEG_QUALITY = 88
        const val DEFAULT_TRASH_RETENTION_MS: Long = 30L * 24L * 60L * 60L * 1000L
    }
}
