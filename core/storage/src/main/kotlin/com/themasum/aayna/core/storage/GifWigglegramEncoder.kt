package com.themasum.aayna.core.storage

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlin.math.abs

/**
 * Pure-Kotlin, offline GIF89a multi-frame looping encoder for D3D wigglegrams.
 * Zero native dependencies, zero network calls.
 */
object GifWigglegramEncoder {
    const val DEFAULT_FRAME_DELAY_MS: Int = 140
    const val MIN_FRAME_DELAY_MS: Int = 20
    const val DEFAULT_LOOP_COUNT: Int = 0

    data class GifFrame(
        val width: Int,
        val height: Int,
        val argbPixels: IntArray,
        val delayMs: Int = DEFAULT_FRAME_DELAY_MS
    ) {
        init {
            require(width > 0 && height > 0) { "Frame dimensions must be positive: ${width}x$height" }
            require(argbPixels.size >= width * height) {
                "argbPixels size (${argbPixels.size}) < width*height (${width * height})"
            }
        }
    }

    // 256-entry palette: 6x6x6 RGB cube (216 colors) + 40-step grayscale ramp (216..255).
    private val globalPaletteRgb: ByteArray = buildGlobalPalette()
    private val cubeLevels = intArrayOf(0, 51, 102, 153, 204, 255)

    fun buildWiggleSequence(frames: List<GifFrame>, pingPong: Boolean = true): List<GifFrame> {
        if (!pingPong || frames.size <= 2) return frames
        val reversedMiddle = frames.subList(1, frames.size - 1).asReversed()
        return frames + reversedMiddle
    }

    fun encode(frames: List<GifFrame>, loopCount: Int = DEFAULT_LOOP_COUNT, pingPong: Boolean = true): ByteArray {
        val out = ByteArrayOutputStream()
        encodeToStream(frames = frames, outputStream = out, loopCount = loopCount, pingPong = pingPong)
        return out.toByteArray()
    }

    fun encodeToStream(
        frames: List<GifFrame>,
        outputStream: OutputStream,
        loopCount: Int = DEFAULT_LOOP_COUNT,
        pingPong: Boolean = true
    ) {
        require(frames.isNotEmpty()) { "At least one frame is required to encode a GIF" }
        val width = frames.first().width
        val height = frames.first().height
        frames.forEach { frame ->
            require(frame.width == width && frame.height == height) {
                "All GIF frames must share identical dimensions ($width x $height), got ${frame.width} x ${frame.height}"
            }
        }

        val sequence = buildWiggleSequence(frames, pingPong)

        // Header: GIF89a
        outputStream.write(
            byteArrayOf(
                'G'.code.toByte(),
                'I'.code.toByte(),
                'F'.code.toByte(),
                '8'.code.toByte(),
                '9'.code.toByte(),
                'a'.code.toByte()
            )
        )

        // Logical Screen Descriptor
        writeShortLe(outputStream, width)
        writeShortLe(outputStream, height)
        outputStream.write(0xF7) // Global Color Table = 1, Color Res = 7, Sort = 0, GCT Size = 7 (256 colors)
        outputStream.write(0x00) // Background color index
        outputStream.write(0x00) // Pixel aspect ratio

        // Global Color Table (256 * 3 bytes)
        outputStream.write(globalPaletteRgb)

        // Netscape 2.0 Application Extension (looping)
        writeNetscapeLoopExtension(outputStream, loopCount.coerceIn(0, 65535))

        // Frames
        sequence.forEach { frame ->
            val delayCs = (frame.delayMs.coerceAtLeast(MIN_FRAME_DELAY_MS) / 10).coerceIn(2, 65535)
            writeGraphicControlExtension(outputStream, delayCs)
            writeImageDescriptor(outputStream, width, height)
            val indexedPixels = quantizeFrame(frame.argbPixels, width * height)
            writeLzwImageData(outputStream, indexedPixels)
        }

        // Trailer
        outputStream.write(0x3B)
        outputStream.flush()
    }

    fun encodeBitmaps(
        bitmaps: List<Bitmap>,
        frameDelayMs: Int = DEFAULT_FRAME_DELAY_MS,
        loopCount: Int = DEFAULT_LOOP_COUNT,
        pingPong: Boolean = true
    ): ByteArray {
        require(bitmaps.isNotEmpty()) { "At least one Bitmap is required to encode a GIF" }
        val width = bitmaps.first().width
        val height = bitmaps.first().height
        val frames =
            bitmaps.map { bitmap ->
                val scaled =
                    if (bitmap.width == width && bitmap.height == height) {
                        bitmap
                    } else {
                        Bitmap.createScaledBitmap(bitmap, width, height, true)
                    }
                val pixels = IntArray(width * height)
                scaled.getPixels(pixels, 0, width, 0, 0, width, height)
                if (scaled !== bitmap) {
                    scaled.recycle()
                }
                GifFrame(width = width, height = height, argbPixels = pixels, delayMs = frameDelayMs)
            }
        return encode(frames = frames, loopCount = loopCount, pingPong = pingPong)
    }

    private fun writeNetscapeLoopExtension(out: OutputStream, loopCount: Int) {
        out.write(0x21)
        out.write(0xFF)
        out.write(0x0B)
        out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        out.write(0x03)
        out.write(0x01)
        writeShortLe(out, loopCount)
        out.write(0x00)
    }

    private fun writeGraphicControlExtension(out: OutputStream, delayCentiseconds: Int) {
        out.write(0x21)
        out.write(0xF9)
        out.write(0x04)
        out.write(0x04) // Disposal method = 1 (do not dispose), no transparent index
        writeShortLe(out, delayCentiseconds)
        out.write(0x00) // Transparent color index
        out.write(0x00) // Block terminator
    }

    private fun writeImageDescriptor(out: OutputStream, width: Int, height: Int) {
        out.write(0x2C)
        writeShortLe(out, 0)
        writeShortLe(out, 0)
        writeShortLe(out, width)
        writeShortLe(out, height)
        out.write(0x00) // Use global color table, not interlaced
    }

    private fun writeShortLe(out: OutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
    }

    private fun quantizeFrame(argbPixels: IntArray, pixelCount: Int): ByteArray {
        val indices = ByteArray(pixelCount)
        for (i in 0 until pixelCount) {
            val argb = argbPixels[i]
            val r = (argb ushr 16) and 0xFF
            val g = (argb ushr 8) and 0xFF
            val b = argb and 0xFF
            indices[i] = nearestPaletteIndex(r, g, b).toByte()
        }
        return indices
    }

    private fun nearestPaletteIndex(r: Int, g: Int, b: Int): Int {
        val ri = (r * 5 + 127) / 255
        val gi = (g * 5 + 127) / 255
        val bi = (b * 5 + 127) / 255
        val cubeIndex = ri * 36 + gi * 6 + bi
        val cr = cubeLevels[ri]
        val cg = cubeLevels[gi]
        val cb = cubeLevels[bi]
        val cubeErr = (r - cr) * (r - cr) + (g - cg) * (g - cg) + (b - cb) * (b - cb)

        // Check grayscale ramp (indices 216..255) for smoother monochrome/skin gradients.
        val lum = (r * 77 + g * 150 + b * 29) ushr 8
        val graySlot = ((lum * 39 + 127) / 255).coerceIn(0, 39)
        val grayVal = (graySlot * 255) / 39
        val grayErr = (r - grayVal) * (r - grayVal) + (g - grayVal) * (g - grayVal) + (b - grayVal) * (b - grayVal)

        return if (grayErr < cubeErr && abs(r - g) < 18 && abs(g - b) < 18) {
            216 + graySlot
        } else {
            cubeIndex
        }
    }

    private fun buildGlobalPalette(): ByteArray {
        val palette = ByteArray(256 * 3)
        var offset = 0
        for (r in 0..5) {
            for (g in 0..5) {
                for (b in 0..5) {
                    palette[offset++] = (r * 51).toByte()
                    palette[offset++] = (g * 51).toByte()
                    palette[offset++] = (b * 51).toByte()
                }
            }
        }
        for (i in 0 until 40) {
            val gray = ((i * 255) / 39).toByte()
            palette[offset++] = gray
            palette[offset++] = gray
            palette[offset++] = gray
        }
        return palette
    }

    private fun writeLzwImageData(out: OutputStream, pixels: ByteArray) {
        val minCodeSize = 8
        out.write(minCodeSize)

        val clearCode = 1 shl minCodeSize // 256
        val eoiCode = clearCode + 1 // 257
        val maxTableSize = 4096

        val subBlock = ByteArray(255)
        var subBlockLen = 0
        var bitBuffer = 0
        var bitCount = 0

        fun flushSubBlock() {
            if (subBlockLen > 0) {
                out.write(subBlockLen)
                out.write(subBlock, 0, subBlockLen)
                subBlockLen = 0
            }
        }

        fun writeCode(code: Int, codeSize: Int) {
            bitBuffer = bitBuffer or (code shl bitCount)
            bitCount += codeSize
            while (bitCount >= 8) {
                subBlock[subBlockLen++] = (bitBuffer and 0xFF).toByte()
                if (subBlockLen == 255) {
                    flushSubBlock()
                }
                bitBuffer = bitBuffer ushr 8
                bitCount -= 8
            }
        }

        // Hash table for LZW dictionary: key = (prefix shl 8) or suffix
        val hashSize = 8192
        val hashKeys = IntArray(hashSize) { -1 }
        val hashCodes = IntArray(hashSize)

        fun resetDictionary() {
            hashKeys.fill(-1)
        }

        var codeSize = minCodeSize + 1
        var nextCode = eoiCode + 1

        writeCode(clearCode, codeSize)
        var prefix = pixels[0].toInt() and 0xFF

        for (i in 1 until pixels.size) {
            val suffix = pixels[i].toInt() and 0xFF
            val key = (prefix shl 8) or suffix
            var slot = ((key * 2654435761L).toInt() ushr 19) and (hashSize - 1)
            var foundCode = -1
            while (true) {
                val existingKey = hashKeys[slot]
                if (existingKey == -1) break
                if (existingKey == key) {
                    foundCode = hashCodes[slot]
                    break
                }
                slot = (slot + 1) and (hashSize - 1)
            }

            if (foundCode != -1) {
                prefix = foundCode
            } else {
                writeCode(prefix, codeSize)
                if (nextCode < maxTableSize) {
                    hashKeys[slot] = key
                    hashCodes[slot] = nextCode
                    if (nextCode == (1 shl codeSize) && codeSize < 12) {
                        codeSize++
                    }
                    nextCode++
                } else {
                    writeCode(clearCode, codeSize)
                    resetDictionary()
                    codeSize = minCodeSize + 1
                    nextCode = eoiCode + 1
                }
                prefix = suffix
            }
        }

        writeCode(prefix, codeSize)
        writeCode(eoiCode, codeSize)

        if (bitCount > 0) {
            subBlock[subBlockLen++] = (bitBuffer and 0xFF).toByte()
        }
        flushSubBlock()
        out.write(0x00) // Block terminator
    }
}
