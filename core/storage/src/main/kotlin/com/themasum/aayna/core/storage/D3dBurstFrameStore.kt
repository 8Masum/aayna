package com.themasum.aayna.core.storage

import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Canonical disk persistence for D3D burst frames (`filesDir/d3d_frames/<recipeId>/frame_N.jpg`),
 * matching the directory and file naming convention expected by gallery D3D playback/export.
 */
object D3dBurstFrameStore {
    const val D3D_FRAMES_DIR = "d3d_frames"
    const val DEFAULT_MAX_EDGE_PX = 1440
    const val DEFAULT_JPEG_QUALITY = 92

    fun sanitizeRecipeId(recipeId: String): String = recipeId.replace(Regex("[^A-Za-z0-9_-]+"), "_")

    fun framesDirFor(filesDir: File, recipeId: String): File =
        File(File(filesDir, D3D_FRAMES_DIR), sanitizeRecipeId(recipeId))

    fun frameFileName(index: Int): String = "frame_$index.jpg"

    fun frameFileFor(filesDir: File, recipeId: String, index: Int): File =
        File(framesDirFor(filesDir, recipeId), frameFileName(index))

    fun computeScaledDimensions(width: Int, height: Int, maxEdgePx: Int = DEFAULT_MAX_EDGE_PX): Pair<Int, Int> {
        if (width <= 0 || height <= 0 || maxEdgePx <= 0) {
            return width.coerceAtLeast(1) to height.coerceAtLeast(1)
        }
        val longest = max(width, height)
        if (longest <= maxEdgePx) {
            return width to height
        }
        val scale = maxEdgePx.toDouble() / longest.toDouble()
        val targetW = (width * scale).roundToInt().coerceAtLeast(1)
        val targetH = (height * scale).roundToInt().coerceAtLeast(1)
        return targetW to targetH
    }

    suspend fun saveBurstFrames(
        filesDir: File,
        recipeId: String,
        frames: List<Bitmap>,
        maxEdgePx: Int = DEFAULT_MAX_EDGE_PX,
        jpegQuality: Int = DEFAULT_JPEG_QUALITY
    ): List<File> = withContext(Dispatchers.IO) {
        val validFrames = frames.filter { !it.isRecycled && it.width > 0 && it.height > 0 }
        if (validFrames.isEmpty()) return@withContext emptyList()

        val clampedQuality = jpegQuality.coerceIn(50, 100)
        val dir = framesDirFor(filesDir, recipeId).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }

        val writtenFiles = ArrayList<File>(validFrames.size)
        validFrames.forEachIndexed { index, source ->
            val (targetW, targetH) = computeScaledDimensions(source.width, source.height, maxEdgePx)
            val toWrite =
                if (targetW == source.width && targetH == source.height) {
                    source
                } else {
                    Bitmap.createScaledBitmap(source, targetW, targetH, true)
                }
            val outFile = File(dir, frameFileName(index))
            try {
                FileOutputStream(outFile).use { out ->
                    check(toWrite.compress(Bitmap.CompressFormat.JPEG, clampedQuality, out)) {
                        "Failed to compress D3D burst frame $index for $recipeId"
                    }
                }
                writtenFiles.add(outFile)
            } finally {
                if (toWrite !== source && !toWrite.isRecycled) {
                    toWrite.recycle()
                }
            }
        }
        writtenFiles
    }

    fun deleteFramesDir(filesDir: File, recipeId: String): Boolean {
        val dir = framesDirFor(filesDir, recipeId)
        return if (dir.exists()) dir.deleteRecursively() else false
    }
}
