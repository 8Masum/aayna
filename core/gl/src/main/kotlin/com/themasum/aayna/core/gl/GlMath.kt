package com.themasum.aayna.core.gl

import com.themasum.aayna.core.model.StampAtlasMeta

data class GlyphUvRect(val uMin: Float, val vMin: Float, val uMax: Float, val vMax: Float)

object GlMath {
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

    const val MAX_STAMP_CHARS = 24
}
