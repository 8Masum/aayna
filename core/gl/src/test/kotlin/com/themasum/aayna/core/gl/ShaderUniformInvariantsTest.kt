package com.themasum.aayna.core.gl

import com.themasum.aayna.core.model.CameraCatalog
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShaderUniformInvariantsTest {
    @Test
    fun shaderUniformNameRegistries_matchAllDeclaredUniformsInGlShaderSources() {
        assertUniformsMatch(
            label = "Pass 1 (OES)",
            shaderSource = stripInactiveOesBranch(
                GlShaderSources.buildPass1FragmentShader(useOesInput = true),
                useOes = true
            ),
            expectedNames = GlShaderSources.PASS1_UNIFORM_NAMES
        )
        assertUniformsMatch(
            label = "Pass 1 (2D)",
            shaderSource = stripInactiveOesBranch(
                GlShaderSources.buildPass1FragmentShader(useOesInput = false),
                useOes = false
            ),
            expectedNames = GlShaderSources.PASS1_UNIFORM_NAMES
        )
        assertUniformsMatch(
            label = "Pass 2A Threshold",
            shaderSource = GlShaderSources.PASS2_THRESHOLD_FRAGMENT_SHADER,
            expectedNames = GlShaderSources.PASS2_THRESHOLD_UNIFORM_NAMES
        )
        assertUniformsMatch(
            label = "Pass 2B Blur",
            shaderSource = GlShaderSources.PASS2_BLUR_FRAGMENT_SHADER,
            expectedNames = GlShaderSources.PASS2_BLUR_UNIFORM_NAMES
        )
        assertUniformsMatch(
            label = "Pass 2C Star",
            shaderSource = GlShaderSources.PASS2_STAR_FRAGMENT_SHADER,
            expectedNames = GlShaderSources.PASS2_STAR_UNIFORM_NAMES
        )
        assertUniformsMatch(
            label = "Pass 3 LUT Composite",
            shaderSource = GlShaderSources.PASS3_LUT_COMPOSITE_FRAGMENT_SHADER,
            expectedNames = GlShaderSources.PASS3_UNIFORM_NAMES
        )
        assertUniformsMatch(
            label = "Pass 4 Final",
            shaderSource = GlShaderSources.PASS4_FINAL_FRAGMENT_SHADER,
            expectedNames = GlShaderSources.PASS4_UNIFORM_NAMES
        )
        assertUniformsMatch(
            label = "Pass Overlay Frame",
            shaderSource = GlShaderSources.PASS_OVERLAY_FRAME_FRAGMENT_SHADER,
            expectedNames = GlShaderSources.PASS_OVERLAY_UNIFORM_NAMES
        )
    }

    @Test
    fun aaynaFourPassEngine_cachesAllUniformsAtLinkTimeWithZeroPerFrameLookups() {
        val engineFile = resolveSourceFile("AaynaFourPassEngine.kt")
        val text = engineFile.readText(Charsets.UTF_8)

        val glGetUniformMatches = Regex("""glGetUniformLocation\s*\(""").findAll(text).toList()
        // 1 inside uniformLoc + 2 (direct + [0] fallback) inside uniformArrayLoc
        assertEquals(
            "glGetUniformLocation must only appear inside link-time helpers uniformLoc/uniformArrayLoc",
            3,
            glGetUniformMatches.size
        )

        val allExpectedUniforms =
            (
                GlShaderSources.PASS1_UNIFORM_NAMES +
                    GlShaderSources.PASS2_THRESHOLD_UNIFORM_NAMES +
                    GlShaderSources.PASS2_BLUR_UNIFORM_NAMES +
                    GlShaderSources.PASS2_STAR_UNIFORM_NAMES +
                    GlShaderSources.PASS3_UNIFORM_NAMES +
                    GlShaderSources.PASS4_UNIFORM_NAMES +
                    GlShaderSources.PASS_OVERLAY_UNIFORM_NAMES
                ).toSet()

        for (uniformName in allExpectedUniforms) {
            assertTrue(
                "Expected link-time lookup for uniform '$uniformName' in AaynaFourPassEngine.kt",
                text.contains("\"$uniformName\"")
            )
        }
    }

    @Test
    fun assetMetadataManifests_referenceAll83Luts11GrungeTexturesAnd4StampAtlases() {
        val assetsDir = resolveAssetsDirectory()

        val lutsMetaText = File(assetsDir, "luts_2d/luts_2d_meta.json").readText(Charsets.UTF_8)
        assertTrue(lutsMetaText.contains("\"LUT_2D_64x64x8x8\""))
        assertTrue(lutsMetaText.contains("\"cubeSize\": 64"))
        assertTrue(lutsMetaText.contains("\"tilesX\": 8"))
        assertTrue(lutsMetaText.contains("\"tilesY\": 8"))
        assertEquals(83, CameraCatalog.all.size)
        for (camera in CameraCatalog.all) {
            val lutFile = File(assetsDir, camera.lutAssetPath)
            assertTrue("Missing 2D LUT asset for ${camera.id}: ${lutFile.path}", lutFile.isFile)
            assertTrue(
                "luts_2d_meta.json missing entry for ${camera.lutAssetPath}",
                lutsMetaText.contains("\"${camera.lutAssetPath}\"")
            )
        }

        val grungeMetaText = File(assetsDir, "grunge/grunge_meta.json").readText(Charsets.UTF_8)
        val expectedGrungeFiles =
            listOf(
                "grunge/light_leak_warm.png",
                "grunge/light_leak_edge.png",
                "grunge/dust_scratches.png",
                "grunge/dv_scanlines.png",
                "grunge/instant_frame.png",
                "grunge/instant_wide_frame.png",
                "grunge/polaroid_square_frame.png",
                "grunge/film_border_35mm.png",
                "grunge/cine_16mm_frame.png",
                "grunge/photo_booth_4strip_frame.png",
                "grunge/peephole_dispo_mask.png"
            )
        for (relPath in expectedGrungeFiles) {
            assertTrue("grunge_meta.json missing $relPath", grungeMetaText.contains("\"$relPath\""))
            assertTrue("Missing grunge asset $relPath", File(assetsDir, relPath).isFile)
        }
        assertTrue("photo_booth_4strip_frame must declare panels", grungeMetaText.contains("\"panels\""))

        val stampMetaText = File(assetsDir, "stamps/stamp_atlas_meta.json").readText(Charsets.UTF_8)
        val expectedStampAtlases =
            listOf(
                "stamps/dot_matrix_atlas.png",
                "stamps/seven_segment_atlas.png",
                "stamps/vcr_osd_atlas.png",
                "stamps/pixel_atlas.png"
            )
        for (relPath in expectedStampAtlases) {
            assertTrue("stamp_atlas_meta.json missing $relPath", stampMetaText.contains("\"$relPath\""))
            assertTrue("Missing stamp atlas $relPath", File(assetsDir, relPath).isFile)
        }
    }

    private fun stripInactiveOesBranch(shaderSource: String, useOes: Boolean): String {
        val branchRegex =
            Regex(
                """#if USE_OES_INPUT\s*\r?\n(.*?)\r?\n#else\s*\r?\n(.*?)\r?\n#endif""",
                RegexOption.DOT_MATCHES_ALL
            )
        return branchRegex.replace(shaderSource) { match ->
            if (useOes) match.groupValues[1] else match.groupValues[2]
        }
    }

    private fun assertUniformsMatch(label: String, shaderSource: String, expectedNames: List<String>) {
        val declared =
            Regex("""^\s*uniform\s+\w+\s+([A-Za-z0-9_]+)(?:\[\d+])?\s*;""", RegexOption.MULTILINE)
                .findAll(shaderSource)
                .map { it.groupValues[1] }
                .toList()
        assertEquals(
            "Uniform declarations mismatch for $label",
            expectedNames.toSet(),
            declared.toSet()
        )
        assertEquals(
            "Duplicate uniform declarations detected for $label",
            declared.size,
            declared.toSet().size
        )
    }

    private fun resolveSourceFile(fileName: String): File {
        val direct = File("src/main/kotlin/com/themasum/aayna/core/gl/$fileName")
        if (direct.isFile) return direct
        return File("core/gl/src/main/kotlin/com/themasum/aayna/core/gl/$fileName")
    }

    private fun resolveAssetsDirectory(): File {
        val direct = File("src/main/assets")
        if (direct.isDirectory) return direct
        return File("core/gl/src/main/assets")
    }
}
