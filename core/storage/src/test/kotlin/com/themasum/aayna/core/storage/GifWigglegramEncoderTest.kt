package com.themasum.aayna.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GifWigglegramEncoderTest {
    @Test
    fun `encode produces valid GIF89a header, Netscape loop block, GCE timing, and trailer`() {
        val width = 4
        val height = 3
        val frame1 =
            GifWigglegramEncoder.GifFrame(
                width = width,
                height = height,
                argbPixels = IntArray(width * height) { 0xFFFF0000.toInt() },
                delayMs = 150
            )
        val frame2 =
            GifWigglegramEncoder.GifFrame(
                width = width,
                height = height,
                argbPixels = IntArray(width * height) { 0xFF00FF00.toInt() },
                delayMs = 150
            )

        val bytes = GifWigglegramEncoder.encode(listOf(frame1, frame2), loopCount = 0, pingPong = false)

        // Header: GIF89a
        val header = bytes.copyOfRange(0, 6).toString(Charsets.US_ASCII)
        assertEquals("GIF89a", header)

        // Logical Screen Descriptor: 4x3 little-endian + 0xF7 packed
        assertEquals(4, bytes[6].toInt() and 0xFF)
        assertEquals(0, bytes[7].toInt() and 0xFF)
        assertEquals(3, bytes[8].toInt() and 0xFF)
        assertEquals(0, bytes[9].toInt() and 0xFF)
        assertEquals(0xF7, bytes[10].toInt() and 0xFF)

        // Trailer: 0x3B
        assertEquals(0x3B, bytes.last().toInt() and 0xFF)

        // Netscape 2.0 application extension present
        val rawAscii = bytes.toString(Charsets.US_ASCII)
        assertTrue(rawAscii.contains("NETSCAPE2.0"))

        // Graphic Control Extension blocks: 0x21 0xF9 0x04 0x04 <delayLo> <delayHi> 0x00 0x00
        // 150ms -> 15 centiseconds (0x0F, 0x00)
        val gcePositions =
            findSubsequencePositions(bytes, byteArrayOf(0x21, 0xF9.toByte(), 0x04, 0x04, 0x0F, 0x00, 0x00, 0x00))
        assertEquals(2, gcePositions.size)
    }

    @Test
    fun `buildWiggleSequence ping-pongs 3 or more frames smoothly`() {
        val frames =
            (0..2).map { index ->
                GifWigglegramEncoder.GifFrame(
                    width = 2,
                    height = 2,
                    argbPixels = intArrayOf(index, index, index, index),
                    delayMs = 120
                )
            }

        val sequence = GifWigglegramEncoder.buildWiggleSequence(frames, pingPong = true)
        assertEquals(4, sequence.size)
        assertEquals(0, sequence[0].argbPixels[0])
        assertEquals(1, sequence[1].argbPixels[0])
        assertEquals(2, sequence[2].argbPixels[0])
        assertEquals(1, sequence[3].argbPixels[0])
    }

    @Test
    fun `encode handles multi-subblock LZW stream for larger frames`() {
        val width = 48
        val height = 48
        val pixels =
            IntArray(width * height) { idx ->
                val r = (idx * 7) and 0xFF
                val g = (idx * 13) and 0xFF
                val b = (idx * 29) and 0xFF
                (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        val frame = GifWigglegramEncoder.GifFrame(width, height, pixels, delayMs = 100)
        val bytes = GifWigglegramEncoder.encode(listOf(frame), pingPong = false)
        assertEquals("GIF89a", bytes.copyOfRange(0, 6).toString(Charsets.US_ASCII))
        assertEquals(0x3B, bytes.last().toInt() and 0xFF)
        assertTrue(bytes.size > 800)
    }

    private fun findSubsequencePositions(haystack: ByteArray, needle: ByteArray): List<Int> {
        val matches = mutableListOf<Int>()
        for (i in 0..(haystack.size - needle.size)) {
            var matched = true
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) {
                    matched = false
                    break
                }
            }
            if (matched) matches.add(i)
        }
        return matches
    }
}
