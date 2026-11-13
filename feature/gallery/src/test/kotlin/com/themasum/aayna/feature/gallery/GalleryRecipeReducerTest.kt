package com.themasum.aayna.feature.gallery

import com.themasum.aayna.core.model.AspectRatioPreset
import com.themasum.aayna.core.model.CameraCatalog
import com.themasum.aayna.core.model.FlashMode
import com.themasum.aayna.core.model.FrameBorderStyle
import com.themasum.aayna.core.model.LensAttachment
import com.themasum.aayna.core.model.ResolutionPreset
import com.themasum.aayna.core.model.StampFormat
import com.themasum.aayna.core.model.StampStyle
import com.themasum.aayna.core.model.UserSettingsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class GalleryRecipeReducerTest {
    private val baseRecipe =
        GalleryRecipeReducer.createImportedRecipe(
            cameraId = CameraCatalog.dc04SilverPulse.id,
            settings = UserSettingsSnapshot(),
            capturedAtEpochMs = 1_700_000_000_000L,
            randomSeed = 42L,
            recipeId = "test_recipe_1"
        )

    @Test
    fun `swapCameraProfile updates cameraId and eraYear while preserving custom overrides`() {
        val tweaked =
            GalleryRecipeReducer.updateEv(baseRecipe, 1.25f).let {
                GalleryRecipeReducer.updateLightLeak(it, 0.7f)
            }
        val swapped =
            GalleryRecipeReducer.swapCameraProfile(
                recipe = tweaked,
                newProfile = CameraCatalog.lomo94Grunge,
                applyProfileDefaults = false
            )

        assertEquals(CameraCatalog.lomo94Grunge.id, swapped.cameraId)
        assertEquals(1994, swapped.stampConfig.eraYear)
        assertEquals(1.25f, swapped.opticalSettings.evCompensation, 0.0001f)
        assertEquals(0.7f, swapped.opticalSettings.lightLeakIntensity, 0.0001f)
    }

    @Test
    fun `swapCameraProfile with applyProfileDefaults resets optical defaults`() {
        val tweaked = GalleryRecipeReducer.updateAspectRatio(baseRecipe, AspectRatioPreset.RATIO_16_9)
        val swapped =
            GalleryRecipeReducer.swapCameraProfile(
                recipe = tweaked,
                newProfile = CameraCatalog.instMInstant,
                applyProfileDefaults = true
            )

        assertEquals(CameraCatalog.instMInstant.id, swapped.cameraId)
        assertEquals(AspectRatioPreset.RATIO_1_1, swapped.opticalSettings.aspectRatio)
        assertEquals(FrameBorderStyle.INSTANT_PRINT, swapped.opticalSettings.frameStyle)
    }

    @Test
    fun `shuffleRandomSeed always produces a different seed`() {
        val sameCandidate = GalleryRecipeReducer.shuffleRandomSeed(baseRecipe, candidateSeed = 42L)
        assertNotEquals(42L, sameCandidate.randomSeed)

        val newCandidate = GalleryRecipeReducer.shuffleRandomSeed(baseRecipe, candidateSeed = 999L)
        assertEquals(999L, newCandidate.randomSeed)
    }

    @Test
    fun `optical parameter updates clamp to valid bounds`() {
        val overEv = GalleryRecipeReducer.updateEv(baseRecipe, 5.0f)
        assertEquals(2.0f, overEv.opticalSettings.evCompensation, 0.0001f)
        val underEv = GalleryRecipeReducer.updateEv(baseRecipe, -5.0f)
        assertEquals(-2.0f, underEv.opticalSettings.evCompensation, 0.0001f)

        val overWarmth = GalleryRecipeReducer.updateWarmth(baseRecipe, 3.0f)
        assertEquals(1.0f, overWarmth.opticalSettings.warmthShift, 0.0001f)
        val underWarmth = GalleryRecipeReducer.updateWarmth(baseRecipe, -3.0f)
        assertEquals(-1.0f, underWarmth.opticalSettings.warmthShift, 0.0001f)

        val overBloom = GalleryRecipeReducer.updateBloom(baseRecipe, 1.5f)
        assertEquals(1.0f, overBloom.opticalSettings.bloomIntensity, 0.0001f)
        val underVignette = GalleryRecipeReducer.updateVignette(baseRecipe, -0.4f)
        assertEquals(0.0f, underVignette.opticalSettings.vignetteIntensity, 0.0001f)

        val overLeak = GalleryRecipeReducer.updateLightLeak(baseRecipe, 2.0f)
        assertEquals(1.0f, overLeak.opticalSettings.lightLeakIntensity, 0.0001f)
        val underDust = GalleryRecipeReducer.updateDust(baseRecipe, -1.0f)
        assertEquals(0.0f, underDust.opticalSettings.dustIntensity, 0.0001f)
    }

    @Test
    fun `toggleFlashSim and stamp or frame overrides update cleanly`() {
        val flashOn = GalleryRecipeReducer.toggleFlashSim(baseRecipe)
        assertEquals(FlashMode.OPTICAL_FLASH_SIM, flashOn.opticalSettings.flashMode)
        val flashOff = GalleryRecipeReducer.toggleFlashSim(flashOn)
        assertEquals(FlashMode.OFF, flashOff.opticalSettings.flashMode)

        val updated =
            GalleryRecipeReducer.updateLensAttachment(flashOff, LensAttachment.STAR_4PT).let {
                GalleryRecipeReducer.updateAspectRatio(it, AspectRatioPreset.RATIO_3_2)
            }.let {
                GalleryRecipeReducer.updateFrameStyle(it, FrameBorderStyle.FILM_35MM)
            }.let {
                GalleryRecipeReducer.updateResolutionPreset(it, ResolutionPreset.VINTAGE_8MP)
            }.let {
                GalleryRecipeReducer.updateStampStyle(it, StampStyle.CLASSIC_RED)
            }.let {
                GalleryRecipeReducer.updateStampFormat(it, StampFormat.CUSTOM)
            }.let {
                GalleryRecipeReducer.updateStampEraYearOffset(it, true)
            }.let {
                GalleryRecipeReducer.updateStampCustomText(it, "AAYNA 2004")
            }

        assertEquals(LensAttachment.STAR_4PT, updated.opticalSettings.lensAttachment)
        assertEquals(AspectRatioPreset.RATIO_3_2, updated.opticalSettings.aspectRatio)
        assertEquals(FrameBorderStyle.FILM_35MM, updated.opticalSettings.frameStyle)
        assertEquals(ResolutionPreset.VINTAGE_8MP, updated.resolutionPreset)
        assertEquals(StampStyle.CLASSIC_RED, updated.stampConfig.style)
        assertEquals(StampFormat.CUSTOM, updated.stampConfig.format)
        assertEquals(true, updated.stampConfig.useEraYearOffset)
        assertEquals("AAYNA 2004", updated.stampConfig.customText)
    }
}
