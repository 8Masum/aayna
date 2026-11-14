package com.themasum.aayna.core.gl

import com.themasum.aayna.core.model.FrameWindowMeta
import com.themasum.aayna.core.model.OpticalSettings
import com.themasum.aayna.core.model.StampAtlasMeta
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

data class GlyphUvRect(val uMin: Float, val vMin: Float, val uMax: Float, val vMax: Float)

data class Lut2dSampleCoords(val u0: Float, val v0: Float, val u1: Float, val v1: Float, val fracB: Float)

data class ViewportRect(val x: Int, val y: Int, val width: Int, val height: Int)

object GlMath {
    const val MAX_STAMP_CHARS = 24

    // CIE 1931 D65 standard illuminant chromaticity
    private const val D65_X = 0.31271
    private const val D65_Y = 0.32902

    // Row-major sRGB (D65) -> CIE XYZ
    private val SRGB_TO_XYZ =
        doubleArrayOf(
            0.4124564,
            0.3575761,
            0.1804375,
            0.2126729,
            0.7151522,
            0.0721750,
            0.0193339,
            0.1191920,
            0.9503041
        )

    // Row-major CIE XYZ -> sRGB (D65)
    private val XYZ_TO_SRGB =
        doubleArrayOf(
            3.2404542,
            -1.5371385,
            -0.4985314,
            -0.9692660,
            1.8760108,
            0.0415560,
            0.0556434,
            -0.2040259,
            1.0572252
        )

    // Row-major Bradford cone response matrix
    private val BRADFORD =
        doubleArrayOf(
            0.8951000,
            0.2664000,
            -0.1614000,
            -0.7502000,
            1.7135000,
            0.0367000,
            0.0389000,
            -0.0685000,
            1.0296000
        )

    // Row-major inverse Bradford cone response matrix
    private val BRADFORD_INV =
        doubleArrayOf(
            0.9869929,
            -0.1470543,
            0.1599627,
            0.4323053,
            0.5183603,
            0.0492912,
            -0.0085287,
            0.0400428,
            0.9684867
        )

    /**
     * Computes `(scaleX, scaleY)` centered UV scale factors so an input texture of
     * [inputAspect] fills a target FBO of [targetAspect] via center crop without stretching.
     */
    fun computeAspectCropScale(inputAspect: Float, targetAspect: Float): Pair<Float, Float> {
        if (inputAspect <= 0f || targetAspect <= 0f) {
            return 1f to 1f
        }
        return if (inputAspect > targetAspect) {
            (targetAspect / inputAspect) to 1f
        } else if (inputAspect < targetAspect) {
            1f to (inputAspect / targetAspect)
        } else {
            1f to 1f
        }
    }

    /**
     * Computes the minimum zoom scale in `[1.0f, ...]` (applied as UV scale `<= 1.0f`, i.e.
     * `inscribedScale = 1f / zoom`) so rotating a rectangle of [aspectRatio] by [angleDegrees]
     * keeps all four corners inside `[0, 1]^2` without black corner triangles.
     */
    fun computeStraightenScale(angleDegrees: Float, aspectRatio: Float): Float {
        if (abs(angleDegrees) < 1e-4f || aspectRatio <= 0f) {
            return 1f
        }
        val rad = angleDegrees.toDouble() * PI / 180.0
        val absCos = abs(cos(rad))
        val absSin = abs(sin(rad))
        val a = aspectRatio.toDouble()
        val factorX = absCos + absSin / a
        val factorY = absCos + absSin * a
        val denom = maxOf(factorX, factorY).coerceAtLeast(1.0)
        return (1.0 / denom).toFloat()
    }

    /**
     * Computes the effective target aspect ratio from [opticalSettings], honoring
     * `cropAspectRatio` when set and flipping to portrait when [isPortraitSurface] is true.
     */
    fun computeEffectiveAspectRatio(opticalSettings: OpticalSettings, isPortraitSurface: Boolean): Float {
        val preset = opticalSettings.cropAspectRatio ?: opticalSettings.aspectRatio
        return if (isPortraitSurface) {
            preset.heightRatio.toFloat() / preset.widthRatio.toFloat()
        } else {
            preset.widthRatio.toFloat() / preset.heightRatio.toFloat()
        }
    }

    /**
     * Computes exact 2D LUT `(u0, v0, u1, v1, fracB)` coordinates for a `cubeSize x cubeSize x tilesX x tilesY`
     * PNG LUT (`64x64x8x8` on `512x512` with packing `x = (b % tilesX) * cubeSize + r`,
     * `y = (b / tilesX) * cubeSize + g`), using `+0.5` pixel-center offsets to prevent tile bleed.
     */
    fun compute2dLutUv(
        r: Float,
        g: Float,
        b: Float,
        cubeSize: Int = 64,
        tilesX: Int = 8,
        texWidth: Int = 512,
        texHeight: Int = 512
    ): Lut2dSampleCoords {
        val n = cubeSize.coerceAtLeast(2).toFloat()
        val maxIdx = n - 1f
        val tx = tilesX.coerceAtLeast(1)
        val w = texWidth.coerceAtLeast(1).toFloat()
        val h = texHeight.coerceAtLeast(1).toFloat()

        val clampedR = r.coerceIn(0f, 1f)
        val clampedG = g.coerceIn(0f, 1f)
        val clampedB = b.coerceIn(0f, 1f)

        val blueScaled = clampedB * maxIdx
        val b0 = floor(blueScaled).toInt().coerceIn(0, cubeSize - 1)
        val b1 = (b0 + 1).coerceAtMost(cubeSize - 1)
        val fracB = (blueScaled - b0).coerceIn(0f, 1f)

        val tile0X = (b0 % tx).toFloat()
        val tile0Y = (b0 / tx).toFloat()
        val tile1X = (b1 % tx).toFloat()
        val tile1Y = (b1 / tx).toFloat()

        val pxX = clampedR * maxIdx + 0.5f
        val pxY = clampedG * maxIdx + 0.5f

        return Lut2dSampleCoords(
            u0 = (tile0X * n + pxX) / w,
            v0 = (tile0Y * n + pxY) / h,
            u1 = (tile1X * n + pxX) / w,
            v1 = (tile1Y * n + pxY) / h,
            fracB = fracB
        )
    }

    /**
     * Inline EV linear multiplier `pow(2.0, evStops)`.
     */
    fun computeEvScale(evStops: Float): Float = 2.0.pow(evStops.toDouble()).toFloat()

    /**
     * Computes a column-major 3x3 linear sRGB-to-sRGB Bradford XYZ chromatic adaptation matrix
     * for [temperatureShift], [warmthShift] (`-1f..1f`), and [tintShift].
     *
     * When all inputs are `0f`, returns the exact 3x3 identity matrix.
     */
    fun computeBradfordWbMatrix(temperatureShift: Float, warmthShift: Float, tintShift: Float): FloatArray {
        val totalTemp = (temperatureShift + warmthShift * 0.45f).coerceIn(-1.5f, 1.5f)
        val totalTint = tintShift.coerceIn(-1f, 1f)

        if (abs(totalTemp) < 1e-6f && abs(totalTint) < 1e-6f) {
            return floatArrayOf(
                1f,
                0f,
                0f,
                0f,
                1f,
                0f,
                0f,
                0f,
                1f
            )
        }

        val srcX = D65_X / D65_Y
        val srcY = 1.0
        val srcZ = (1.0 - D65_X - D65_Y) / D65_Y

        val dstChromaX = (D65_X + totalTemp * 0.065).coerceIn(0.20, 0.46)
        val dstChromaY = (D65_Y + totalTemp * 0.025 + totalTint * 0.035).coerceIn(0.22, 0.46)
        val dstX = dstChromaX / dstChromaY
        val dstY = 1.0
        val dstZ = (1.0 - dstChromaX - dstChromaY) / dstChromaY

        val srcLms = mulMat3Vec3RowMajor(BRADFORD, srcX, srcY, srcZ)
        val dstLms = mulMat3Vec3RowMajor(BRADFORD, dstX, dstY, dstZ)

        val gainL = dstLms[0] / srcLms[0].coerceAtLeast(1e-6)
        val gainM = dstLms[1] / srcLms[1].coerceAtLeast(1e-6)
        val gainS = dstLms[2] / srcLms[2].coerceAtLeast(1e-6)

        val diagRowMajor =
            doubleArrayOf(
                gainL,
                0.0,
                0.0,
                0.0,
                gainM,
                0.0,
                0.0,
                0.0,
                gainS
            )

        // M_xyz_adapt = BRADFORD_INV * diag * BRADFORD
        val tmp1 = mulMat3RowMajor(diagRowMajor, BRADFORD)
        val xyzAdapt = mulMat3RowMajor(BRADFORD_INV, tmp1)

        // M_rgb_adapt = XYZ_TO_SRGB * xyzAdapt * SRGB_TO_XYZ
        val tmp2 = mulMat3RowMajor(xyzAdapt, SRGB_TO_XYZ)
        val rgbAdaptRowMajor = mulMat3RowMajor(XYZ_TO_SRGB, tmp2)

        // Convert row-major to GLSL column-major floatArrayOf(col0, col1, col2)
        return floatArrayOf(
            rgbAdaptRowMajor[0].toFloat(),
            rgbAdaptRowMajor[3].toFloat(),
            rgbAdaptRowMajor[6].toFloat(),
            rgbAdaptRowMajor[1].toFloat(),
            rgbAdaptRowMajor[4].toFloat(),
            rgbAdaptRowMajor[7].toFloat(),
            rgbAdaptRowMajor[2].toFloat(),
            rgbAdaptRowMajor[5].toFloat(),
            rgbAdaptRowMajor[8].toFloat()
        )
    }

    /**
     * Applies a GLSL column-major 3x3 matrix to an RGB triplet (for testing/CPU inspection).
     */
    fun transformRgbByMat3(colMajorMat3: FloatArray, r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        require(colMajorMat3.size == 9) { "Expected 9-element 3x3 matrix" }
        val outR = colMajorMat3[0] * r + colMajorMat3[3] * g + colMajorMat3[6] * b
        val outG = colMajorMat3[1] * r + colMajorMat3[4] * g + colMajorMat3[7] * b
        val outB = colMajorMat3[2] * r + colMajorMat3[5] * g + colMajorMat3[8] * b
        return Triple(outR, outG, outB)
    }

    /**
     * Converts a top-down pixel panel `[leftPx, topPx, rightPx, bottomPx]` from `grunge_meta.json`
     * into a bottom-up OpenGL viewport rectangle for an FBO of `[outputWidth, outputHeight]`.
     */
    fun computeFramePanelViewport(
        panelPx: List<Int>,
        frameWidthPx: Int,
        frameHeightPx: Int,
        outputWidth: Int,
        outputHeight: Int
    ): ViewportRect {
        require(panelPx.size >= 4) { "Panel rect must have 4 ints [left, top, right, bottom]" }
        val fw = frameWidthPx.coerceAtLeast(1).toDouble()
        val fh = frameHeightPx.coerceAtLeast(1).toDouble()
        val scaleX = outputWidth.toDouble() / fw
        val scaleY = outputHeight.toDouble() / fh

        val left = (panelPx[0] * scaleX).roundToInt().coerceIn(0, outputWidth)
        val right = (panelPx[2] * scaleX).roundToInt().coerceIn(left, outputWidth)
        val top = (panelPx[1] * scaleY).roundToInt().coerceIn(0, outputHeight)
        val bottom = (panelPx[3] * scaleY).roundToInt().coerceIn(top, outputHeight)

        val vpW = (right - left).coerceAtLeast(2)
        val vpH = (bottom - top).coerceAtLeast(2)
        val vpY = (outputHeight - bottom).coerceAtLeast(0)
        return ViewportRect(x = left, y = vpY, width = vpW, height = vpH)
    }

    /**
     * Normalizes [FrameWindowMeta.panels] into `[uMin, vMin, uMax, vMax]` quads in top-down PNG UV space.
     */
    fun computeFramePanelsNormalized(meta: FrameWindowMeta): List<FloatArray> {
        val fw = meta.widthPx.coerceAtLeast(1).toFloat()
        val fh = meta.heightPx.coerceAtLeast(1).toFloat()
        return meta.panels.mapNotNull { rect ->
            if (rect.size < 4) {
                null
            } else {
                floatArrayOf(
                    rect[0] / fw,
                    rect[1] / fh,
                    rect[2] / fw,
                    rect[3] / fh
                )
            }
        }
    }

    /**
     * Computes normalized `[uMin, vMin, uMax, vMax]` atlas UV bounds for [ch] in [meta],
     * respecting `glyphPadding` inside each cell. Unknown characters fall back to `' '`
     * (or index 0 if space is absent).
     */
    fun computeStampGlyphUv(meta: StampAtlasMeta, ch: Char): GlyphUvRect {
        val upper = ch.uppercaseChar()
        var index = meta.chars.indexOf(upper)
        if (index < 0) {
            index = meta.chars.indexOf(' ').coerceAtLeast(0)
        }
        val cols = meta.columns.coerceAtLeast(1)
        val col = index % cols
        val row = index / cols

        val atlasW = meta.atlasWidth.coerceAtLeast(1).toFloat()
        val atlasH = meta.atlasHeight.coerceAtLeast(1).toFloat()
        val pad = meta.glyphPadding.coerceAtLeast(0).toFloat()

        val leftPx = col * meta.cellWidth + pad
        val rightPx = (col + 1) * meta.cellWidth - pad
        val topPx = row * meta.cellHeight + pad
        val bottomPx = (row + 1) * meta.cellHeight - pad

        return GlyphUvRect(
            uMin = leftPx / atlasW,
            vMin = topPx / atlasH,
            uMax = rightPx / atlasW,
            vMax = bottomPx / atlasH
        )
    }

    /**
     * Maps each character in [text] (truncated to [maxChars]) to its glyph index in [meta.chars].
     */
    fun encodeStampIndices(meta: StampAtlasMeta, text: String, maxChars: Int = MAX_STAMP_CHARS): IntArray {
        val spaceIndex = meta.chars.indexOf(' ').coerceAtLeast(0)
        val count = text.length.coerceAtMost(maxChars)
        return IntArray(count) { i ->
            val idx = meta.chars.indexOf(text[i].uppercaseChar())
            if (idx >= 0) idx else spaceIndex
        }
    }

    /**
     * Parses `#RRGGBB` or `#AARRGGBB` hex into `(r, g, b)` floats in `0f..1f`.
     */
    fun parseHexRgb(
        hex: String,
        fallback: Triple<Float, Float, Float> = Triple(1f, 1f, 1f)
    ): Triple<Float, Float, Float> {
        val clean = hex.trim().removePrefix("#")
        val parsed = clean.toLongOrNull(16) ?: return fallback
        return when (clean.length) {
            6 -> {
                val r = ((parsed shr 16) and 0xFF) / 255f
                val g = ((parsed shr 8) and 0xFF) / 255f
                val b = (parsed and 0xFF) / 255f
                Triple(r, g, b)
            }
            8 -> {
                val r = ((parsed shr 16) and 0xFF) / 255f
                val g = ((parsed shr 8) and 0xFF) / 255f
                val b = (parsed and 0xFF) / 255f
                Triple(r, g, b)
            }
            else -> fallback
        }
    }

    private fun mulMat3Vec3RowMajor(m: DoubleArray, x: Double, y: Double, z: Double): DoubleArray = doubleArrayOf(
        m[0] * x + m[1] * y + m[2] * z,
        m[3] * x + m[4] * y + m[5] * z,
        m[6] * x + m[7] * y + m[8] * z
    )

    private fun mulMat3RowMajor(a: DoubleArray, b: DoubleArray): DoubleArray {
        val out = DoubleArray(9)
        for (row in 0 until 3) {
            val r3 = row * 3
            for (col in 0 until 3) {
                out[r3 + col] =
                    a[r3] * b[col] +
                    a[r3 + 1] * b[3 + col] +
                    a[r3 + 2] * b[6 + col]
            }
        }
        return out
    }
}
