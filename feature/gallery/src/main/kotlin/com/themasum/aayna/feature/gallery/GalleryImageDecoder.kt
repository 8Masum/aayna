package com.themasum.aayna.feature.gallery

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

data class GalleryExifTransform(
    val rotationDegrees: Int = 0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false
) {
    val isIdentity: Boolean
        get() = rotationDegrees == 0 && !flipHorizontal && !flipVertical
}

object GalleryImageDecoderMath {
    const val MAX_IMPORT_EDGE_PX: Int = 4096

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
        val exif =
            runCatching { ExifInterface(file.absolutePath) }.getOrNull()
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
