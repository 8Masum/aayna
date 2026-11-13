package com.themasum.aayna.feature.gallery

import com.themasum.aayna.core.model.AspectRatioPreset
import com.themasum.aayna.core.model.CameraCatalog
import com.themasum.aayna.core.model.CameraProfile
import com.themasum.aayna.core.model.CaptureRecipe
import com.themasum.aayna.core.model.FlashMode
import com.themasum.aayna.core.model.FrameBorderStyle
import com.themasum.aayna.core.model.LensAttachment
import com.themasum.aayna.core.model.OpticalSettings
import com.themasum.aayna.core.model.ResolutionPreset
import com.themasum.aayna.core.model.StampConfig
import com.themasum.aayna.core.model.StampFormat
import com.themasum.aayna.core.model.StampStyle
import com.themasum.aayna.core.model.UserSettingsSnapshot
import java.util.UUID

enum class GalleryLabMode(val label: String) {
    NEGATIVES("NEGATIVES"),
    FILM_ROLLS("FILM ROLLS")
}

enum class StudioEditorTab(val label: String) {
    CAMERA("CAMERA"),
    OPTICS("OPTICS"),
    EFFECTS("EFFECTS"),
    STAMP("FRAME & STAMP")
}

object GalleryRecipeReducer {
    const val MIN_EV: Float = -2.0f
    const val MAX_EV: Float = 2.0f
    const val MIN_WARMTH: Float = -1.0f
    const val MAX_WARMTH: Float = 1.0f

    fun createImportedRecipe(
        cameraId: String,
        settings: UserSettingsSnapshot,
        capturedAtEpochMs: Long = System.currentTimeMillis(),
        randomSeed: Long = System.nanoTime(),
        recipeId: String = "import_${UUID.randomUUID()}"
    ): CaptureRecipe {
        val profile = CameraCatalog.findByIdOrDefault(cameraId)
        return CaptureRecipe(
            recipeId = recipeId,
            cameraId = profile.id,
            capturedAtEpochMs = capturedAtEpochMs,
            randomSeed = randomSeed,
            resolutionPreset = settings.resolutionPreset,
            opticalSettings =
            OpticalSettings(
                bloomIntensity = profile.bloom.intensity.coerceIn(0f, 1f),
                lightLeakIntensity = profile.grunge.lightLeakStrength.coerceIn(0f, 1f),
                dustIntensity = profile.grunge.dustStrength.coerceIn(0f, 1f),
                vignetteIntensity = profile.optics.vignetteStrength.coerceIn(0f, 1f),
                aspectRatio = profile.defaultAspect,
                frameStyle = profile.defaultFrame
            ),
            stampConfig =
            StampConfig(
                enabled = profile.stamp.defaultEnabled,
                style = settings.stampColor,
                format = settings.stampFormat,
                useEraYearOffset = settings.useEraYearOffset,
                eraYear = profile.eraYearOffset,
                glowStrength = settings.stampGlow.coerceIn(0f, 1f)
            )
        )
    }

    fun swapCameraProfile(
        recipe: CaptureRecipe,
        newProfile: CameraProfile,
        applyProfileDefaults: Boolean = false
    ): CaptureRecipe {
        val updatedOptics =
            if (applyProfileDefaults) {
                recipe.opticalSettings.copy(
                    bloomIntensity = newProfile.bloom.intensity.coerceIn(0f, 1f),
                    lightLeakIntensity = newProfile.grunge.lightLeakStrength.coerceIn(0f, 1f),
                    dustIntensity = newProfile.grunge.dustStrength.coerceIn(0f, 1f),
                    vignetteIntensity = newProfile.optics.vignetteStrength.coerceIn(0f, 1f),
                    aspectRatio = newProfile.defaultAspect,
                    frameStyle = newProfile.defaultFrame
                )
            } else {
                recipe.opticalSettings
            }
        return recipe.copy(
            cameraId = newProfile.id,
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

    fun updateWarmth(recipe: CaptureRecipe, warmth: Float): CaptureRecipe = recipe.copy(
        opticalSettings = recipe.opticalSettings.copy(warmthShift = warmth.coerceIn(MIN_WARMTH, MAX_WARMTH))
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

    fun updateStampFormat(recipe: CaptureRecipe, format: StampFormat): CaptureRecipe = recipe.copy(
        stampConfig = recipe.stampConfig.copy(format = format)
    )

    fun updateStampEraYearOffset(recipe: CaptureRecipe, useEraYearOffset: Boolean): CaptureRecipe = recipe.copy(
        stampConfig = recipe.stampConfig.copy(useEraYearOffset = useEraYearOffset)
    )

    fun updateStampCustomText(recipe: CaptureRecipe, customText: String): CaptureRecipe = recipe.copy(
        stampConfig = recipe.stampConfig.copy(customText = customText.take(24))
    )
}
