package com.themasum.aayna.feature.gallery

import com.themasum.aayna.core.gl.GlRenderParams
import com.themasum.aayna.core.model.AspectRatioPreset
import com.themasum.aayna.core.model.CameraCatalog
import com.themasum.aayna.core.model.CameraCategory
import com.themasum.aayna.core.model.CameraProfile
import com.themasum.aayna.core.model.CaptureRecipe
import com.themasum.aayna.core.model.ExposureMath
import com.themasum.aayna.core.model.FlashMode
import com.themasum.aayna.core.model.FrameBorderStyle
import com.themasum.aayna.core.model.LensAttachment
import com.themasum.aayna.core.model.MediaType
import com.themasum.aayna.core.model.OpticalSettings
import com.themasum.aayna.core.model.ResolutionPreset
import com.themasum.aayna.core.model.StampConfig
import com.themasum.aayna.core.model.StampFontStyle
import com.themasum.aayna.core.model.StampFormat
import com.themasum.aayna.core.model.StampPosition
import com.themasum.aayna.core.model.StampStyle
import com.themasum.aayna.core.model.UserSettingsSnapshot
import com.themasum.aayna.core.model.WhiteBalancePreset
import com.themasum.aayna.core.storage.db.NegativeEntity
import java.util.UUID

enum class GalleryFilterTab(val label: String) {
    ALL("All"),
    ROLLS("Rolls"),
    INSTANT("Instant"),
    VIDEO("Video"),
    D3D("D3D"),
    TRASH("Trash")
}

enum class StudioEditorTab(val label: String) {
    CAMERA("Camera"),
    ADJUST("Adjust"),
    FILM_FX("Film FX"),
    FRAME_LENS("Frame & Lens"),
    DATE_STAMP("Date Stamp"),
    CROP_ROTATE("Crop & Rotate")
}

enum class CameraShelfGroup(val label: String) {
    POPULAR("Popular"),
    CCD("CCD"),
    FILM("Film"),
    INSTANT("Instant"),
    CAMCORDER("Camcorder"),
    SPECIAL("Special")
}

enum class StudioCropPreset(val label: String, val aspectPreset: AspectRatioPreset?, val exactRatio: Float?) {
    ORIGINAL("Original", null, null),
    RATIO_1_1("1:1", AspectRatioPreset.RATIO_1_1, 1f),
    RATIO_3_4("3:4", AspectRatioPreset.RATIO_4_3, 3f / 4f),
    RATIO_4_3("4:3", AspectRatioPreset.RATIO_4_3, 4f / 3f),
    RATIO_16_9("16:9", AspectRatioPreset.RATIO_16_9, 16f / 9f),
    RATIO_9_16("9:16", AspectRatioPreset.RATIO_16_9, 9f / 16f)
}

enum class AdjustParameter(val label: String) {
    EV("EV"),
    HIGHLIGHTS("Highlights"),
    SHADOWS("Shadows"),
    SHARPEN("Sharpen"),
    WARMTH("Warmth"),
    BEAUTY("Beauty")
}

object GalleryFilterHelper {
    fun isInstantWork(entity: NegativeEntity): Boolean {
        if (entity.resolvedMediaType() != MediaType.STILL) return false
        val profile = CameraCatalog.findByIdOrDefault(entity.cameraId)
        return entity.rollId == null || profile.category == CameraCategory.INSTANT
    }

    fun filterNegatives(
        tab: GalleryFilterTab,
        activeNegatives: List<NegativeEntity>,
        trashedNegatives: List<NegativeEntity>
    ): List<NegativeEntity> = when (tab) {
        GalleryFilterTab.ALL -> activeNegatives.filter { !it.isTrashed }
        GalleryFilterTab.ROLLS -> activeNegatives.filter { !it.isTrashed && it.rollId != null }
        GalleryFilterTab.INSTANT -> activeNegatives.filter { !it.isTrashed && isInstantWork(it) }
        GalleryFilterTab.VIDEO -> activeNegatives.filter {
            !it.isTrashed && it.resolvedMediaType() == MediaType.VIDEO
        }
        GalleryFilterTab.D3D -> activeNegatives.filter {
            !it.isTrashed && it.resolvedMediaType() == MediaType.D3D_BURST
        }
        GalleryFilterTab.TRASH -> trashedNegatives.filter { it.isTrashed }
    }
}

object GalleryCameraShelfHelper {
    private val popularIds =
        listOf(
            "z30",
            "ixus95",
            "m532",
            "u300",
            "grf",
            "dcr",
            "v80",
            "instm",
            "d3d",
            "booth",
            "lomo",
            "sx70",
            "2000s",
            "quik"
        )

    fun camerasForGroup(group: CameraShelfGroup, favoriteCameraIds: Set<String> = emptySet()): List<CameraProfile> =
        when (group) {
            CameraShelfGroup.POPULAR -> {
                val favProfiles = favoriteCameraIds.mapNotNull { CameraCatalog.findById(it) }
                val curated = popularIds.mapNotNull { CameraCatalog.findById(it) }
                (favProfiles + curated).distinctBy { it.id }
            }
            CameraShelfGroup.CCD -> CameraCatalog.byCategory(CameraCategory.CCD)
            CameraShelfGroup.FILM -> CameraCatalog.byCategory(CameraCategory.FILM)
            CameraShelfGroup.INSTANT -> CameraCatalog.byCategory(CameraCategory.INSTANT)
            CameraShelfGroup.CAMCORDER -> CameraCatalog.byCategory(CameraCategory.CAMCORDER)
            CameraShelfGroup.SPECIAL ->
                CameraCatalog.all.filter { it.category == CameraCategory.SPECIAL || it.isSpecialSeries }
        }
}

object GalleryRecipeReducer {
    const val MIN_EV: Float = OpticalSettings.MIN_EV
    const val MAX_EV: Float = OpticalSettings.MAX_EV
    const val MIN_WARMTH: Float = OpticalSettings.MIN_WARMTH_SHIFT
    const val MAX_WARMTH: Float = OpticalSettings.MAX_WARMTH_SHIFT
    const val MIN_TONAL: Float = OpticalSettings.MIN_TONAL_SHIFT
    const val MAX_TONAL: Float = OpticalSettings.MAX_TONAL_SHIFT
    const val MIN_STRAIGHTEN_DEG: Float = OpticalSettings.MIN_STRAIGHTEN_DEGREES
    const val MAX_STRAIGHTEN_DEG: Float = OpticalSettings.MAX_STRAIGHTEN_DEGREES
    const val MAX_CUSTOM_STAMP_CHARS: Int = 24
    const val MAX_CAPTION_CHARS: Int = 140

    fun createImportedRecipe(
        cameraId: String,
        settings: UserSettingsSnapshot,
        capturedAtEpochMs: Long = System.currentTimeMillis(),
        randomSeed: Long = System.nanoTime(),
        recipeId: String = "import_${UUID.randomUUID()}",
        mediaType: MediaType = MediaType.STILL,
        burstFrameCount: Int = 1
    ): CaptureRecipe {
        val profile = CameraCatalog.findByIdOrDefault(cameraId)
        val skinId = settings.skinIdForCamera(profile.id, profile.defaultSkinId)
        return CaptureRecipe(
            recipeId = recipeId,
            cameraId = profile.id,
            capturedAtEpochMs = capturedAtEpochMs,
            randomSeed = randomSeed,
            mediaType = mediaType,
            skinId = skinId,
            resolutionPreset = settings.resolutionPreset,
            opticalSettings = profile.buildDefaultOpticalSettings(),
            stampConfig =
            StampConfig(
                enabled = profile.stamp.defaultEnabled,
                style = settings.stampColor,
                fontStyle = settings.stampFontStyle,
                position = settings.stampPosition,
                format = settings.stampFormat,
                useEraYearOffset = settings.useEraYearOffset,
                eraYear = profile.eraYearOffset,
                glowStrength = settings.stampGlow.coerceIn(0f, 1f)
            ),
            burstFrameCount = burstFrameCount.coerceAtLeast(1)
        )
    }

    fun swapCameraProfile(
        recipe: CaptureRecipe,
        newProfile: CameraProfile,
        applyProfileDefaults: Boolean = false,
        skinIdOverride: String? = null
    ): CaptureRecipe {
        val updatedOptics =
            if (applyProfileDefaults) {
                newProfile.buildDefaultOpticalSettings()
            } else {
                recipe.opticalSettings
            }
        return recipe.copy(
            cameraId = newProfile.id,
            skinId = skinIdOverride ?: newProfile.defaultSkinId,
            opticalSettings = updatedOptics,
            stampConfig = recipe.stampConfig.copy(eraYear = newProfile.eraYearOffset)
        )
    }

    fun shuffleRandomSeed(recipe: CaptureRecipe, candidateSeed: Long = System.nanoTime()): CaptureRecipe {
        val nextSeed =
            if (candidateSeed == recipe.randomSeed) {
                candidateSeed xor 0x5DEECE66DL + 1L
            } else {
                candidateSeed
            }
        return recipe.copy(randomSeed = nextSeed)
    }

    fun updateEv(recipe: CaptureRecipe, ev: Float): CaptureRecipe = recipe.copy(
        opticalSettings = recipe.opticalSettings.copy(evCompensation = ev.coerceIn(MIN_EV, MAX_EV))
    )

    fun updateHighlights(recipe: CaptureRecipe, highlights: Float): CaptureRecipe = recipe.copy(
        opticalSettings = recipe.opticalSettings.copy(highlights = highlights.coerceIn(MIN_TONAL, MAX_TONAL))
    )

    fun updateShadows(recipe: CaptureRecipe, shadows: Float): CaptureRecipe = recipe.copy(
        opticalSettings = recipe.opticalSettings.copy(shadows = shadows.coerceIn(MIN_TONAL, MAX_TONAL))
    )

    fun updateSharpen(recipe: CaptureRecipe, sharpen: Float): CaptureRecipe = recipe.copy(
        opticalSettings = recipe.opticalSettings.copy(sharpen = sharpen.coerceIn(0f, 1f))
    )

    fun updateWarmth(recipe: CaptureRecipe, warmth: Float): CaptureRecipe = recipe.copy(
        opticalSettings = recipe.opticalSettings.copy(warmthShift = warmth.coerceIn(MIN_WARMTH, MAX_WARMTH))
    )

    fun updateWhiteBalance(recipe: CaptureRecipe, wb: WhiteBalancePreset): CaptureRecipe = recipe.copy(
        opticalSettings = recipe.opticalSettings.copy(whiteBalance = wb)
    )

    fun updateBeautySoftness(recipe: CaptureRecipe, beauty: Float): CaptureRecipe = recipe.copy(
        opticalSettings = recipe.opticalSettings.copy(beautySoftness = beauty.coerceIn(0f, 1f))
    )

    fun updateBloom(recipe: CaptureRecipe, bloom: Float): CaptureRecipe = recipe.copy(
        opticalSettings = recipe.opticalSettings.copy(bloomIntensity = bloom.coerceIn(0f, 1f))
    )

    fun updateVignette(recipe: CaptureRecipe, vignette: Float): CaptureRecipe = recipe.copy(
        opticalSettings = recipe.opticalSettings.copy(vignetteIntensity = vignette.coerceIn(0f, 1f))
    )

    fun updateLightLeak(recipe: CaptureRecipe, lightLeak: Float): CaptureRecipe = recipe.copy(
        opticalSettings = recipe.opticalSettings.copy(lightLeakIntensity = lightLeak.coerceIn(0f, 1f))
    )

    fun updateDust(recipe: CaptureRecipe, dust: Float): CaptureRecipe = recipe.copy(
        opticalSettings = recipe.opticalSettings.copy(dustIntensity = dust.coerceIn(0f, 1f))
    )

    fun updateLensAttachment(recipe: CaptureRecipe, lens: LensAttachment): CaptureRecipe = recipe.copy(
        opticalSettings = recipe.opticalSettings.copy(lensAttachment = lens)
    )

    fun toggleFlashSim(recipe: CaptureRecipe): CaptureRecipe {
        val nextFlash =
            if (recipe.opticalSettings.flashMode == FlashMode.OPTICAL_FLASH_SIM) {
                FlashMode.OFF
            } else {
                FlashMode.OPTICAL_FLASH_SIM
            }
        return recipe.copy(
            opticalSettings = recipe.opticalSettings.copy(flashMode = nextFlash)
        )
    }

    fun updateAspectRatio(recipe: CaptureRecipe, aspectRatio: AspectRatioPreset): CaptureRecipe = recipe.copy(
        opticalSettings = recipe.opticalSettings.copy(aspectRatio = aspectRatio)
    )

    fun updateCropAspectRatio(recipe: CaptureRecipe, cropAspectRatio: AspectRatioPreset?): CaptureRecipe = recipe.copy(
        opticalSettings = recipe.opticalSettings.copy(cropAspectRatio = cropAspectRatio)
    )

    fun updateCropPreset(recipe: CaptureRecipe, preset: StudioCropPreset): CaptureRecipe = recipe.copy(
        opticalSettings = recipe.opticalSettings.copy(cropAspectRatio = preset.aspectPreset)
    )

    fun updateStraightenDegrees(recipe: CaptureRecipe, degrees: Float): CaptureRecipe = recipe.copy(
        opticalSettings =
        recipe.opticalSettings.copy(
            straightenDegrees = degrees.coerceIn(MIN_STRAIGHTEN_DEG, MAX_STRAIGHTEN_DEG)
        )
    )

    fun updateFrameStyle(recipe: CaptureRecipe, frameStyle: FrameBorderStyle): CaptureRecipe = recipe.copy(
        opticalSettings = recipe.opticalSettings.copy(frameStyle = frameStyle)
    )

    fun updateResolutionPreset(recipe: CaptureRecipe, preset: ResolutionPreset): CaptureRecipe =
        recipe.copy(resolutionPreset = preset)

    fun updateStampEnabled(recipe: CaptureRecipe, enabled: Boolean): CaptureRecipe = recipe.copy(
        stampConfig = recipe.stampConfig.copy(enabled = enabled)
    )

    fun updateStampStyle(recipe: CaptureRecipe, style: StampStyle): CaptureRecipe = recipe.copy(
        stampConfig = recipe.stampConfig.copy(style = style)
    )

    fun updateStampFontStyle(recipe: CaptureRecipe, fontStyle: StampFontStyle): CaptureRecipe = recipe.copy(
        stampConfig = recipe.stampConfig.copy(fontStyle = fontStyle)
    )

    fun updateStampPosition(recipe: CaptureRecipe, position: StampPosition): CaptureRecipe = recipe.copy(
        stampConfig = recipe.stampConfig.copy(position = position)
    )

    fun updateStampFormat(recipe: CaptureRecipe, format: StampFormat): CaptureRecipe = recipe.copy(
        stampConfig = recipe.stampConfig.copy(format = format)
    )

    fun updateStampGlow(recipe: CaptureRecipe, glow: Float): CaptureRecipe = recipe.copy(
        stampConfig = recipe.stampConfig.copy(glowStrength = glow.coerceIn(0f, 1f))
    )

    fun updateStampEraYearOffset(recipe: CaptureRecipe, useEraYearOffset: Boolean): CaptureRecipe = recipe.copy(
        stampConfig = recipe.stampConfig.copy(useEraYearOffset = useEraYearOffset)
    )

    fun updateStampCustomText(recipe: CaptureRecipe, customText: String): CaptureRecipe = recipe.copy(
        stampConfig = recipe.stampConfig.copy(customText = customText.take(MAX_CUSTOM_STAMP_CHARS))
    )

    fun updateCaption(recipe: CaptureRecipe, caption: String): CaptureRecipe =
        recipe.copy(caption = caption.take(MAX_CAPTION_CHARS))

    fun buildGlRenderParams(
        recipe: CaptureRecipe,
        profile: CameraProfile,
        stampText: String,
        grainBoost: Float = 0f,
        splitCompareRatio: Float = 0f,
        isHoldOriginal: Boolean = false,
        inputAspectRatio: Float = 4f / 3f,
        skipStamp: Boolean = isHoldOriginal
    ): GlRenderParams {
        val baseGains =
            ExposureMath.computeShaderFallbackGains(
                hasManualSensor = false,
                ev = recipe.opticalSettings.evCompensation,
                iso = recipe.opticalSettings.iso,
                shutterNs = recipe.opticalSettings.shutterSpeedNs
            )
        val clampedGrainBoost = grainBoost.coerceIn(0f, 1f) * 1.4f
        val effectiveGains =
            baseGains.copy(
                shaderNoiseBoost = maxOf(baseGains.shaderNoiseBoost, clampedGrainBoost)
            )
        return GlRenderParams(
            profile = profile,
            opticalSettings = recipe.opticalSettings.clamped(),
            stampConfig = recipe.stampConfig,
            stampText = stampText,
            fallbackGains = effectiveGains,
            isPreviewHoldOriginal = isHoldOriginal,
            splitCompareRatio = splitCompareRatio.coerceIn(0f, 1f),
            randomSeed = recipe.randomSeed,
            inputAspectRatio = inputAspectRatio.coerceAtLeast(0.1f),
            skipStamp = skipStamp
        )
    }
}
