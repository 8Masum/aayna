package com.themasum.aayna.feature.viewfinder

import com.themasum.aayna.core.model.CameraCatalog
import com.themasum.aayna.core.model.SkinCatalog
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProccdRulerAndSkinManifestTest {
    @Test
    fun rulerSpecs_have21Or41NotchesAndRoundTripCleanly() {
        val evSpec = ProccdRulerMath.specFor(RulerParameter.EV)
        assertEquals(41, evSpec.notchCount)
        assertEquals(20, ProccdRulerMath.valueToNotchIndex(evSpec, 0f))
        assertEquals(0.0f, ProccdRulerMath.notchIndexToValue(evSpec, 20), 0.001f)
        assertEquals(0.5f, ProccdRulerMath.stepByNotches(evSpec, 0f, 5), 0.001f)

        val warmthSpec = ProccdRulerMath.specFor(RulerParameter.WARMTH)
        assertEquals(41, warmthSpec.notchCount)

        for (param in listOf(
            RulerParameter.VIGNETTE,
            RulerParameter.BLOOM,
            RulerParameter.GRAIN,
            RulerParameter.LEAK
        )) {
            val spec = ProccdRulerMath.specFor(param)
            assertEquals(21, spec.notchCount)
            assertEquals(10, ProccdRulerMath.valueToNotchIndex(spec, 0.5f))
            assertEquals(0.5f, ProccdRulerMath.notchIndexToValue(spec, 10), 0.001f)
        }
    }

    @Test
    fun applyRulerValue_updatesGrainAndOpticalParametersWithoutClobberingOthers() {
        val base = ViewfinderUiState()
        val withGrain = ViewfinderStateReducer.applyRulerValue(base, RulerParameter.GRAIN, 0.72f)
        assertEquals(0.72f, ViewfinderStateReducer.currentRulerValue(withGrain, RulerParameter.GRAIN), 0.001f)
        val effective = ViewfinderStateReducer.effectiveProfileForRender(withGrain)
        assertEquals(0.72f, effective.grain.lumaStrength, 0.001f)

        val withEv = ViewfinderStateReducer.applyRulerValue(withGrain, RulerParameter.EV, -1.5f)
        assertEquals(-1.5f, withEv.opticalSettings.evCompensation, 0.001f)
        assertEquals(0.72f, withEv.grainStrengthOverride ?: 0f, 0.001f)
    }

    @Test
    fun skinManifestOnDisk_parsesAll24SkinsAnd84CameraIcons() {
        val candidates =
            listOf(
                File("src/main/assets/skins/skin_manifest.json"),
                File("feature/viewfinder/src/main/assets/skins/skin_manifest.json")
            )
        val manifestFile = candidates.firstOrNull { it.exists() }
        assertNotNull("skin_manifest.json not found on disk", manifestFile)

        val manifest = SkinManifestParser.parse(manifestFile!!.readText())
        assertEquals(0.4119f, manifest.proportions.cameraScreenHMidLine, 0.0001f)
        assertEquals(0.6558f, manifest.proportions.cameraScreenContainerRatio, 0.0001f)
        assertEquals(0.8411f, manifest.proportions.viewportHeightRatio, 0.0001f)
        assertEquals(103, manifest.proportions.bezelCutoutLeftPx)
        assertEquals(28, manifest.proportions.bezelCutoutTopPx)
        assertEquals(797, manifest.proportions.bezelCutoutRightPx)
        assertEquals(953, manifest.proportions.bezelCutoutBottomPx)

        assertEquals(24, manifest.skins.size)
        for (skin in SkinCatalog.all) {
            assertTrue("Missing manifest entry for ${skin.skinId}", manifest.skins.containsKey(skin.skinId))
        }

        assertEquals(84, manifest.cameraIcons.size)
        for (camera in CameraCatalog.all) {
            assertTrue("Missing camera icon in manifest for ${camera.id}", manifest.cameraIcons.containsKey(camera.id))
        }
        assertTrue(manifest.cameraIcons.containsKey("carousel"))
    }
}
