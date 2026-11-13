package com.themasum.aayna.core.gl

import com.themasum.aayna.core.model.AspectRatioPreset
import com.themasum.aayna.core.model.StampAtlasMeta
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class GlMathTest {
    private val sampleMeta =
        StampAtlasMeta(
            atlasWidth = 512,
            atlasHeight = 128,
            columns = 16,
            rows = 4,
            cellWidth = 32,
            cellHeight = 32,
            glyphPadding = 4,
            chars = "0123456789'.:-/ ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        )

    @Test
    fun computeAspectCropScale_widerInputCropsHorizontally() {
        val (sx, sy) =
            GlMath.computeAspectCropScale(
                inputAspect = 16f / 9f,
                targetAspect = 4f / 3f
            )
        assertEquals(0.75f, sx, 1e-4f)
        assertEquals(1.0f, sy, 1e-4f)
    }

    @Test
    fun computeAspectCropScale_tallerInputCropsVertically() {
        val (sx, sy) =
            GlMath.computeAspectCropScale(
                inputAspect = 4f / 3f,
                targetAspect = 16f / 9f
            )
        assertEquals(1.0f, sx, 1e-4f)
        assertEquals(0.75f, sy, 1e-4f)
    }

    @Test
    fun computeAspectCropScale_equalAndSquarePresets() {
        val target = AspectRatioPreset.RATIO_1_1
        val (sx, sy) =
            GlMath.computeAspectCropScale(
                inputAspect = 3f / 2f,
                targetAspect = target.widthRatio.toFloat() / target.heightRatio.toFloat()
            )
        assertEquals(2f / 3f, sx, 1e-4f)
        assertEquals(1.0f, sy, 1e-4f)

        val (eqX, eqY) = GlMath.computeAspectCropScale(4f / 3f, 4f / 3f)
        assertEquals(1.0f, eqX, 1e-5f)
        assertEquals(1.0f, eqY, 1e-5f)
    }

    @Test
    fun computeStampGlyphUv_computesExactPaddedCellBounds() {
        // Index 0 = '0' -> col 0, row 0 -> [4..28]/512, [4..28]/128
        val zeroUv = GlMath.computeStampGlyphUv(sampleMeta, '0')
        assertEquals(4f / 512f, zeroUv.uMin, 1e-5f)
        assertEquals(4f / 128f, zeroUv.vMin, 1e-5f)
        assertEquals(28f / 512f, zeroUv.uMax, 1e-5f)
        assertEquals(28f / 128f, zeroUv.vMax, 1e-5f)

        // Index 16 = 'A' -> col 0, row 1 -> [4..28]/512, [36..60]/128
        val aUv = GlMath.computeStampGlyphUv(sampleMeta, 'a')
        assertEquals(4f / 512f, aUv.uMin, 1e-5f)
        assertEquals(36f / 128f, aUv.vMin, 1e-5f)
        assertEquals(28f / 512f, aUv.uMax, 1e-5f)
        assertEquals(60f / 128f, aUv.vMax, 1e-5f)
    }

    @Test
    fun computeStampGlyphUv_unknownCharFallsBackToSpace() {
        val spaceUv = GlMath.computeStampGlyphUv(sampleMeta, ' ')
        val unknownUv = GlMath.computeStampGlyphUv(sampleMeta, '?')
        assertEquals(spaceUv, unknownUv)
    }

    @Test
    fun encodeStampIndices_mapsUppercaseAndUnknownToSpace() {
        val indices = GlMath.encodeStampIndices(sampleMeta, "'04 11?")
        val expected =
            intArrayOf(
                sampleMeta.chars.indexOf('\''),
                sampleMeta.chars.indexOf('0'),
                sampleMeta.chars.indexOf('4'),
                sampleMeta.chars.indexOf(' '),
                sampleMeta.chars.indexOf('1'),
                sampleMeta.chars.indexOf('1'),
                sampleMeta.chars.indexOf(' ')
            )
        assertArrayEquals(expected, indices)
    }
}
