package com.themasum.aayna.feature.gallery

import com.themasum.aayna.core.model.AspectRatioPreset
import com.themasum.aayna.core.model.CameraCatalog
import com.themasum.aayna.core.model.FlashMode
import com.themasum.aayna.core.model.FrameBorderStyle
import com.themasum.aayna.core.model.LensAttachment
import com.themasum.aayna.core.model.MediaType
import com.themasum.aayna.core.model.ResolutionPreset
import com.themasum.aayna.core.model.StampFontStyle
import com.themasum.aayna.core.model.StampFormat
import com.themasum.aayna.core.model.StampPosition
import com.themasum.aayna.core.model.StampStyle
import com.themasum.aayna.core.model.UserSettingsSnapshot
import com.themasum.aayna.core.model.WhiteBalancePreset
import com.themasum.aayna.core.storage.db.NegativeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryRecipeReducerTest {
    private val baseRecipe =
        GalleryRecipeReducer.createImportedRecipe(
            cameraId = CameraCatalog.z30.id,
            settings = UserSettingsSnapshot(),
            capturedAtEpochMs = 1_700_000_000_000L,
            randomSeed = 42L,
            recipeId = "test_recipe_1"
        )

    @Test
    fun `studio has all 6 ProCCD tabs in canonical order`() {
        assertEquals(
            listOf(
                StudioEditorTab.CAMERA,
                StudioEditorTab.ADJUST,
                StudioEditorTab.FILM_FX,
                StudioEditorTab.FRAME_LENS,
                StudioEditorTab.DATE_STAMP,
                StudioEditorTab.CROP_ROTATE
            ),
            StudioEditorTab.entries
        )
    }

    @Test
    fun `swapCameraProfile applies cleanly across all 83 cameras and category shelf groups`() {
        assertEquals(83, CameraCatalog.all.size)
        val groupedUnion =
            listOf(
                CameraShelfGroup.CCD,
                CameraShelfGroup.FILM,
                CameraShelfGroup.INSTANT,
                CameraShelfGroup.CAMCORDER,
                CameraShelfGroup.SPECIAL
            ).flatMap { GalleryCameraShelfHelper.camerasForGroup(it) }
                .map { it.id }
                .toSet()
        assertEquals(CameraCatalog.all.map { it.id }.toSet(), groupedUnion)

        CameraCatalog.all.forEach { profile ->
            val swapped =
                GalleryRecipeReducer.swapCameraProfile(
                    recipe = baseRecipe,
                    newProfile = profile,
                    applyProfileDefaults = true
                )
            assertEquals(profile.id, swapped.cameraId)
            assertEquals(profile.defaultSkinId, swapped.skinId)
            assertEquals(profile.eraYearOffset, swapped.stampConfig.eraYear)
            assertEquals(profile.defaultAspect, swapped.opticalSettings.aspectRatio)
            assertEquals(profile.defaultFrame, swapped.opticalSettings.frameStyle)
        }
    }

    @Test
    fun `swapCameraProfile without applyProfileDefaults preserves custom overrides`() {
        val tweaked =
            GalleryRecipeReducer.updateEv(baseRecipe, 1.25f).let {
                GalleryRecipeReducer.updateLightLeak(it, 0.7f)
            }
        val swapped =
            GalleryRecipeReducer.swapCameraProfile(
                recipe = tweaked,
                newProfile = CameraCatalog.lomo,
                applyProfileDefaults = false
            )

        assertEquals(CameraCatalog.lomo.id, swapped.cameraId)
        assertEquals(CameraCatalog.lomo.eraYearOffset, swapped.stampConfig.eraYear)
        assertEquals(1.25f, swapped.opticalSettings.evCompensation, 0.0001f)
        assertEquals(0.7f, swapped.opticalSettings.lightLeakIntensity, 0.0001f)
    }

    @Test
    fun `shuffleRandomSeed always produces a different seed`() {
        val sameCandidate = GalleryRecipeReducer.shuffleRandomSeed(baseRecipe, candidateSeed = 42L)
        assertNotEquals(42L, sameCandidate.randomSeed)

        val newCandidate = GalleryRecipeReducer.shuffleRandomSeed(baseRecipe, candidateSeed = 999L)
        assertEquals(999L, newCandidate.randomSeed)
    }

    @Test
    fun `adjust and film fx parameter updates clamp to valid bounds`() {
        val overEv = GalleryRecipeReducer.updateEv(baseRecipe, 5.0f)
        assertEquals(2.0f, overEv.opticalSettings.evCompensation, 0.0001f)
        val underEv = GalleryRecipeReducer.updateEv(baseRecipe, -5.0f)
        assertEquals(-2.0f, underEv.opticalSettings.evCompensation, 0.0001f)

        val overHighlights = GalleryRecipeReducer.updateHighlights(baseRecipe, 3.0f)
        assertEquals(1.0f, overHighlights.opticalSettings.highlights, 0.0001f)
        val underShadows = GalleryRecipeReducer.updateShadows(baseRecipe, -3.0f)
        assertEquals(-1.0f, underShadows.opticalSettings.shadows, 0.0001f)

        val overSharpen = GalleryRecipeReducer.updateSharpen(baseRecipe, 2.0f)
        assertEquals(1.0f, overSharpen.opticalSettings.sharpen, 0.0001f)
        val underBeauty = GalleryRecipeReducer.updateBeautySoftness(baseRecipe, -0.5f)
        assertEquals(0.0f, underBeauty.opticalSettings.beautySoftness, 0.0001f)

        val wbUpdated = GalleryRecipeReducer.updateWhiteBalance(baseRecipe, WhiteBalancePreset.TUNGSTEN)
        assertEquals(WhiteBalancePreset.TUNGSTEN, wbUpdated.opticalSettings.whiteBalance)

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
    fun `crop and straighten updates clamp to -15 to +15 degrees and support all presets`() {
        val maxTilt = GalleryRecipeReducer.updateStraightenDegrees(baseRecipe, 42.5f)
        assertEquals(15.0f, maxTilt.opticalSettings.straightenDegrees, 0.0001f)

        val minTilt = GalleryRecipeReducer.updateStraightenDegrees(baseRecipe, -28.0f)
        assertEquals(-15.0f, minTilt.opticalSettings.straightenDegrees, 0.0001f)

        val cropOriginal = GalleryRecipeReducer.updateCropPreset(baseRecipe, StudioCropPreset.ORIGINAL)
        assertNull(cropOriginal.opticalSettings.cropAspectRatio)

        val crop1x1 = GalleryRecipeReducer.updateCropPreset(baseRecipe, StudioCropPreset.RATIO_1_1)
        assertEquals(AspectRatioPreset.RATIO_1_1, crop1x1.opticalSettings.cropAspectRatio)

        val crop3x4 = GalleryRecipeReducer.updateCropPreset(baseRecipe, StudioCropPreset.RATIO_3_4)
        assertEquals(AspectRatioPreset.RATIO_4_3, crop3x4.opticalSettings.cropAspectRatio)

        val crop9x16 = GalleryRecipeReducer.updateCropPreset(baseRecipe, StudioCropPreset.RATIO_9_16)
        assertEquals(AspectRatioPreset.RATIO_16_9, crop9x16.opticalSettings.cropAspectRatio)
    }

    @Test
    fun `all 9 lenses 8 frames 5 stamp fonts 4 stamp positions and skipStamp params wire cleanly`() {
        assertEquals(9, LensAttachment.entries.size)
        assertEquals(8, FrameBorderStyle.entries.size)
        assertEquals(5, StampStyle.entries.size)
        assertEquals(5, StampFontStyle.entries.size)
        assertEquals(4, StampPosition.entries.size)

        val flashOn = GalleryRecipeReducer.toggleFlashSim(baseRecipe)
        assertEquals(FlashMode.OPTICAL_FLASH_SIM, flashOn.opticalSettings.flashMode)

        val updated =
            GalleryRecipeReducer.updateLensAttachment(flashOn, LensAttachment.KALEIDOSCOPE).let {
                GalleryRecipeReducer.updateFrameStyle(it, FrameBorderStyle.PHOTO_BOOTH_4STRIP)
            }.let {
                GalleryRecipeReducer.updateResolutionPreset(it, ResolutionPreset.VINTAGE_8MP)
            }.let {
                GalleryRecipeReducer.updateStampStyle(it, StampStyle.CLASSIC_RED)
            }.let {
                GalleryRecipeReducer.updateStampFontStyle(it, StampFontStyle.VCR_OSD)
            }.let {
                GalleryRecipeReducer.updateStampPosition(it, StampPosition.TOP_LEFT)
            }.let {
                GalleryRecipeReducer.updateStampGlow(it, 1.5f)
            }.let {
                GalleryRecipeReducer.updateStampFormat(it, StampFormat.CUSTOM)
            }.let {
                GalleryRecipeReducer.updateStampEraYearOffset(it, true)
            }.let {
                GalleryRecipeReducer.updateStampCustomText(it, "AAYNA 2004")
            }.let {
                GalleryRecipeReducer.updateCaption(it, "Tokyo night walk")
            }

        assertEquals(LensAttachment.KALEIDOSCOPE, updated.opticalSettings.lensAttachment)
        assertEquals(FrameBorderStyle.PHOTO_BOOTH_4STRIP, updated.opticalSettings.frameStyle)
        assertEquals(ResolutionPreset.VINTAGE_8MP, updated.resolutionPreset)
        assertEquals(StampStyle.CLASSIC_RED, updated.stampConfig.style)
        assertEquals(StampFontStyle.VCR_OSD, updated.stampConfig.fontStyle)
        assertEquals(StampPosition.TOP_LEFT, updated.stampConfig.position)
        assertEquals(1.0f, updated.stampConfig.glowStrength, 0.0001f)
        assertEquals(StampFormat.CUSTOM, updated.stampConfig.format)
        assertTrue(updated.stampConfig.useEraYearOffset)
        assertEquals("AAYNA 2004", updated.stampConfig.customText)
        assertEquals("Tokyo night walk", updated.caption)

        val holdParams =
            GalleryRecipeReducer.buildGlRenderParams(
                recipe = updated,
                profile = CameraCatalog.z30,
                stampText = "AAYNA 2004",
                grainBoost = 0.5f,
                isHoldOriginal = true
            )
        assertTrue(holdParams.isPreviewHoldOriginal)
        assertTrue(holdParams.skipStamp)
        assertEquals(0.7f, holdParams.fallbackGains.shaderNoiseBoost, 0.0001f)
    }

    @Test
    fun `gallery filter helper separates all rolls instant video d3d and trash cleanly`() {
        val instantStill =
            NegativeEntity(
                recipeId = "still_instant",
                cameraId = "z30",
                capturedAtEpochMs = 1000L,
                cleanImagePath = "/tmp/1.jpg",
                thumbnailPath = "/tmp/1t.jpg",
                recipeJson = "{}",
                rollId = null,
                mediaType = MediaType.STILL.name,
                isTrashed = false
            )
        val rollStill =
            NegativeEntity(
                recipeId = "still_roll",
                cameraId = "grf",
                capturedAtEpochMs = 2000L,
                cleanImagePath = "/tmp/2.jpg",
                thumbnailPath = "/tmp/2t.jpg",
                recipeJson = "{}",
                rollId = "roll_1",
                frameNumber = 1,
                mediaType = MediaType.STILL.name,
                isTrashed = false
            )
        val rollInstantCam =
            NegativeEntity(
                recipeId = "inst_roll",
                cameraId = "instm",
                capturedAtEpochMs = 3000L,
                cleanImagePath = "/tmp/3.jpg",
                thumbnailPath = "/tmp/3t.jpg",
                recipeJson = "{}",
                rollId = "roll_1",
                frameNumber = 2,
                mediaType = MediaType.STILL.name,
                isTrashed = false
            )
        val videoClip =
            NegativeEntity(
                recipeId = "vid_1",
                cameraId = "dcr",
                capturedAtEpochMs = 4000L,
                cleanImagePath = "",
                thumbnailPath = "/tmp/4t.jpg",
                recipeJson = "{}",
                mediaType = MediaType.VIDEO.name,
                videoDurationMs = 8000L,
                mediaUri = "content://media/video/1",
                isTrashed = false
            )
        val d3dBurst =
            NegativeEntity(
                recipeId = "d3d_1",
                cameraId = "d3d",
                capturedAtEpochMs = 5000L,
                cleanImagePath = "/tmp/5.jpg",
                thumbnailPath = "/tmp/5t.jpg",
                recipeJson = "{}",
                mediaType = MediaType.D3D_BURST.name,
                burstFrameCount = 4,
                isTrashed = false
            )
        val trashedItem =
            instantStill.copy(
                recipeId = "trashed_1",
                isTrashed = true,
                trashedAtEpochMs = 6000L
            )

        val active = listOf(instantStill, rollStill, rollInstantCam, videoClip, d3dBurst)
        val trashed = listOf(trashedItem)

        assertEquals(5, GalleryFilterHelper.filterNegatives(GalleryFilterTab.ALL, active, trashed).size)
        assertEquals(
            listOf("still_roll", "inst_roll"),
            GalleryFilterHelper.filterNegatives(GalleryFilterTab.ROLLS, active, trashed).map { it.recipeId }
        )
        assertEquals(
            listOf("still_instant", "inst_roll"),
            GalleryFilterHelper.filterNegatives(GalleryFilterTab.INSTANT, active, trashed).map { it.recipeId }
        )
        assertEquals(
            listOf("vid_1"),
            GalleryFilterHelper.filterNegatives(GalleryFilterTab.VIDEO, active, trashed).map { it.recipeId }
        )
        assertEquals(
            listOf("d3d_1"),
            GalleryFilterHelper.filterNegatives(GalleryFilterTab.D3D, active, trashed).map { it.recipeId }
        )
        assertEquals(
            listOf("trashed_1"),
            GalleryFilterHelper.filterNegatives(GalleryFilterTab.TRASH, active, trashed).map { it.recipeId }
        )
    }
}
