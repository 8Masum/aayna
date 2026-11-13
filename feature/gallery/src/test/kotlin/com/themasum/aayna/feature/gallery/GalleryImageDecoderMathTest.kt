package com.themasum.aayna.feature.gallery

import androidx.exifinterface.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryImageDecoderMathTest {
    @Test
    fun `calculateInSampleSize returns 1 when image fits within max edge`() {
        assertEquals(1, GalleryImageDecoderMath.calculateInSampleSize(1920, 1080, 4096))
        assertEquals(1, GalleryImageDecoderMath.calculateInSampleSize(4096, 3072, 4096))
        assertEquals(1, GalleryImageDecoderMath.calculateInSampleSize(0, 0, 4096))
    }

    @Test
    fun `calculateInSampleSize returns power of two capping longest edge at or below max`() {
        assertEquals(2, GalleryImageDecoderMath.calculateInSampleSize(6000, 4000, 4096))
        assertEquals(2, GalleryImageDecoderMath.calculateInSampleSize(8192, 6144, 4096))
        assertEquals(4, GalleryImageDecoderMath.calculateInSampleSize(12000, 9000, 4096))
    }

    @Test
    fun `computeScaledDimensions preserves aspect ratio and caps longest edge`() {
        val (w1, h1) = GalleryImageDecoderMath.computeScaledDimensions(3000, 2000, 4096)
        assertEquals(3000, w1)
        assertEquals(2000, h1)

        val (w2, h2) = GalleryImageDecoderMath.computeScaledDimensions(8000, 4000, 4096)
        assertEquals(4096, w2)
        assertEquals(2048, h2)

        val (w3, h3) = GalleryImageDecoderMath.computeScaledDimensions(3000, 6000, 4096)
        assertEquals(2048, w3)
        assertEquals(4096, h3)
    }

    @Test
    fun `exifOrientationToTransform maps all standard orientations accurately`() {
        val normal = GalleryImageDecoderMath.exifOrientationToTransform(ExifInterface.ORIENTATION_NORMAL)
        assertTrue(normal.isIdentity)

        val rot90 = GalleryImageDecoderMath.exifOrientationToTransform(ExifInterface.ORIENTATION_ROTATE_90)
        assertEquals(90, rot90.rotationDegrees)
        assertFalse(rot90.flipHorizontal)
        assertFalse(rot90.flipVertical)

        val rot180 = GalleryImageDecoderMath.exifOrientationToTransform(ExifInterface.ORIENTATION_ROTATE_180)
        assertEquals(180, rot180.rotationDegrees)

        val rot270 = GalleryImageDecoderMath.exifOrientationToTransform(ExifInterface.ORIENTATION_ROTATE_270)
        assertEquals(270, rot270.rotationDegrees)

        val flipH = GalleryImageDecoderMath.exifOrientationToTransform(ExifInterface.ORIENTATION_FLIP_HORIZONTAL)
        assertEquals(0, flipH.rotationDegrees)
        assertTrue(flipH.flipHorizontal)

        val flipV = GalleryImageDecoderMath.exifOrientationToTransform(ExifInterface.ORIENTATION_FLIP_VERTICAL)
        assertEquals(0, flipV.rotationDegrees)
        assertTrue(flipV.flipVertical)

        val transpose = GalleryImageDecoderMath.exifOrientationToTransform(ExifInterface.ORIENTATION_TRANSPOSE)
        assertEquals(90, transpose.rotationDegrees)
        assertTrue(transpose.flipHorizontal)

        val transverse = GalleryImageDecoderMath.exifOrientationToTransform(ExifInterface.ORIENTATION_TRANSVERSE)
        assertEquals(270, transverse.rotationDegrees)
        assertTrue(transverse.flipHorizontal)
    }
}
