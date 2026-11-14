package com.themasum.aayna.feature.gallery

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.themasum.aayna.core.storage.GifWigglegramEncoder
import com.themasum.aayna.core.storage.ThumbnailMemoryCache
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GalleryExifTransform(
    val rotationDegrees: Int = 0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false
) {
    val isIdentity: Boolean
        get() = rotationDegrees == 0 && !flipHorizontal && !flipVertical
}

data class GalleryExifMetadata(
    val widthPx: Int = 0,
    val heightPx: Int = 0,
    val make: String = "",
    val model: String = "",
    val dateTimeOriginal: String = "",
    val software: String = "",
    val makerNote: String = "",
    val fileSizeBytes: Long = 0L
)

object GalleryD3dTimingMath {
    const val MIN_FRAME_DELAY_MS: Int = 80
    const val MAX_FRAME_DELAY_MS: Int = 320
    const val DEFAULT_FRAME_DELAY_MS: Int = GifWigglegramEncoder.DEFAULT_FRAME_DELAY_MS

    fun clampFrameDelayMs(delayMs: Int): Int = delayMs.coerceIn(MIN_FRAME_DELAY_MS, MAX_FRAME_DELAY_MS)

    fun computeSequenceIndices(frameCount: Int, pingPong: Boolean): List<Int> {
        if (frameCount <= 0) return emptyList()
        if (!pingPong || frameCount <= 2) {
            return (0 until frameCount).toList()
        }
        val forward = (0 until frameCount).toList()
        val backward = (frameCount - 2 downTo 1).toList()
        return forward + backward
    }

    fun computeCycleDurationMs(frameCount: Int, frameDelayMs: Int, pingPong: Boolean): Long {
        val sequence = computeSequenceIndices(frameCount, pingPong)
        if (sequence.isEmpty()) return 0L
        return sequence.size.toLong() * clampFrameDelayMs(frameDelayMs).toLong()
    }

    fun computeFrameIndexAtElapsed(elapsedMs: Long, frameCount: Int, frameDelayMs: Int, pingPong: Boolean): Int {
        val sequence = computeSequenceIndices(frameCount, pingPong)
        if (sequence.isEmpty()) return 0
        val delay = clampFrameDelayMs(frameDelayMs).toLong()
        val safeElapsed = elapsedMs.coerceAtLeast(0L)
        val step = ((safeElapsed / delay) % sequence.size.toLong()).toInt()
        return sequence[step]
    }
}

object GalleryImageDecoderMath {
    const val MAX_IMPORT_EDGE_PX: Int = 4096
    const val THUMBNAIL_MAX_EDGE_PX: Int = 480

    fun calculateInSampleSize(width: Int, height: Int, maxEdgePx: Int = MAX_IMPORT_EDGE_PX): Int {
        if (width <= 0 || height <= 0 || maxEdgePx <= 0) return 1
        var sampleSize = 1
        var longest = max(width, height)
        while (longest / 2 >= maxEdgePx) {
            longest /= 2
            sampleSize *= 2
        }
        if (longest > maxEdgePx) {
            sampleSize *= 2
        }
        return sampleSize.coerceAtLeast(1)
    }

    fun computeScaledDimensions(width: Int, height: Int, maxEdgePx: Int = MAX_IMPORT_EDGE_PX): Pair<Int, Int> {
        if (width <= 0 || height <= 0 || maxEdgePx <= 0) {
            return width.coerceAtLeast(1) to height.coerceAtLeast(1)
        }
        val longest = max(width, height)
        if (longest <= maxEdgePx) {
            return width to height
        }
        val scale = maxEdgePx.toDouble() / longest.toDouble()
        val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
        return targetWidth to targetHeight
    }

    fun exifOrientationToTransform(orientation: Int): GalleryExifTransform = when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL ->
            GalleryExifTransform(flipHorizontal = true)
        ExifInterface.ORIENTATION_ROTATE_180 ->
            GalleryExifTransform(rotationDegrees = 180)
        ExifInterface.ORIENTATION_FLIP_VERTICAL ->
            GalleryExifTransform(flipVertical = true)
        ExifInterface.ORIENTATION_TRANSPOSE ->
            GalleryExifTransform(rotationDegrees = 90, flipHorizontal = true)
        ExifInterface.ORIENTATION_ROTATE_90 ->
            GalleryExifTransform(rotationDegrees = 90)
        ExifInterface.ORIENTATION_TRANSVERSE ->
            GalleryExifTransform(rotationDegrees = 270, flipHorizontal = true)
        ExifInterface.ORIENTATION_ROTATE_270 ->
            GalleryExifTransform(rotationDegrees = 270)
        else -> GalleryExifTransform()
    }
}

object GalleryImageDecoder {
    private const val D3D_FRAMES_DIR = "d3d_frames"
    private const val D3D_FRAME_JPEG_QUALITY = 92

    suspend fun loadThumbnailCached(
        path: String,
        thumbnailCache: ThumbnailMemoryCache?,
        maxEdgePx: Int = GalleryImageDecoderMath.THUMBNAIL_MAX_EDGE_PX
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (path.isBlank()) return@withContext null
        val file = File(path)
        if (!file.exists()) return@withContext null
        val lastModified = file.lastModified()
        thumbnailCache?.get(file.absolutePath, lastModified)?.takeUnless { it.isRecycled }?.let {
            return@withContext it
        }
        val decoded = decodeFileSafely(file.absolutePath, maxEdgePx = maxEdgePx) ?: return@withContext null
        thumbnailCache?.put(file.absolutePath, lastModified, decoded)
        decoded
    }

    suspend fun readExifMetadata(path: String): GalleryExifMetadata = withContext(Dispatchers.IO) {
        if (path.isBlank()) return@withContext GalleryExifMetadata()
        val file = File(path)
        if (!file.exists()) return@withContext GalleryExifMetadata()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val exif = runCatching { ExifInterface(file.absolutePath) }.getOrNull()
        GalleryExifMetadata(
            widthPx = bounds.outWidth.coerceAtLeast(0),
            heightPx = bounds.outHeight.coerceAtLeast(0),
            make = exif?.getAttribute(ExifInterface.TAG_MAKE).orEmpty(),
            model = exif?.getAttribute(ExifInterface.TAG_MODEL).orEmpty(),
            dateTimeOriginal = exif?.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL).orEmpty(),
            software = exif?.getAttribute(ExifInterface.TAG_SOFTWARE).orEmpty(),
            makerNote = exif?.getAttribute(ExifInterface.TAG_MAKER_NOTE).orEmpty(),
            fileSizeBytes = file.length()
        )
    }

    fun createScaledThumbnail(source: Bitmap, maxEdgePx: Int = GalleryImageDecoderMath.THUMBNAIL_MAX_EDGE_PX): Bitmap {
        val (targetW, targetH) =
            GalleryImageDecoderMath.computeScaledDimensions(
                width = source.width,
                height = source.height,
                maxEdgePx = maxEdgePx
            )
        return if (targetW == source.width && targetH == source.height) {
            source.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            Bitmap.createScaledBitmap(source, targetW, targetH, true)
        }
    }

    fun rotateBitmap90Cw(source: Bitmap): Bitmap {
        val matrix = Matrix().apply { postRotate(90f) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    fun cropBitmapToAspect(source: Bitmap, targetAspect: Float): Bitmap {
        if (targetAspect <= 0f || source.width <= 0 || source.height <= 0) return source
        val srcAspect = source.width.toFloat() / source.height.toFloat()
        if (abs(srcAspect - targetAspect) < 0.01f) return source

        val cropW: Int
        val cropH: Int
        if (srcAspect > targetAspect) {
            cropH = source.height
            cropW = (cropH * targetAspect).roundToInt().coerceIn(2, source.width)
        } else {
            cropW = source.width
            cropH = (cropW / targetAspect).roundToInt().coerceIn(2, source.height)
        }
        val x = ((source.width - cropW) / 2).coerceAtLeast(0)
        val y = ((source.height - cropH) / 2).coerceAtLeast(0)
        return Bitmap.createBitmap(source, x, y, cropW, cropH)
    }

    fun synthesizeParallaxFrames(source: Bitmap, count: Int = 4): List<Bitmap> {
        val safeCount = count.coerceIn(2, 6)
        val marginRatio = 0.06f
        val cropW = (source.width * (1f - marginRatio)).roundToInt().coerceIn(2, source.width)
        val cropH = source.height
        val maxShiftX = (source.width - cropW).coerceAtLeast(0)
        return (0 until safeCount).map { idx ->
            val t = if (safeCount <= 1) 0.5f else idx.toFloat() / (safeCount - 1).toFloat()
            val x = (maxShiftX * t).roundToInt().coerceIn(0, maxShiftX)
            Bitmap.createBitmap(source, x, 0, cropW, cropH)
        }
    }

    suspend fun saveD3dFramesToDir(filesDir: File, recipeId: String, frames: List<Bitmap>) =
        withContext(Dispatchers.IO) {
            if (frames.isEmpty()) return@withContext
            val dir = d3dDirFor(filesDir, recipeId).apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            frames.forEachIndexed { index, bitmap ->
                val outFile = File(dir, "frame_$index.jpg")
                FileOutputStream(outFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, D3D_FRAME_JPEG_QUALITY, out)
                }
            }
        }

    suspend fun loadD3dFrames(
        filesDir: File,
        recipeId: String,
        cleanImagePath: String,
        maxEdgePx: Int = 960
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        val dir = d3dDirFor(filesDir, recipeId)
        val savedFiles =
            dir
                .takeIf { it.exists() && it.isDirectory }
                ?.listFiles { file -> file.isFile && file.name.startsWith("frame_") }
                ?.sortedBy { it.name }
                .orEmpty()
        if (savedFiles.isNotEmpty()) {
            val decoded = savedFiles.mapNotNull { decodeFileSafely(it.absolutePath, maxEdgePx) }
            if (decoded.isNotEmpty()) return@withContext decoded
        }

        if (cleanImagePath.isNotBlank()) {
            val cleanFile = File(cleanImagePath)
            val parent = cleanFile.parentFile
            val stem = cleanFile.nameWithoutExtension
            if (parent != null && parent.exists()) {
                val siblings =
                    (0 until 4).mapNotNull { i ->
                        val candidate = File(parent, "${stem}_f$i.jpg")
                        if (candidate.exists()) decodeFileSafely(candidate.absolutePath, maxEdgePx) else null
                    }
                if (siblings.size >= 2) return@withContext siblings
            }
            val single = decodeFileSafely(cleanImagePath, maxEdgePx)
            if (single != null) {
                val parallax = synthesizeParallaxFrames(single, count = 4)
                single.recycle()
                return@withContext parallax
            }
        }
        emptyList()
    }

    fun deleteD3dFramesDir(filesDir: File, recipeId: String) {
        val dir = d3dDirFor(filesDir, recipeId)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }

    private fun d3dDirFor(filesDir: File, recipeId: String): File {
        val safeId = recipeId.replace(Regex("[^A-Za-z0-9_-]+"), "_")
        return File(File(filesDir, D3D_FRAMES_DIR), safeId)
    }

    fun decodeUriSafely(
        contentResolver: ContentResolver,
        uri: Uri,
        maxEdgePx: Int = GalleryImageDecoderMath.MAX_IMPORT_EDGE_PX
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        } ?: return null

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sampleSize =
            GalleryImageDecoderMath.calculateInSampleSize(
                width = bounds.outWidth,
                height = bounds.outHeight,
                maxEdgePx = maxEdgePx
            )

        val decodeOptions =
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

        val rawBitmap =
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

        val exifTransform =
            contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val orientation =
                    exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                GalleryImageDecoderMath.exifOrientationToTransform(orientation)
            } ?: GalleryExifTransform()

        return applyTransformAndCap(rawBitmap, exifTransform, maxEdgePx)
    }

    fun decodeFileSafely(path: String, maxEdgePx: Int = GalleryImageDecoderMath.MAX_IMPORT_EDGE_PX): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sampleSize =
            GalleryImageDecoderMath.calculateInSampleSize(
                width = bounds.outWidth,
                height = bounds.outHeight,
                maxEdgePx = maxEdgePx
            )

        val decodeOptions =
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        val rawBitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return null
        val exif = runCatching { ExifInterface(file.absolutePath) }.getOrNull()
        val orientation =
            exif?.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            ) ?: ExifInterface.ORIENTATION_NORMAL
        val transform = GalleryImageDecoderMath.exifOrientationToTransform(orientation)
        return applyTransformAndCap(rawBitmap, transform, maxEdgePx)
    }

    private fun applyTransformAndCap(source: Bitmap, transform: GalleryExifTransform, maxEdgePx: Int): Bitmap {
        val oriented =
            if (transform.isIdentity) {
                source
            } else {
                val matrix =
                    Matrix().apply {
                        if (transform.flipHorizontal || transform.flipVertical) {
                            postScale(
                                if (transform.flipHorizontal) -1f else 1f,
                                if (transform.flipVertical) -1f else 1f
                            )
                        }
                        if (transform.rotationDegrees != 0) {
                            postRotate(transform.rotationDegrees.toFloat())
                        }
                    }
                val rotated =
                    Bitmap.createBitmap(
                        source,
                        0,
                        0,
                        source.width,
                        source.height,
                        matrix,
                        true
                    )
                if (rotated !== source) {
                    source.recycle()
                }
                rotated
            }

        val (targetW, targetH) =
            GalleryImageDecoderMath.computeScaledDimensions(
                width = oriented.width,
                height = oriented.height,
                maxEdgePx = maxEdgePx
            )
        if (targetW == oriented.width && targetH == oriented.height) {
            return oriented
        }
        val scaled = Bitmap.createScaledBitmap(oriented, targetW, targetH, true)
        if (scaled !== oriented) {
            oriented.recycle()
        }
        return scaled
    }
}
