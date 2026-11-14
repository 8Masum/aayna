package com.themasum.aayna.feature.viewfinder

import com.themasum.aayna.core.camera.ZoomUiState
import com.themasum.aayna.core.model.AspectRatioPreset
import com.themasum.aayna.core.model.CameraCatalog
import com.themasum.aayna.core.model.CameraCategory
import com.themasum.aayna.core.model.CameraProfile
import com.themasum.aayna.core.model.CameraSkinSpec
import com.themasum.aayna.core.model.CaptureStorageMode
import com.themasum.aayna.core.model.ExposureMath
import com.themasum.aayna.core.model.FlashMode
import com.themasum.aayna.core.model.FrameBorderStyle
import com.themasum.aayna.core.model.LensAttachment
import com.themasum.aayna.core.model.OpticalSettings
import com.themasum.aayna.core.model.SkinCatalog
import com.themasum.aayna.core.model.StampConfig
import com.themasum.aayna.core.model.TimerDuration
import com.themasum.aayna.core.model.UserSettingsSnapshot
import com.themasum.aayna.core.model.ViewfinderMaskType
import com.themasum.aayna.core.model.WhiteBalancePreset
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class ViewfinderPanel {
    NONE,
    CONTROLS,
    CAMERA_SHELF,
    SKIN_MANAGER,
    LENS,
    FRAME,
    STAMP
}

enum class ControlTab {
    EXPOSURE,
    OPTICS
}

enum class RulerParameter(val shortLabel: String) {
    EV("EV"),
    WARMTH("WARM"),
    VIGNETTE("VIG"),
    BLOOM("BLOOM"),
    GRAIN("GRAIN"),
    LEAK("LEAK")
}

enum class CaptureKind {
    STILL,
    VIDEO,
    D3D_BURST,
    PHOTO_BOOTH
}

data class RulerSpec(
    val parameter: RulerParameter,
    val minValue: Float,
    val maxValue: Float,
    val notchCount: Int,
    val majorStepNotches: Int
)

object ProccdRulerMath {
    fun specFor(parameter: RulerParameter): RulerSpec = when (parameter) {
        RulerParameter.EV -> RulerSpec(
            parameter,
            minValue = -2.0f,
            maxValue = 2.0f,
            notchCount = 41,
            majorStepNotches = 5
        )
        RulerParameter.WARMTH ->
            RulerSpec(parameter, minValue = -1.0f, maxValue = 1.0f, notchCount = 41, majorStepNotches = 5)
        RulerParameter.VIGNETTE ->
            RulerSpec(parameter, minValue = 0.0f, maxValue = 1.0f, notchCount = 21, majorStepNotches = 5)
        RulerParameter.BLOOM ->
            RulerSpec(parameter, minValue = 0.0f, maxValue = 1.0f, notchCount = 21, majorStepNotches = 5)
        RulerParameter.GRAIN ->
            RulerSpec(parameter, minValue = 0.0f, maxValue = 1.0f, notchCount = 21, majorStepNotches = 5)
        RulerParameter.LEAK ->
            RulerSpec(parameter, minValue = 0.0f, maxValue = 1.0f, notchCount = 21, majorStepNotches = 5)
    }

    fun valueToNotchIndex(spec: RulerSpec, value: Float): Int {
        val maxIndex = (spec.notchCount - 1).coerceAtLeast(1)
        val span = (spec.maxValue - spec.minValue).takeIf { it > 0f } ?: 1f
        val fraction = ((value - spec.minValue) / span).coerceIn(0f, 1f)
        return (fraction * maxIndex).roundToInt().coerceIn(0, maxIndex)
    }

    fun notchIndexToValue(spec: RulerSpec, index: Int): Float {
        val maxIndex = (spec.notchCount - 1).coerceAtLeast(1)
        val clamped = index.coerceIn(0, maxIndex)
        val fraction = clamped.toFloat() / maxIndex.toFloat()
        return spec.minValue + fraction * (spec.maxValue - spec.minValue)
    }

    fun stepByNotches(spec: RulerSpec, currentValue: Float, deltaNotches: Int): Float {
        val currentIdx = valueToNotchIndex(spec, currentValue)
        return notchIndexToValue(spec, currentIdx + deltaNotches)
    }

    fun formatValue(parameter: RulerParameter, value: Float): String = when (parameter) {
        RulerParameter.EV -> ViewfinderStateReducer.formatEvLabel(value)
        RulerParameter.WARMTH -> {
            val pct = (value * 100f).roundToInt()
            if (pct > 0) "+$pct" else "$pct"
        }
        RulerParameter.VIGNETTE,
        RulerParameter.BLOOM,
        RulerParameter.GRAIN,
        RulerParameter.LEAK -> "${(value.coerceIn(0f, 1f) * 100f).roundToInt()}%"
    }
}

data class FocusReticleState(val normalizedX: Float, val normalizedY: Float, val triggeredAtMs: Long)

data class DevelopingOverlayState(val frameNumber: Int, val capacity: Int, val cameraCodeName: String)

data class EjectedPhotoCardState(
    val thumbnailPath: String?,
    val cameraCodeName: String,
    val label: String,
    val triggeredAtMs: Long
)

data class ViewfinderUiState(
    val activeCamera: CameraProfile = CameraCatalog.defaultCamera,
    val activeSkinId: String = CameraCatalog.defaultCamera.defaultSkinId,
    val activeSkin: CameraSkinSpec = CameraCatalog.defaultCamera.skin,
    val opticalSettings: OpticalSettings =
        ViewfinderStateReducer.defaultOpticalSettingsFor(CameraCatalog.defaultCamera),
    val stampConfig: StampConfig =
        ViewfinderStateReducer.defaultStampConfigFor(
            CameraCatalog.defaultCamera,
            UserSettingsSnapshot()
        ),
    val settings: UserSettingsSnapshot = UserSettingsSnapshot(),
    val favoriteCameraIds: Set<String> = emptySet(),
    val isVideoMode: Boolean = false,
    val isMinimalMode: Boolean = false,
    val timerDuration: TimerDuration = TimerDuration.OFF,
    val countdownRemainingSeconds: Int? = null,
    val isGridVisible: Boolean = false,
    val isFrontCamera: Boolean = false,
    val isCapturing: Boolean = false,
    val isRecordingVideo: Boolean = false,
    val recordingElapsedMs: Long = 0L,
    val boothSequenceStep: Int = 0,
    val isPreviewHoldOriginal: Boolean = false,
    val showCaptureFlashPulse: Boolean = false,
    val activePanel: ViewfinderPanel = ViewfinderPanel.NONE,
    val activeControlTab: ControlTab = ControlTab.EXPOSURE,
    val activeRulerParameter: RulerParameter = RulerParameter.EV,
    val grainStrengthOverride: Float? = null,
    val randomSeed: Long = 1042L,
    val activeRollId: String? = null,
    val rollShotsTaken: Int = 0,
    val rollCapacity: Int = 24,
    val showRollCompletePrompt: Boolean = false,
    val developingOverlay: DevelopingOverlayState? = null,
    val ejectedCardPreview: EjectedPhotoCardState? = null,
    val lastNegativeThumbnailPath: String? = null,
    val lastNegativeTimestampMs: Long = 0L,
    val statusBannerMessage: String? = null,
    val lastCapturedUri: String? = null,
    val lastErrorMessage: String? = null,
    val focusReticle: FocusReticleState? = null,
    val inputAspectRatio: Float = 4f / 3f,
    val zoomUiState: ZoomUiState = ZoomUiState(),
    val sampleSpecsCamera: CameraProfile? = null,
    val cameraShelfCategory: CameraCategory? = null,
    val cameraShelfFavoritesOnly: Boolean = false,
    val isHydrated: Boolean = false
)

object ViewfinderStateReducer {
    private val evPresets = listOf(-2.0f, -1.5f, -1.0f, -0.5f, 0.0f, 0.5f, 1.0f, 1.5f, 2.0f)

    fun defaultOpticalSettingsFor(profile: CameraProfile): OpticalSettings = profile.buildDefaultOpticalSettings()

    fun defaultStampConfigFor(profile: CameraProfile, settings: UserSettingsSnapshot): StampConfig = StampConfig(
        enabled = profile.stamp.defaultEnabled,
        style = settings.stampColor,
        fontStyle = settings.stampFontStyle,
        position = settings.stampPosition,
        format = settings.stampFormat,
        useEraYearOffset = settings.useEraYearOffset,
        eraYear = profile.eraYearOffset,
        glowStrength = settings.stampGlow,
        customText = ""
    )

    fun resolveEffectiveSkin(profile: CameraProfile, settings: UserSettingsSnapshot): Pair<String, CameraSkinSpec> {
        val skinId = settings.skinIdForCamera(profile.id, profile.defaultSkinId)
        val definition = SkinCatalog.findById(skinId)
        val spec = definition?.toCameraSkinSpec() ?: profile.skin
        return skinId to spec
    }

    fun switchCamera(state: ViewfinderUiState, profile: CameraProfile): ViewfinderUiState {
        val (skinId, skinSpec) = resolveEffectiveSkin(profile, state.settings)
        return state.copy(
            activeCamera = profile,
            activeSkinId = skinId,
            activeSkin = skinSpec,
            opticalSettings = defaultOpticalSettingsFor(profile),
            stampConfig = defaultStampConfigFor(profile, state.settings),
            isVideoMode = if (profile.supportsVideo) state.isVideoMode else false,
            grainStrengthOverride = null,
            boothSequenceStep = 0,
            activePanel = ViewfinderPanel.NONE
        )
    }

    fun applyUserSettings(
        state: ViewfinderUiState,
        settings: UserSettingsSnapshot,
        syncGridFromSettings: Boolean = false
    ): ViewfinderUiState {
        val (skinId, skinSpec) = resolveEffectiveSkin(state.activeCamera, settings)
        return state.copy(
            settings = settings,
            activeSkinId = skinId,
            activeSkin = skinSpec,
            rollCapacity = settings.rollCapacity,
            isGridVisible = if (syncGridFromSettings) settings.gridEnabled else state.isGridVisible,
            stampConfig =
            state.stampConfig.copy(
                style = settings.stampColor,
                fontStyle = settings.stampFontStyle,
                position = settings.stampPosition,
                format = settings.stampFormat,
                useEraYearOffset = settings.useEraYearOffset,
                eraYear = state.activeCamera.eraYearOffset,
                glowStrength = settings.stampGlow
            )
        )
    }

    fun shouldBlockCaptureForRoll(storageMode: CaptureStorageMode, shotsTaken: Int, capacity: Int): Boolean =
        storageMode == CaptureStorageMode.ANALOG_DEVELOPING_RITUAL && shotsTaken >= capacity

    fun resolveCaptureKind(profile: CameraProfile, isVideoMode: Boolean): CaptureKind = when {
        isVideoMode && profile.supportsVideo -> CaptureKind.VIDEO
        profile.isD3dBurst || profile.viewfinderMaskType == ViewfinderMaskType.MULTI_D3D -> CaptureKind.D3D_BURST
        profile.id == "booth" || profile.viewfinderMaskType == ViewfinderMaskType.PHOTO_BOOTH -> CaptureKind.PHOTO_BOOTH
        else -> CaptureKind.STILL
    }

    fun effectiveProfileForRender(state: ViewfinderUiState): CameraProfile {
        val override = state.grainStrengthOverride ?: return state.activeCamera
        val clamped = override.coerceIn(0f, 1f)
        return state.activeCamera.copy(
            grain = state.activeCamera.grain.copy(lumaStrength = clamped)
        )
    }

    fun currentRulerValue(state: ViewfinderUiState, parameter: RulerParameter): Float = when (parameter) {
        RulerParameter.EV -> state.opticalSettings.evCompensation
        RulerParameter.WARMTH -> state.opticalSettings.warmthShift
        RulerParameter.VIGNETTE -> state.opticalSettings.vignetteIntensity
        RulerParameter.BLOOM -> state.opticalSettings.bloomIntensity
        RulerParameter.GRAIN -> state.grainStrengthOverride ?: state.activeCamera.grain.lumaStrength
        RulerParameter.LEAK -> state.opticalSettings.lightLeakIntensity
    }

    fun applyRulerValue(state: ViewfinderUiState, parameter: RulerParameter, rawValue: Float): ViewfinderUiState {
        val spec = ProccdRulerMath.specFor(parameter)
        val value = rawValue.coerceIn(spec.minValue, spec.maxValue)
        return when (parameter) {
            RulerParameter.EV ->
                state.copy(
                    opticalSettings = state.opticalSettings.copy(evCompensation = value).clamped()
                )
            RulerParameter.WARMTH ->
                state.copy(
                    opticalSettings = state.opticalSettings.copy(warmthShift = value).clamped()
                )
            RulerParameter.VIGNETTE ->
                state.copy(
                    opticalSettings = state.opticalSettings.copy(vignetteIntensity = value).clamped()
                )
            RulerParameter.BLOOM ->
                state.copy(
                    opticalSettings = state.opticalSettings.copy(bloomIntensity = value).clamped()
                )
            RulerParameter.GRAIN ->
                state.copy(grainStrengthOverride = value)
            RulerParameter.LEAK ->
                state.copy(
                    opticalSettings = state.opticalSettings.copy(lightLeakIntensity = value).clamped()
                )
        }
    }

    fun stepEv(currentEv: Float, delta: Float): Float {
        val raw = (currentEv + delta).coerceIn(-2.0f, 2.0f)
        return (raw * 2f).roundToInt() / 2f
    }

    fun cycleEv(currentEv: Float): Float {
        val idx = evPresets.indexOfFirst { abs(it - currentEv) < 0.05f }
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

    fun formatRollCounter(shotsTaken: Int, capacity: Int): String {
        val safeCap = capacity.coerceAtLeast(1)
        val count = shotsTaken.coerceIn(0, safeCap).toString().padStart(2, '0')
        val capStr = safeCap.toString().padStart(2, '0')
        return "$count/$capStr"
    }

    fun formatLcdModeBadge(state: ViewfinderUiState): String = when (state.settings.storageMode) {
        CaptureStorageMode.INSTANT_DIGICAM -> "INSTANT"
        CaptureStorageMode.NON_DESTRUCTIVE_ROLL -> "NEG"
        CaptureStorageMode.ANALOG_DEVELOPING_RITUAL -> {
            "ROLL ${formatRollCounter(state.rollShotsTaken, state.rollCapacity)}"
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
