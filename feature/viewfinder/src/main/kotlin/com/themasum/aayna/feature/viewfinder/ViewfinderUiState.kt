package com.themasum.aayna.feature.viewfinder

import com.themasum.aayna.core.model.AspectRatioPreset
import com.themasum.aayna.core.model.CameraCatalog
import com.themasum.aayna.core.model.CameraProfile
import com.themasum.aayna.core.model.CaptureStorageMode
import com.themasum.aayna.core.model.ExposureMath
import com.themasum.aayna.core.model.FlashMode
import com.themasum.aayna.core.model.FrameBorderStyle
import com.themasum.aayna.core.model.LensAttachment
import com.themasum.aayna.core.model.OpticalSettings
import com.themasum.aayna.core.model.StampConfig
import com.themasum.aayna.core.model.TimerDuration
import com.themasum.aayna.core.model.UserSettingsSnapshot
import com.themasum.aayna.core.model.WhiteBalancePreset
import java.util.Locale
import kotlin.math.roundToInt

enum class ViewfinderPanel {
    NONE,
    CONTROLS,
    CAMERA_SHELF
}

enum class ControlTab {
    EXPOSURE,
    OPTICS
}

data class FocusReticleState(val normalizedX: Float, val normalizedY: Float, val triggeredAtMs: Long)

data class DevelopingOverlayState(val frameNumber: Int, val capacity: Int, val cameraCodeName: String)

data class ViewfinderUiState(
    val activeCamera: CameraProfile = CameraCatalog.defaultCamera,
    val opticalSettings: OpticalSettings =
        ViewfinderStateReducer.defaultOpticalSettingsFor(CameraCatalog.defaultCamera),
    val stampConfig: StampConfig =
        ViewfinderStateReducer.defaultStampConfigFor(
            CameraCatalog.defaultCamera,
            UserSettingsSnapshot()
        ),
    val settings: UserSettingsSnapshot = UserSettingsSnapshot(),
    val timerDuration: TimerDuration = TimerDuration.OFF,
    val countdownRemainingSeconds: Int? = null,
    val isGridVisible: Boolean = false,
    val isFrontCamera: Boolean = false,
    val isCapturing: Boolean = false,
    val isPreviewHoldOriginal: Boolean = false,
    val showCaptureFlashPulse: Boolean = false,
    val activePanel: ViewfinderPanel = ViewfinderPanel.NONE,
    val activeControlTab: ControlTab = ControlTab.EXPOSURE,
    val randomSeed: Long = 1042L,
    val activeRollId: String? = null,
    val rollShotsTaken: Int = 0,
    val rollCapacity: Int = 24,
    val showRollCompletePrompt: Boolean = false,
    val developingOverlay: DevelopingOverlayState? = null,
    val statusBannerMessage: String? = null,
    val lastCapturedUri: String? = null,
    val lastErrorMessage: String? = null,
    val focusReticle: FocusReticleState? = null,
    val inputAspectRatio: Float = 4f / 3f
)

object ViewfinderStateReducer {
    private val evPresets = listOf(-2.0f, -1.5f, -1.0f, -0.5f, 0.0f, 0.5f, 1.0f, 1.5f, 2.0f)

    fun defaultOpticalSettingsFor(profile: CameraProfile): OpticalSettings {
        val defaultLens =
            when {
                profile.optics.fisheyeDefault > 0f -> LensAttachment.FISHEYE
                profile.optics.starPoints == 4 -> LensAttachment.STAR_4PT
                profile.optics.starPoints == 6 -> LensAttachment.STAR_6PT
                else -> LensAttachment.STANDARD
            }
        return OpticalSettings(
            evCompensation = 0f,
            iso = null,
            shutterSpeedNs = null,
            whiteBalance = WhiteBalancePreset.AUTO,
            warmthShift = 0f,
            bloomIntensity = profile.bloom.intensity,
            lensAttachment = defaultLens,
            lightLeakIntensity = profile.grunge.lightLeakStrength,
            dustIntensity = profile.grunge.dustStrength,
            vignetteIntensity = profile.optics.vignetteStrength,
            flashMode = FlashMode.OFF,
            aspectRatio = profile.defaultAspect,
            frameStyle = profile.defaultFrame
        )
    }

    fun defaultStampConfigFor(profile: CameraProfile, settings: UserSettingsSnapshot): StampConfig = StampConfig(
        enabled = profile.stamp.defaultEnabled,
        style = settings.stampColor,
        format = settings.stampFormat,
        useEraYearOffset = settings.useEraYearOffset,
        eraYear = profile.eraYearOffset,
        glowStrength = settings.stampGlow,
        customText = ""
    )

    fun switchCamera(state: ViewfinderUiState, profile: CameraProfile): ViewfinderUiState = state.copy(
        activeCamera = profile,
        opticalSettings = defaultOpticalSettingsFor(profile),
        stampConfig = defaultStampConfigFor(profile, state.settings),
        activePanel = ViewfinderPanel.NONE
    )

    fun applyUserSettings(
        state: ViewfinderUiState,
        settings: UserSettingsSnapshot,
        syncGridFromSettings: Boolean = false
    ): ViewfinderUiState = state.copy(
        settings = settings,
        rollCapacity = settings.rollCapacity,
        isGridVisible = if (syncGridFromSettings) settings.gridEnabled else state.isGridVisible,
        stampConfig =
        state.stampConfig.copy(
            style = settings.stampColor,
            format = settings.stampFormat,
            useEraYearOffset = settings.useEraYearOffset,
            eraYear = state.activeCamera.eraYearOffset,
            glowStrength = settings.stampGlow
        )
    )

    fun stepEv(currentEv: Float, delta: Float): Float {
        val raw = (currentEv + delta).coerceIn(-2.0f, 2.0f)
        return (raw * 2f).roundToInt() / 2f
    }

    fun cycleEv(currentEv: Float): Float {
        val idx = evPresets.indexOfFirst { kotlin.math.abs(it - currentEv) < 0.05f }
        return if (idx < 0) 0.0f else evPresets[(idx + 1) % evPresets.size]
    }

    fun cycleIso(currentIso: Int?): Int? {
        val steps = ExposureMath.STANDARD_ISO_STEPS
        val idx = steps.indexOf(currentIso)
        return if (idx < 0) steps.first() else steps[(idx + 1) % steps.size]
    }

    fun cycleShutter(currentNs: Long?): Long? {
        val presets = ExposureMath.STANDARD_SHUTTER_PRESETS
        val idx = presets.indexOfFirst { it.exposureTimeNs == currentNs }
        return if (idx < 0) presets.first().exposureTimeNs else presets[(idx + 1) % presets.size].exposureTimeNs
    }

    fun cycleWhiteBalance(current: WhiteBalancePreset): WhiteBalancePreset {
        val values = WhiteBalancePreset.entries
        return values[(current.ordinal + 1) % values.size]
    }

    fun cycleFlashMode(current: FlashMode): FlashMode {
        val values = FlashMode.entries
        return values[(current.ordinal + 1) % values.size]
    }

    fun cycleAspectRatio(current: AspectRatioPreset): AspectRatioPreset {
        val values = AspectRatioPreset.entries
        return values[(current.ordinal + 1) % values.size]
    }

    fun cycleTimer(current: TimerDuration): TimerDuration {
        val values = TimerDuration.entries
        return values[(current.ordinal + 1) % values.size]
    }

    fun cycleLensAttachment(current: LensAttachment): LensAttachment {
        val values = LensAttachment.entries
        return values[(current.ordinal + 1) % values.size]
    }

    fun toggleFrameStyle(profile: CameraProfile, current: FrameBorderStyle): FrameBorderStyle {
        if (current != FrameBorderStyle.NONE) {
            return FrameBorderStyle.NONE
        }
        return when {
            profile.optionalFrame != FrameBorderStyle.NONE -> profile.optionalFrame
            profile.defaultFrame != FrameBorderStyle.NONE -> profile.defaultFrame
            else -> FrameBorderStyle.INSTANT_PRINT
        }
    }

    fun toggleLeaksAndDust(profile: CameraProfile, settings: OpticalSettings): OpticalSettings {
        val currentlyActive = settings.lightLeakIntensity > 0f || settings.dustIntensity > 0f
        return if (currentlyActive) {
            settings.copy(lightLeakIntensity = 0f, dustIntensity = 0f)
        } else {
            settings.copy(
                lightLeakIntensity = profile.grunge.lightLeakStrength.coerceAtLeast(0.28f),
                dustIntensity = profile.grunge.dustStrength.coerceAtLeast(0.22f)
            )
        }
    }

    fun formatLcdModeBadge(state: ViewfinderUiState): String = when (state.settings.storageMode) {
        CaptureStorageMode.INSTANT_DIGICAM -> "INSTANT"
        CaptureStorageMode.NON_DESTRUCTIVE_ROLL -> "NEG"
        CaptureStorageMode.ANALOG_DEVELOPING_RITUAL -> {
            val count = state.rollShotsTaken.toString().padStart(2, '0')
            "ROLL $count/${state.rollCapacity}"
        }
    }

    fun formatShutterLabel(shutterNs: Long?): String = ExposureMath.STANDARD_SHUTTER_PRESETS
        .firstOrNull { it.exposureTimeNs == shutterNs }
        ?.label
        ?: "AUTO"

    fun formatEvLabel(ev: Float): String {
        val rounded = (ev * 10f).roundToInt() / 10f
        return when {
            rounded > 0f -> String.format(Locale.US, "+%.1f", rounded)
            rounded < 0f -> String.format(Locale.US, "%.1f", rounded)
            else -> "0.0"
        }
    }
}
