package com.themasum.aayna.core.gl

import android.content.res.AssetManager
import android.opengl.GLES30
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class CubeLutData(val size: Int, val rgbaBytes: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CubeLutData) return false
        return size == other.size && rgbaBytes.contentEquals(other.rgbaBytes)
    }

    override fun hashCode(): Int {
        var result = size
        result = 31 * result + rgbaBytes.contentHashCode()
        return result
    }
}

object CubeLutParser {
    private const val EXPECTED_MIN_SIZE = 2
    private const val EXPECTED_MAX_SIZE = 128

    fun loadFromAssets(assetManager: AssetManager, assetPath: String): CubeLutData =
        assetManager.open(assetPath).use { stream ->
            parse(stream)
        }

    fun parse(inputStream: InputStream): CubeLutData {
        BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
            return parse(reader.lineSequence())
        }
    }

    fun parse(lines: Sequence<String>): CubeLutData {
        var lutSize = -1
        var domainMin = floatArrayOf(0f, 0f, 0f)
        var domainMax = floatArrayOf(1f, 1f, 1f)
        var rgbaBytes: ByteArray? = null
        var writeOffset = 0
        var tripletCount = 0

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) {
                continue
            }

            if (line.startsWith("TITLE", ignoreCase = true) ||
                line.startsWith("LUT_1D_SIZE", ignoreCase = true)
            ) {
                continue
            }

            if (line.startsWith("LUT_3D_SIZE", ignoreCase = true)) {
                val tokens = line.split(WHITESPACE_REGEX)
                require(tokens.size >= 2) { "Malformed LUT_3D_SIZE line: $line" }
                val parsedSize =
                    tokens[1].toIntOrNull()
                        ?: throw IllegalArgumentException("Invalid LUT_3D_SIZE value: ${tokens[1]}")
                require(parsedSize in EXPECTED_MIN_SIZE..EXPECTED_MAX_SIZE) {
                    "Unsupported LUT_3D_SIZE: $parsedSize"
                }
                lutSize = parsedSize
                val totalEntries = parsedSize * parsedSize * parsedSize
                rgbaBytes = ByteArray(totalEntries * 4)
                continue
            }

            if (line.startsWith("DOMAIN_MIN", ignoreCase = true)) {
                domainMin = parseFloatTriplet(line, dropFirstToken = true)
                continue
            }

            if (line.startsWith("DOMAIN_MAX", ignoreCase = true)) {
                domainMax = parseFloatTriplet(line, dropFirstToken = true)
                continue
            }

            val size = lutSize
            val out =
                rgbaBytes
                    ?: throw IllegalArgumentException("LUT data encountered before LUT_3D_SIZE header")
            val expectedTotal = size * size * size
            require(tripletCount < expectedTotal) {
                "Too many LUT entries: expected $expectedTotal"
            }

            val rgb = parseFloatTriplet(line, dropFirstToken = false)
            out[writeOffset++] = normalizeToByte(rgb[0], domainMin[0], domainMax[0])
            out[writeOffset++] = normalizeToByte(rgb[1], domainMin[1], domainMax[1])
            out[writeOffset++] = normalizeToByte(rgb[2], domainMin[2], domainMax[2])
            out[writeOffset++] = 0xFF.toByte()
            tripletCount++
        }

        require(lutSize > 0 && rgbaBytes != null) { "Missing LUT_3D_SIZE header in .cube stream" }
        val expectedTotal = lutSize * lutSize * lutSize
        require(tripletCount == expectedTotal) {
            "Incomplete .cube LUT: expected $expectedTotal entries, got $tripletCount"
        }

        return CubeLutData(size = lutSize, rgbaBytes = rgbaBytes)
    }

    fun uploadToTexture3D(lut: CubeLutData): Int {
        val textureIds = IntArray(1)
        GLES30.glGenTextures(1, textureIds, 0)
        val textureId = textureIds[0]
        check(textureId != 0) { "Failed to generate GL_TEXTURE_3D handle" }

        val buffer =
            ByteBuffer
                .allocateDirect(lut.rgbaBytes.size)
                .order(ByteOrder.nativeOrder())
                .apply {
                    put(lut.rgbaBytes)
                    position(0)
                }

        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, textureId)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 4)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_R, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexImage3D(
            GLES30.GL_TEXTURE_3D,
            0,
            GLES30.GL_RGBA8,
            lut.size,
            lut.size,
            lut.size,
            0,
            GLES30.GL_RGBA,
            GLES30.GL_UNSIGNED_BYTE,
            buffer
        )
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, 0)
        return textureId
    }

    private fun parseFloatTriplet(line: String, dropFirstToken: Boolean): FloatArray {
        val tokens = line.split(WHITESPACE_REGEX)
        val offset = if (dropFirstToken) 1 else 0
        require(tokens.size >= offset + 3) { "Expected 3 float values in line: $line" }
        val r =
            tokens[offset].toFloatOrNull()
                ?: throw IllegalArgumentException("Invalid float '${tokens[offset]}' in line: $line")
        val g =
            tokens[offset + 1].toFloatOrNull()
                ?: throw IllegalArgumentException("Invalid float '${tokens[offset + 1]}' in line: $line")
        val b =
            tokens[offset + 2].toFloatOrNull()
                ?: throw IllegalArgumentException("Invalid float '${tokens[offset + 2]}' in line: $line")
        return floatArrayOf(r, g, b)
    }

    private fun normalizeToByte(value: Float, minVal: Float, maxVal: Float): Byte {
        val span = maxVal - minVal
        val normalized =
            if (span > 1e-6f) {
                (value - minVal) / span
            } else {
                value
            }
        val clamped = normalized.coerceIn(0f, 1f)
        return (clamped * 255f + 0.5f).toInt().coerceIn(0, 255).toByte()
    }

    private val WHITESPACE_REGEX = Regex("\\s+")
}
