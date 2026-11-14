package com.themasum.aayna.core.gl

import com.themasum.aayna.core.model.AspectRatioPreset
import com.themasum.aayna.core.model.FrameWindowMeta
import com.themasum.aayna.core.model.OpticalSettings
import com.themasum.aayna.core.model.StampAtlasMeta
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun compute2dLutUv_mapsTileCornersAndHalfTexelCentersWithoutBleed() {
        // Black (0, 0, 0) -> tile (0, 0), pixel center (0.5, 0.5)
        val black = GlMath.compute2dLutUv(0f, 0f, 0f)
        assertEquals(0.5f / 512f, black.u0, 1e-6f)
        assertEquals(0.5f / 512f, black.v0, 1e-6f)
        assertEquals(0f, black.fracB, 1e-6f)

        // Pure Red in slice b=0 (1, 0, 0) -> tile (0, 0), pixel center (63.5, 0.5)
        val red = GlMath.compute2dLutUv(1f, 0f, 0f)
        assertEquals(63.5f / 512f, red.u0, 1e-6f)
        assertEquals(0.5f / 512f, red.v0, 1e-6f)

        // Pure Green in slice b=0 (0, 1, 0) -> tile (0, 0), pixel center (0.5, 63.5)
        val green = GlMath.compute2dLutUv(0f, 1f, 0f)
        assertEquals(0.5f / 512f, green.u0, 1e-6f)
        assertEquals(63.5f / 512f, green.v0, 1e-6f)

        // White (1, 1, 1) -> b=63 -> tile (7, 7), pixel center (511.5, 511.5)
        val white = GlMath.compute2dLutUv(1f, 1f, 1f)
        assertEquals(511.5f / 512f, white.u0, 1e-6f)
        assertEquals(511.5f / 512f, white.v0, 1e-6f)
        assertEquals(0f, white.fracB, 1e-6f)

        // Blue slice b=8 (8/63) -> tile (0, 1) -> x=0.5, y=64.5
        val slice8 = GlMath.compute2dLutUv(0f, 0f, 8f / 63f)
        assertEquals(0.5f / 512f, slice8.u0, 1e-6f)
        assertEquals(64.5f / 512f, slice8.v0, 1e-6f)

        // Fractional halfway between slice 0 and slice 1 -> tile (0,0) and tile (1,0), fracB = 0.5
        val midB = GlMath.compute2dLutUv(0f, 0f, 0.5f / 63f)
        assertEquals(0.5f / 512f, midB.u0, 1e-6f)
        assertEquals(0.5f / 512f, midB.v0, 1e-6f)
        assertEquals(64.5f / 512f, midB.u1, 1e-6f)
        assertEquals(0.5f / 512f, midB.v1, 1e-6f)
        assertEquals(0.5f, midB.fracB, 1e-5f)
    }

    @Test
    fun computeEvScale_matchesPow2() {
        assertEquals(1.0f, GlMath.computeEvScale(0f), 1e-6f)
        assertEquals(2.0f, GlMath.computeEvScale(1f), 1e-6f)
        assertEquals(4.0f, GlMath.computeEvScale(2f), 1e-6f)
        assertEquals(0.5f, GlMath.computeEvScale(-1f), 1e-6f)
    }

    @Test
    fun computeBradfordWbMatrix_identityWarmCoolAndTintSanity() {
        val identity = GlMath.computeBradfordWbMatrix(0f, 0f, 0f)
        assertArrayEquals(
            floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f),
            identity,
            1e-6f
        )

        val warmMat = GlMath.computeBradfordWbMatrix(0f, warmthShift = 0.8f, tintShift = 0f)
        val (wR, wG, wB) = GlMath.transformRgbByMat3(warmMat, 0.5f, 0.5f, 0.5f)
        assertTrue("Warm shift should increase R > G ($wR vs $wG)", wR > wG)
        assertTrue("Warm shift should decrease B < G ($wB vs $wG)", wG > wB)

        val coolMat = GlMath.computeBradfordWbMatrix(0f, warmthShift = -0.8f, tintShift = 0f)
        val (cR, cG, cB) = GlMath.transformRgbByMat3(coolMat, 0.5f, 0.5f, 0.5f)
        assertTrue("Cool shift should increase B > G ($cB vs $cG)", cB > cG)
        assertTrue("Cool shift should decrease R < G ($cR vs $cG)", cG > cR)

        val greenTintMat = GlMath.computeBradfordWbMatrix(0f, 0f, tintShift = 0.6f)
        val (gR, gG, gB) = GlMath.transformRgbByMat3(greenTintMat, 0.5f, 0.5f, 0.5f)
        assertTrue("Positive tint should elevate G above R and B", gG > gR && gG > gB)

        val magentaTintMat = GlMath.computeBradfordWbMatrix(0f, 0f, tintShift = -0.6f)
        val (mR, mG, mB) = GlMath.transformRgbByMat3(magentaTintMat, 0.5f, 0.5f, 0.5f)
        assertTrue("Negative tint should elevate R and B above G", mR > mG && mB > mG)

        for (temp in listOf(-1f, 0f, 1f)) {
            for (warmth in listOf(-1f, 0f, 1f)) {
                for (tint in listOf(-1f, 0f, 1f)) {
                    val m = GlMath.computeBradfordWbMatrix(temp, warmth, tint)
                    assertTrue("All Bradford entries must be finite", m.all { it.isFinite() })
                }
            }
        }
    }

    @Test
    fun computeStraightenScale_keepsRotatedCornersInsideUnitUvSquare() {
        assertEquals(1.0f, GlMath.computeStraightenScale(0f, 4f / 3f), 1e-6f)
        assertEquals(1f / sqrt(2.0).toFloat(), GlMath.computeStraightenScale(45f, 1.0f), 1e-5f)

        val aspects = listOf(4f / 3f, 3f / 2f, 1f, 16f / 9f, 9f / 16f)
        val angles = listOf(-15f, -7.5f, -1f, 0f, 1f, 7.5f, 15f)
        val corners = listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f)

        for (aspect in aspects) {
            for (deg in angles) {
                val scale = GlMath.computeStraightenScale(deg, aspect)
                assertTrue("Straighten scale in (0, 1]", scale > 0f && scale <= 1f)
                val rad = Math.toRadians(deg.toDouble())
                val c = cos(rad)
                val s = sin(rad)
                for ((u, v) in corners) {
                    val px = (u - 0.5) * aspect * scale
                    val py = (v - 0.5) * scale
                    val rx = px * c - py * s
                    val ry = px * s + py * c
                    val outU = rx / aspect + 0.5
                    val outV = ry + 0.5
                    assertTrue(
                        "Corner ($u,$v) at $deg deg, aspect $aspect out of U bounds: $outU",
                        outU >= -1e-5 && outU <= 1.0 + 1e-5
                    )
                    assertTrue(
                        "Corner ($u,$v) at $deg deg, aspect $aspect out of V bounds: $outV",
                        outV >= -1e-5 && outV <= 1.0 + 1e-5
                    )
                }
            }
        }
    }

    @Test
    fun computeEffectiveAspectRatio_honorsCropOverrideAndPortraitOrientation() {
        val base = OpticalSettings(aspectRatio = AspectRatioPreset.RATIO_4_3, cropAspectRatio = null)
        assertEquals(4f / 3f, GlMath.computeEffectiveAspectRatio(base, isPortraitSurface = false), 1e-5f)
        assertEquals(3f / 4f, GlMath.computeEffectiveAspectRatio(base, isPortraitSurface = true), 1e-5f)

        val cropped = base.copy(cropAspectRatio = AspectRatioPreset.RATIO_16_9)
        assertEquals(16f / 9f, GlMath.computeEffectiveAspectRatio(cropped, isPortraitSurface = false), 1e-5f)
        assertEquals(9f / 16f, GlMath.computeEffectiveAspectRatio(cropped, isPortraitSurface = true), 1e-5f)
    }

    @Test
    fun computeFramePanelViewport_flipsTopDownPanelsToBottomUpGlViewport() {
        val panels =
            listOf(
                listOf(252, 44, 772, 244),
                listOf(252, 268, 772, 468),
                listOf(252, 492, 772, 692),
                listOf(252, 716, 772, 916)
            )
        // Native 1024x1024
        val vp0 = GlMath.computeFramePanelViewport(panels[0], 1024, 1024, 1024, 1024)
        assertEquals(ViewportRect(x = 252, y = 780, width = 520, height = 200), vp0)

        val vp3 = GlMath.computeFramePanelViewport(panels[3], 1024, 1024, 1024, 1024)
        assertEquals(ViewportRect(x = 252, y = 108, width = 520, height = 200), vp3)

        // Half resolution 512x512
        val half0 = GlMath.computeFramePanelViewport(panels[0], 1024, 1024, 512, 512)
        assertEquals(ViewportRect(x = 126, y = 390, width = 260, height = 100), half0)

        // Normalized panels
        val meta =
            FrameWindowMeta(
                uMin = 0.24609375f,
                vMin = 0.04296875f,
                uMax = 0.75390625f,
                vMax = 0.89453125f,
                widthPx = 1024,
                heightPx = 1024,
                windowPx = listOf(252, 44, 772, 916),
                panels = panels
            )
        val norm = GlMath.computeFramePanelsNormalized(meta)
        assertEquals(4, norm.size)
        assertTrue(abs(norm[0][0] - 252f / 1024f) < 1e-6f)
        assertTrue(abs(norm[0][1] - 44f / 1024f) < 1e-6f)
        assertTrue(abs(norm[0][2] - 772f / 1024f) < 1e-6f)
        assertTrue(abs(norm[0][3] - 244f / 1024f) < 1e-6f)
    }
}
