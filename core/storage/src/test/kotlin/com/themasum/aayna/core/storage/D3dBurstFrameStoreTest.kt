package com.themasum.aayna.core.storage

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class D3dBurstFrameStoreTest {
    @Test
    fun `framesDirFor and frameFileFor match gallery d3d_frames layout`() {
        val filesDir = File("/tmp/aayna_files")
        val dir = D3dBurstFrameStore.framesDirFor(filesDir, "rec:1/2")
        assertEquals(File(File(filesDir, "d3d_frames"), "rec_1_2"), dir)

        val frame0 = D3dBurstFrameStore.frameFileFor(filesDir, "rec-99", 0)
        val frame3 = D3dBurstFrameStore.frameFileFor(filesDir, "rec-99", 3)
        assertEquals("frame_0.jpg", frame0.name)
        assertEquals("frame_3.jpg", frame3.name)
        assertEquals("rec-99", frame0.parentFile?.name)
        assertEquals("d3d_frames", frame0.parentFile?.parentFile?.name)
    }

    @Test
    fun `computeScaledDimensions preserves small bitmaps and scales longest edge`() {
        assertEquals(800 to 600, D3dBurstFrameStore.computeScaledDimensions(800, 600, maxEdgePx = 1440))
        assertEquals(1440 to 1080, D3dBurstFrameStore.computeScaledDimensions(2880, 2160, maxEdgePx = 1440))
        assertEquals(720 to 1440, D3dBurstFrameStore.computeScaledDimensions(1080, 2160, maxEdgePx = 1440))
        assertEquals(1 to 1, D3dBurstFrameStore.computeScaledDimensions(0, -5, maxEdgePx = 1440))
    }

    @Test
    fun `deleteFramesDir removes only target recipe directory`() {
        val tempRoot = Files.createTempDirectory("aayna_d3d_test").toFile()
        try {
            val targetFile = D3dBurstFrameStore.frameFileFor(tempRoot, "shot-a", 0)
            targetFile.parentFile?.mkdirs()
            targetFile.writeText("frame0")

            val otherFile = D3dBurstFrameStore.frameFileFor(tempRoot, "shot-b", 0)
            otherFile.parentFile?.mkdirs()
            otherFile.writeText("frame0")

            assertTrue(D3dBurstFrameStore.deleteFramesDir(tempRoot, "shot-a"))
            assertFalse(D3dBurstFrameStore.framesDirFor(tempRoot, "shot-a").exists())
            assertTrue(otherFile.exists())
            assertFalse(D3dBurstFrameStore.deleteFramesDir(tempRoot, "shot-a"))
        } finally {
            tempRoot.deleteRecursively()
        }
    }
}
