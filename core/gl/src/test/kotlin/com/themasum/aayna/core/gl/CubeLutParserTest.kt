package com.themasum.aayna.core.gl

import java.io.ByteArrayInputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CubeLutParserTest {
    @Test
    fun parseAllShippedCubeLuts_produces32CubedRgbaBytes() {
        val lutDir = resolveLutsDirectory()
        val cubeFiles =
            lutDir.listFiles { file -> file.extension == "cube" }?.sortedBy { it.name }.orEmpty()
        assertTrue("Expected shipped .cube files in ${lutDir.absolutePath}", cubeFiles.isNotEmpty())

        for (cubeFile in cubeFiles) {
            val data = cubeFile.inputStream().use { CubeLutParser.parse(it) }
            assertEquals("LUT size for ${cubeFile.name}", 32, data.size)
            assertEquals("Byte count for ${cubeFile.name}", 32 * 32 * 32 * 4, data.rgbaBytes.size)
            for (i in 0 until 32 * 32 * 32) {
                assertEquals(
                    "Alpha byte at index $i in ${cubeFile.name}",
                    0xFF.toByte(),
                    data.rgbaBytes[i * 4 + 3]
                )
            }
        }
    }

    @Test
    fun parseSyntheticCubeStream_handlesCommentsDomainAndClamping() {
        val cubeText =
            """
            # Synthetic 2x2x2 LUT
            TITLE "Test LUT"
            LUT_3D_SIZE 2
            DOMAIN_MIN 0.0 0.0 0.0
            DOMAIN_MAX 2.0 2.0 2.0

            0.0 0.0 0.0
            2.0 0.0 0.0
            0.0 2.0 0.0
            2.0 2.0 0.0
            0.0 0.0 2.0
            2.0 0.0 2.0
            0.0 2.0 2.0
            3.0 -0.5 2.0
            """.trimIndent()

        val data = CubeLutParser.parse(ByteArrayInputStream(cubeText.toByteArray(Charsets.UTF_8)))
        assertEquals(2, data.size)
        assertEquals(8 * 4, data.rgbaBytes.size)

        // Entry 0: (0, 0, 0) -> (0, 0, 0, 255)
        assertEquals(0.toByte(), data.rgbaBytes[0])
        assertEquals(0.toByte(), data.rgbaBytes[1])
        assertEquals(0.toByte(), data.rgbaBytes[2])
        assertEquals(0xFF.toByte(), data.rgbaBytes[3])

        // Entry 7: (3.0 -> clamped 1.0 -> 255, -0.5 -> clamped 0.0 -> 0, 2.0 -> 1.0 -> 255)
        val lastOffset = 7 * 4
        assertEquals(0xFF.toByte(), data.rgbaBytes[lastOffset])
        assertEquals(0.toByte(), data.rgbaBytes[lastOffset + 1])
        assertEquals(0xFF.toByte(), data.rgbaBytes[lastOffset + 2])
        assertEquals(0xFF.toByte(), data.rgbaBytes[lastOffset + 3])
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseMissingHeader_throwsIllegalArgumentException() {
        val invalid = "0.0 0.0 0.0\n1.0 1.0 1.0\n"
        CubeLutParser.parse(invalid.lineSequence())
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseIncompleteEntries_throwsIllegalArgumentException() {
        val invalid =
            """
            LUT_3D_SIZE 2
            0.0 0.0 0.0
            1.0 0.0 0.0
            """.trimIndent()
        CubeLutParser.parse(invalid.lineSequence())
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseMalformedFloat_throwsIllegalArgumentException() {
        val invalid =
            """
            LUT_3D_SIZE 2
            0.0 abc 0.0
            """.trimIndent()
        CubeLutParser.parse(invalid.lineSequence())
    }

    private fun resolveLutsDirectory(): File {
        val direct = File("src/main/assets/luts")
        if (direct.isDirectory) return direct
        return File("core/gl/src/main/assets/luts")
    }
}
