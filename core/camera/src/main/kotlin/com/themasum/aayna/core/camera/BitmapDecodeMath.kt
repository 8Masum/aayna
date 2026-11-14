package com.themasum.aayna.core.camera

data class ExifTransform(val rotationDegrees: Int, val mirrorHorizontal: Boolean)

object BitmapDecodeMath {
    const val DEFAULT_MAX_LONG_EDGE: Int = 4096

    // Standard EXIF orientation constants (matches android.media.ExifInterface.ORIENTATION_*)
    const val EXIF_ORIENTATION_UNDEFINED: Int = 0
    const val EXIF_ORIENTATION_NORMAL: Int = 1
    const val EXIF_ORIENTATION_FLIP_HORIZONTAL: Int = 2
    const val EXIF_ORIENTATION_ROTATE_180: Int = 3
    const val EXIF_ORIENTATION_FLIP_VERTICAL: Int = 4
    const val EXIF_ORIENTATION_TRANSPOSE: Int = 5
    const val EXIF_ORIENTATION_ROTATE_90: Int = 6
    const val EXIF_ORIENTATION_TRANSVERSE: Int = 7
    const val EXIF_ORIENTATION_ROTATE_270: Int = 8

    /**
     * Computes the smallest power-of-2 `inSampleSize` such that
     * `max(rawWidth, rawHeight) / inSampleSize <= maxLongEdge`.
     */
    fun computeInSampleSize(rawWidth: Int, rawHeight: Int, maxLongEdge: Int = DEFAULT_MAX_LONG_EDGE): Int {
        if (rawWidth <= 0 || rawHeight <= 0 || maxLongEdge <= 0) {
            return 1
        }
        val longEdge = maxOf(rawWidth, rawHeight)
        var sampleSize = 1
        while (longEdge / sampleSize > maxLongEdge) {
            sampleSize *= 2
        }
        return sampleSize
    }

    /**
     * Computes clockwise rotation degrees and horizontal mirror flag from an EXIF orientation
     * tag, whether the frame originated from a front-facing camera, user front-mirror preference,
     * and OEM [swapFrontMirror] hardware quirk.
     */
    fun computeExifTransform(
        exifOrientation: Int,
        isFrontCamera: Boolean,
        mirrorFrontCamera: Boolean = true,
        swapFrontMirror: Boolean = false
    ): ExifTransform {
        val (rotation, baseMirror) =
            when (exifOrientation) {
                EXIF_ORIENTATION_FLIP_HORIZONTAL -> 0 to true
                EXIF_ORIENTATION_ROTATE_180 -> 180 to false
                EXIF_ORIENTATION_FLIP_VERTICAL -> 180 to true
                EXIF_ORIENTATION_TRANSPOSE -> 90 to true
                EXIF_ORIENTATION_ROTATE_90 -> 90 to false
                EXIF_ORIENTATION_TRANSVERSE -> 270 to true
                EXIF_ORIENTATION_ROTATE_270 -> 270 to false
                else -> 0 to false
            }
        val applyFrontMirror =
            if (isFrontCamera) {
                if (swapFrontMirror) !mirrorFrontCamera else mirrorFrontCamera
            } else {
                false
            }
        val mirrorHorizontal = if (applyFrontMirror) !baseMirror else baseMirror
        return ExifTransform(
            rotationDegrees = rotation,
            mirrorHorizontal = mirrorHorizontal
        )
    }
}
