package com.themasum.aayna.core.model

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraCatalogTest {
    @Test
    fun `catalog has 6 unique cameras with unique codenames`() {
        val all = CameraCatalog.all
        assertEquals(6, all.size)
        assertEquals(6, all.map { it.id }.toSet().size)
        assertEquals(6, all.map { it.codeName }.toSet().size)
        assertEquals(6, all.map { it.lutAsset }.toSet().size)
        assertEquals(6, all.map { it.skin.skinId }.toSet().size)
    }

    @Test
    fun `catalog lutAsset paths match expected cube files`() {
        val expectedAssets =
            setOf(
                "luts/dc_04_ccd.cube",
                "luts/nx_07_nocturne.cube",
                "luts/d_98_disposable.cube",
                "luts/lomo_94_grunge.cube",
                "luts/rf_77_classic.cube",
                "luts/inst_m_instant.cube"
            )
        assertEquals(expectedAssets, CameraCatalog.all.map { it.lutAsset }.toSet())

        val glAssetsDir = File("../gl/src/main/assets")
        if (glAssetsDir.exists()) {
            for (profile in CameraCatalog.all) {
                val cubeFile = File(glAssetsDir, profile.lutAsset)
                assertTrue("Missing LUT asset: ${cubeFile.path}", cubeFile.exists())
            }
        }
    }

    @Test
    fun `findById and findByIdOrDefault resolve properly`() {
        assertEquals("DC-04", CameraCatalog.findById("dc_04_silver_pulse")?.codeName)
        assertEquals("INST-M", CameraCatalog.findById("inst_m_instant")?.codeName)
        assertEquals(CameraCatalog.defaultCamera, CameraCatalog.findByIdOrDefault("unknown_id"))
    }

    @Test
    fun `computeTargetDimensions guarantees even dimensions and respects glMaxTextureSize`() {
        for (preset in ResolutionPreset.entries) {
            for (aspect in AspectRatioPreset.entries) {
                val (w4k, h4k) = preset.computeTargetDimensions(aspect, 4096)
                assertEquals("Width must be even for $preset $aspect", 0, w4k % 2)
                assertEquals("Height must be even for $preset $aspect", 0, h4k % 2)
                assertTrue(maxOf(w4k, h4k) <= 4096)

                val (w2k, h2k) = preset.computeTargetDimensions(aspect, 2048)
                assertEquals(0, w2k % 2)
                assertEquals(0, h2k % 2)
                assertTrue(maxOf(w2k, h2k) <= 2048)

                val (w8k, h8k) = preset.computeTargetDimensions(aspect, 8192)
                assertTrue(maxOf(w8k, h8k) <= ResolutionPreset.MAX_FBO_EDGE_PX)
            }
        }
        assertEquals(
            2048 to 1536,
            ResolutionPreset.CCD_3_2MP.computeTargetDimensions(AspectRatioPreset.RATIO_4_3, 4096)
        )
        assertEquals(
            2560 to 1920,
            ResolutionPreset.DIGICAM_5MP.computeTargetDimensions(AspectRatioPreset.RATIO_4_3, 4096)
        )
        assertEquals(
            3264 to 2448,
            ResolutionPreset.VINTAGE_8MP.computeTargetDimensions(AspectRatioPreset.RATIO_4_3, 4096)
        )
    }

    @Test
    fun `stamp and grunge meta json files deserialize cleanly`() {
        val glAssetsDir = File("../gl/src/main/assets")
        if (glAssetsDir.exists()) {
            val stampMetaFile = File(glAssetsDir, "stamps/stamp_atlas_meta.json")
            val stampMeta = AaynaJson.json.decodeFromString(StampAtlasMeta.serializer(), stampMetaFile.readText())
            assertEquals(512, stampMeta.atlasWidth)
            assertEquals(DateStampFormatter.ATLAS_ALLOWED_CHARS, stampMeta.chars)
            assertTrue(stampMeta.atlases.containsKey("dot_matrix"))

            val grungeMetaFile = File(glAssetsDir, "grunge/grunge_meta.json")
            val grungeMeta = AaynaJson.json.decodeFromString(GrungeAtlasMeta.serializer(), grungeMetaFile.readText())
            assertNotNull(grungeMeta.frames["instant_frame"])
            assertNotNull(grungeMeta.frames["film_border_35mm"])
        }
    }
}
