package com.themasum.aayna.core.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BitmapDecodeMathTest {
    @Test
    fun `computeInSampleSize keeps 12MP sensor at sample size 1 for 4096 cap`() {
        val sampleSize =
            BitmapDecodeMath.computeInSampleSize(
                rawWidth = 4000,
                rawHeight = 3000,
                maxLongEdge = 4096
            )

        assertEquals(1, sampleSize)
        assertTrue(4000 / sampleSize <= 4096)
    }

    @Test
    fun `computeInSampleSize downsamples 48MP 8000x6000 to sample size 2`() {
        val sampleSize =
            BitmapDecodeMath.computeInSampleSize(
                rawWidth = 8000,
                rawHeight = 6000,
                maxLongEdge = 4096
            )

        assertEquals(2, sampleSize)
        assertEquals(4000, 8000 / sampleSize)
        assertEquals(3000, 6000 / sampleSize)
        assertTrue(maxOf(8000, 6000) / sampleSize <= 4096)
    }

    @Test
    fun `computeInSampleSize downsamples 108MP 12000x9000 to sample size 4`() {
        val sampleSize =
            BitmapDecodeMath.computeInSampleSize(
                rawWidth = 12000,
                rawHeight = 9000,
                maxLongEdge = 4096
            )

        assertEquals(4, sampleSize)
        assertEquals(3000, 12000 / sampleSize)
        assertEquals(2250, 9000 / sampleSize)
        assertTrue(maxOf(12000, 9000) / sampleSize <= 4096)
    }

    @Test
    fun `computeInSampleSize handles portrait orientation and custom cap`() {
        val sampleSize =
            BitmapDecodeMath.computeInSampleSize(
                rawWidth = 3000,
                rawHeight = 4000,
                maxLongEdge = 2560
            )

        assertEquals(2, sampleSize)
        assertTrue(4000 / sampleSize <= 2560)
    }

    @Test
    fun `computeInSampleSize guards non-positive inputs`() {
        assertEquals(1, BitmapDecodeMath.computeInSampleSize(0, 3000, 4096))
        assertEquals(1, BitmapDecodeMath.computeInSampleSize(4000, -10, 4096))
        assertEquals(1, BitmapDecodeMath.computeInSampleSize(8000, 6000, 0))
    }

    @Test
    fun `computeExifTransform maps rear camera rotations without mirroring`() {
        assertEquals(
            ExifTransform(rotationDegrees = 0, mirrorHorizontal = false),
            BitmapDecodeMath.computeExifTransform(
                exifOrientation = BitmapDecodeMath.EXIF_ORIENTATION_NORMAL,
                isFrontCamera = false
            )
        )
        assertEquals(
            ExifTransform(rotationDegrees = 90, mirrorHorizontal = false),
            BitmapDecodeMath.computeExifTransform(
                exifOrientation = BitmapDecodeMath.EXIF_ORIENTATION_ROTATE_90,
                isFrontCamera = false
            )
        )
        assertEquals(
            ExifTransform(rotationDegrees = 180, mirrorHorizontal = false),
            BitmapDecodeMath.computeExifTransform(
                exifOrientation = BitmapDecodeMath.EXIF_ORIENTATION_ROTATE_180,
                isFrontCamera = false
            )
        )
        assertEquals(
            ExifTransform(rotationDegrees = 270, mirrorHorizontal = false),
            BitmapDecodeMath.computeExifTransform(
                exifOrientation = BitmapDecodeMath.EXIF_ORIENTATION_ROTATE_270,
                isFrontCamera = false
            )
        )
    }

    @Test
    fun `computeExifTransform mirrors front camera and toggles existing flip`() {
        val frontNormal =
            BitmapDecodeMath.computeExifTransform(
                exifOrientation = BitmapDecodeMath.EXIF_ORIENTATION_ROTATE_270,
                isFrontCamera = true
            )
        assertEquals(270, frontNormal.rotationDegrees)
        assertTrue(frontNormal.mirrorHorizontal)

        val frontAlreadyFlipped =
            BitmapDecodeMath.computeExifTransform(
                exifOrientation = BitmapDecodeMath.EXIF_ORIENTATION_FLIP_HORIZONTAL,
                isFrontCamera = true
            )
        assertEquals(0, frontAlreadyFlipped.rotationDegrees)
        assertFalse(frontAlreadyFlipped.mirrorHorizontal)
    }

    @Test
    fun `computeExifTransform respects mirrorFrontCamera and swapFrontMirror quirk`() {
        val unmirroredFront =
            BitmapDecodeMath.computeExifTransform(
                exifOrientation = BitmapDecodeMath.EXIF_ORIENTATION_NORMAL,
                isFrontCamera = true,
                mirrorFrontCamera = false,
                swapFrontMirror = false
            )
        assertFalse(unmirroredFront.mirrorHorizontal)

        val quirkSwappedMirrorOn =
            BitmapDecodeMath.computeExifTransform(
                exifOrientation = BitmapDecodeMath.EXIF_ORIENTATION_NORMAL,
                isFrontCamera = true,
                mirrorFrontCamera = true,
                swapFrontMirror = true
            )
        assertFalse(quirkSwappedMirrorOn.mirrorHorizontal)

        val quirkSwappedMirrorOff =
            BitmapDecodeMath.computeExifTransform(
                exifOrientation = BitmapDecodeMath.EXIF_ORIENTATION_ROTATE_90,
                isFrontCamera = true,
                mirrorFrontCamera = false,
                swapFrontMirror = true
            )
        assertEquals(90, quirkSwappedMirrorOff.rotationDegrees)
        assertTrue(quirkSwappedMirrorOff.mirrorHorizontal)

        val rearIgnoresFrontQuirk =
            BitmapDecodeMath.computeExifTransform(
                exifOrientation = BitmapDecodeMath.EXIF_ORIENTATION_TRANSPOSE,
                isFrontCamera = false,
                mirrorFrontCamera = true,
                swapFrontMirror = true
            )
        assertEquals(90, rearIgnoresFrontQuirk.rotationDegrees)
        assertTrue(rearIgnoresFrontQuirk.mirrorHorizontal)
    }
}
