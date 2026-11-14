package com.themasum.aayna.core.model

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraCatalogTest {
    @Test
    fun `catalog has all 83 cameras including all 55 ProCCD CameraId models and Z30 default`() {
        val all = CameraCatalog.all
        assertEquals(83, all.size)
        assertEquals(CameraCatalog.z30, CameraCatalog.defaultCamera)
        assertEquals("Z30", CameraCatalog.defaultCamera.codeName)
        assertEquals(83, all.map { it.id }.toSet().size)
        assertEquals(83, all.map { it.codeName }.toSet().size)
        assertEquals(83, all.map { it.lutAssetPath }.toSet().size)
        assertEquals(83, all.map { it.iconAssetPath }.toSet().size)

        val expectedProCcd55 =
            setOf(
                "sx70", "2000s", "big_shot", "dispo", "dispo_k", "v2014", "booth", "inst", "inst_w",
                "xs10", "y2k", "action", "nx", "hdc", "m10", "a2300", "a40", "a620", "blue", "gold",
                "gr", "c123", "cx733", "dc210", "dcr", "f100", "f410", "f71", "fe402", "grf", "h10",
                "i10", "ixus210", "ixus70", "ixus95", "lx5", "m532", "mg330", "n23", "original",
                "original2", "original_r", "rfc", "sipic", "sl400", "t90", "u300", "u400", "u7040",
                "wg1", "xg", "xs3", "z30", "z50", "zr1500"
            )
        assertEquals(55, expectedProCcd55.size)
        assertEquals(expectedProCcd55, CameraCatalog.proCcd55Ids)
        for (id in expectedProCcd55) {
            assertNotNull("Missing ProCCD camera id $id", CameraCatalog.findById(id))
        }

        for (profile in all) {
            assertTrue("Expected luts_2d/ PNG for ${profile.id}", profile.lutAssetPath.startsWith("luts_2d/"))
            assertTrue("Expected .png suffix for ${profile.id}", profile.lutAssetPath.endsWith(".png"))
            assertEquals(profile.lutAssetPath, profile.lutAsset)
            assertTrue("Missing description for ${profile.id}", profile.sampleDescription.isNotBlank())
            assertNotNull("Unknown defaultSkinId ${profile.defaultSkinId}", SkinCatalog.findById(profile.defaultSkinId))
            assertTrue(
                "linkedSkinIds must include defaultSkinId for ${profile.id}",
                profile.defaultSkinId in profile.linkedSkinIds
            )
            profile.linkedSkinIds.forEach { skinId ->
                assertNotNull("Unknown linked skin $skinId on ${profile.id}", SkinCatalog.findById(skinId))
            }
        }
    }

    @Test
    fun `camera categories and specialty flags match roster`() {
        for (category in CameraCategory.entries) {
            assertTrue("Expected cameras for $category", CameraCatalog.byCategory(category).isNotEmpty())
        }

        val videoIds = CameraCatalog.all.filter { it.supportsVideo }.map { it.id }.toSet()
        assertEquals(
            setOf("dcr", "v80", "ccd801", "m20", "dv", "nt16", "d3d", "action", "hdc", "mg330"),
            videoIds
        )

        val d3dCameras = CameraCatalog.all.filter { it.isD3dBurst }
        assertEquals(listOf(CameraCatalog.d3d), d3dCameras)
        assertEquals(16, CameraCatalog.specialSeries().size)
        assertEquals(67, CameraCatalog.classicSeries().size)
    }

    @Test
    fun `all 83 2D LUT PNGs and 6 cube fallback assets exist on disk`() {
        val glAssetsDir = File("../gl/src/main/assets")
        if (glAssetsDir.exists()) {
            for (profile in CameraCatalog.all) {
                val lutPng = File(glAssetsDir, profile.lutAssetPath)
                assertTrue("Missing 2D LUT PNG: ${lutPng.path}", lutPng.exists())
                profile.lutCubeAssetPath?.let { cubePath ->
                    val cubeFile = File(glAssetsDir, cubePath)
                    assertTrue("Missing LUT cube fallback: ${cubeFile.path}", cubeFile.exists())
                }
            }
        }
        val skinsDir = File("../../feature/viewfinder/src/main/assets")
        if (skinsDir.exists()) {
            for (profile in CameraCatalog.all) {
                val iconFile = File(skinsDir, profile.iconAssetPath)
                assertTrue("Missing camera icon: ${iconFile.path}", iconFile.exists())
            }
            assertTrue(File(skinsDir, CameraCatalog.CAROUSEL_BADGE_ICON_ASSET).exists())
            for (skin in SkinCatalog.all) {
                assertTrue(
                    "Missing chassis ${skin.chassisBgAsset}",
                    File(skinsDir, skin.chassisBgAsset).exists()
                )
                assertTrue(
                    "Missing bezel ${skin.bezelOverlayAsset}",
                    File(skinsDir, skin.bezelOverlayAsset).exists()
                )
                assertTrue(
                    "Missing shutter ${skin.shutterButtonAsset}",
                    File(skinsDir, skin.shutterButtonAsset).exists()
                )
            }
        }
    }

    @Test
    fun `findById legacy aliases codeNames and findByIdOrDefault resolve properly`() {
        assertEquals("Z30", CameraCatalog.findById("z30")?.codeName)
        assertEquals("Z30", CameraCatalog.findById("dc_04_silver_pulse")?.codeName)
        assertEquals("M532", CameraCatalog.findById("nx_07_nocturne")?.codeName)
        assertEquals("Quik", CameraCatalog.findById("d_98_disposable")?.codeName)
        assertEquals("Lomo", CameraCatalog.findById("lomo_94_grunge")?.codeName)
        assertEquals("M7", CameraCatalog.findById("rf_77_classic")?.codeName)
        assertEquals("Inst M", CameraCatalog.findById("inst_m_instant")?.codeName)
        assertEquals("SX70", CameraCatalog.findById("SX70")?.codeName)
        assertEquals("2000S", CameraCatalog.findById("2000S")?.codeName)
        assertEquals("BIG SHOT", CameraCatalog.findById("BIG_SHOT")?.codeName)
        assertEquals("ORIGINAL R", CameraCatalog.findById("ORIGINAL_R")?.codeName)
        assertEquals("CCD-801", CameraCatalog.findByCodeName("ccd-801")?.codeName)
        assertNull(CameraCatalog.findById("unknown_id"))
        assertEquals(CameraCatalog.defaultCamera, CameraCatalog.findByIdOrDefault("unknown_id"))
    }

    @Test
    fun `skin catalog has 24 layered skins spanning all categories`() {
        val skins = SkinCatalog.all
        assertEquals(24, skins.size)
        assertEquals(24, skins.map { it.skinId }.toSet().size)
        assertEquals(24, skins.map { it.chassisBgAsset }.toSet().size)
        assertEquals(24, skins.map { it.bezelOverlayAsset }.toSet().size)
        assertEquals(24, skins.map { it.shutterButtonAsset }.toSet().size)

        for (category in CameraCategory.entries) {
            assertTrue(
                "Category $category must have at least one skin",
                SkinCatalog.skinsForCategory(category).isNotEmpty()
            )
        }
        assertTrue(SkinCatalog.silverBrushed.isLightBody)
        assertTrue(SkinCatalog.bloom2000s.isLightBody)
        assertFalse(SkinCatalog.charcoalMetal.isLightBody)
        assertFalse(SkinCatalog.slrNx.isLightBody)

        val manifest = CameraCatalog.buildSkinManifest()
        assertEquals(83, manifest.cameraDefaultSkins.size)
        assertEquals(83, manifest.cameraLinkedSkins.size)
        val roundTripped = AaynaJson.decodeSkinManifest(AaynaJson.encodeSkinManifest(manifest))
        assertEquals(manifest, roundTripped)
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
            val boothFrame = grungeMeta.frames["photo_booth_4strip_frame"]
            assertNotNull(boothFrame)
            assertEquals(4, boothFrame?.panels?.size)
        }
    }
}
