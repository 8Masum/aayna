package com.themasum.aayna.core.camera

import android.hardware.camera2.CaptureRequest
import com.themasum.aayna.core.model.AspectRatioPreset
import com.themasum.aayna.core.model.DeviceQuirkFlags
import com.themasum.aayna.core.model.ExposureMath
import com.themasum.aayna.core.model.OpticalSettings
import com.themasum.aayna.core.model.ShaderExposureFallback
import kotlin.math.roundToInt

/**
 * Hardware zoom bounds reported by CameraX [androidx.camera.core.ZoomState].
 */
data class ZoomBounds(val minZoomRatio: Float = 1f, val maxZoomRatio: Float = 1f) {
    val supportsUltraWide: Boolean
        get() = minZoomRatio <= ULTRA_WIDE_THRESHOLD

    fun clampRatio(ratio: Float): Float {
        val low = minOf(minZoomRatio, maxZoomRatio)
        val high = maxOf(minZoomRatio, maxZoomRatio)
        return ratio.coerceIn(low, high)
    }

    fun availablePresetRatios(candidates: List<Float> = DEFAULT_ROCKER_PRESETS): List<Float> {
        val low = minOf(minZoomRatio, maxZoomRatio)
        val high = maxOf(minZoomRatio, maxZoomRatio)
        return candidates
            .filter { it >= low - PRESET_TOLERANCE && it <= high + PRESET_TOLERANCE }
            .ifEmpty { listOf(1f.coerceIn(low, high)) }
    }

    companion object {
        private const val ULTRA_WIDE_THRESHOLD = 0.75f
        private const val PRESET_TOLERANCE = 0.05f
        val DEFAULT_ROCKER_PRESETS: List<Float> = listOf(0.5f, 1f, 2f, 3f)
    }
}

/**
 * Reactive zoom UI state for rocker controls (`0.5x / 1x / 2x / 3x`) and pinch-to-zoom.
 */
data class ZoomUiState(val zoomRatio: Float = 1f, val linearZoom: Float = 0f, val bounds: ZoomBounds = ZoomBounds())

data class AeModeDecision(
    val useManualAeOff: Boolean,
    val clampedIso: Int?,
    val clampedExposureTimeNs: Long?,
    val evCompensationIndex: Int,
    val hardwareEvApplied: Boolean,
    val shaderFallback: ShaderExposureFallback
)

data class IspOverrideDecision(
    val edgeMode: Int?,
    val edgeModeLabel: String,
    val noiseReductionMode: Int?,
    val noiseReductionLabel: String
)

data class PreviewBindingPolicy(val useCompatPreview: Boolean, val enableCameraEffect: Boolean)

data class CameraBindAttempt(
    val includeImageCapture: Boolean,
    val includeVideoCapture: Boolean,
    val includeEffect: Boolean
)

/**
 * Pure-Kotlin Camera2 / CameraX policy math for AE gating, EV clamping, ISP overrides,
 * preview compatibility fallback, and aspect ratio mapping.
 */
object CameraPolicyMath {
    const val SAFE_CLAMPED_EV_RANGE = 1.5f

    fun resolveAeDecision(
        settings: OpticalSettings,
        hasManualSensor: Boolean,
        minIso: Int,
        maxIso: Int,
        minExposureTimeNs: Long,
        maxExposureTimeNs: Long,
        aeCompensationMinIndex: Int,
        aeCompensationMaxIndex: Int,
        aeCompensationStepNumerator: Int,
        aeCompensationStepDenominator: Int,
        quirkFlags: DeviceQuirkFlags,
        useHardwareAeCompatibility: Boolean = false,
        hasBoundCamera: Boolean = true
    ): AeModeDecision {
        val allowManualSensor =
            hasManualSensor && !quirkFlags.disableManualAe && !useHardwareAeCompatibility
        val useManualAeOff =
            ExposureMath.isFullManualExposure(
                hasManualSensor = allowManualSensor,
                iso = settings.iso,
                shutterNs = settings.shutterSpeedNs
            )

        if (useManualAeOff) {
            val clampedIso = ExposureMath.clampIso(settings.iso, minIso, maxIso)
            val clampedNs =
                ExposureMath.clampExposureTimeNs(
                    requestedNs = settings.shutterSpeedNs,
                    minNs = minExposureTimeNs,
                    maxNs = maxExposureTimeNs
                )
            val fallback =
                ExposureMath.computeShaderFallbackGains(
                    hasManualSensor = true,
                    ev = settings.evCompensation,
                    iso = clampedIso,
                    shutterNs = clampedNs,
                    hardwareEvApplied = false
                )
            return AeModeDecision(
                useManualAeOff = true,
                clampedIso = clampedIso,
                clampedExposureTimeNs = clampedNs,
                evCompensationIndex = 0,
                hardwareEvApplied = false,
                shaderFallback = fallback
            )
        }

        val lowIndex = minOf(aeCompensationMinIndex, aeCompensationMaxIndex)
        val highIndex = maxOf(aeCompensationMinIndex, aeCompensationMaxIndex)
        val stepSize =
            if (aeCompensationStepNumerator > 0 && aeCompensationStepDenominator > 0) {
                aeCompensationStepNumerator.toFloat() / aeCompensationStepDenominator.toFloat()
            } else {
                0f
            }

        val (effectiveEv, effectiveMinIndex, effectiveMaxIndex) =
            if (quirkFlags.clampAeRange) {
                val clampedEv =
                    settings.evCompensation.coerceIn(-SAFE_CLAMPED_EV_RANGE, SAFE_CLAMPED_EV_RANGE)
                if (stepSize > 0f && lowIndex < highIndex) {
                    val safeMaxSteps = (SAFE_CLAMPED_EV_RANGE / stepSize).roundToInt().coerceAtLeast(1)
                    Triple(
                        clampedEv,
                        maxOf(lowIndex, -safeMaxSteps),
                        minOf(highIndex, safeMaxSteps)
                    )
                } else {
                    Triple(clampedEv, lowIndex, highIndex)
                }
            } else {
                Triple(settings.evCompensation, lowIndex, highIndex)
            }

        val evIndex =
            ExposureMath.evToCompensationSteps(
                ev = effectiveEv,
                stepNumerator = aeCompensationStepNumerator,
                stepDenominator = aeCompensationStepDenominator,
                minIndex = effectiveMinIndex,
                maxIndex = effectiveMaxIndex
            )
        val canApplyHardwareEv = hasBoundCamera && effectiveMinIndex < effectiveMaxIndex && stepSize > 0f
        val fallback =
            ExposureMath.computeShaderFallbackGains(
                hasManualSensor = allowManualSensor,
                ev = settings.evCompensation,
                iso = settings.iso,
                shutterNs = settings.shutterSpeedNs,
                hardwareEvApplied = canApplyHardwareEv
            )

        return AeModeDecision(
            useManualAeOff = false,
            clampedIso = null,
            clampedExposureTimeNs = null,
            evCompensationIndex = evIndex,
            hardwareEvApplied = canApplyHardwareEv,
            shaderFallback = fallback
        )
    }

    fun resolveIspOverrides(
        availableEdgeModes: IntArray,
        availableNoiseReductionModes: IntArray,
        quirkFlags: DeviceQuirkFlags
    ): IspOverrideDecision {
        val (edgeMode, edgeLabel) =
            if (quirkFlags.disableEdgeOffOverride) {
                null to "DEFAULT"
            } else {
                when {
                    availableEdgeModes.contains(CaptureRequest.EDGE_MODE_OFF) ->
                        CaptureRequest.EDGE_MODE_OFF to "OFF"

                    availableEdgeModes.contains(CaptureRequest.EDGE_MODE_FAST) ->
                        CaptureRequest.EDGE_MODE_FAST to "FAST"

                    else -> null to "DEFAULT"
                }
            }

        val (nrMode, nrLabel) =
            if (quirkFlags.disableNoiseReductionOffOverride) {
                null to "DEFAULT"
            } else {
                when {
                    availableNoiseReductionModes.contains(CaptureRequest.NOISE_REDUCTION_MODE_OFF) ->
                        CaptureRequest.NOISE_REDUCTION_MODE_OFF to "OFF"

                    availableNoiseReductionModes.contains(CaptureRequest.NOISE_REDUCTION_MODE_MINIMAL) ->
                        CaptureRequest.NOISE_REDUCTION_MODE_MINIMAL to "MINIMAL"

                    else -> null to "DEFAULT"
                }
            }

        return IspOverrideDecision(
            edgeMode = edgeMode,
            edgeModeLabel = edgeLabel,
            noiseReductionMode = nrMode,
            noiseReductionLabel = nrLabel
        )
    }

    fun resolvePreviewBindingPolicy(
        preferCompatPreview: Boolean,
        hasSurfaceProcessor: Boolean,
        quirkFlags: DeviceQuirkFlags,
        effectBindingFailed: Boolean = false
    ): PreviewBindingPolicy = PreviewBindingPolicy(
        useCompatPreview = preferCompatPreview || quirkFlags.forceCompatPreview,
        enableCameraEffect = hasSurfaceProcessor && !effectBindingFailed
    )

    fun buildBindAttemptPlan(
        hasVideoUseCase: Boolean,
        hasEffect: Boolean,
        preferVideoMode: Boolean = false
    ): List<CameraBindAttempt> {
        if (!hasVideoUseCase) {
            return if (hasEffect) {
                listOf(
                    CameraBindAttempt(
                        includeImageCapture = true,
                        includeVideoCapture = false,
                        includeEffect = true
                    ),
                    CameraBindAttempt(
                        includeImageCapture = true,
                        includeVideoCapture = false,
                        includeEffect = false
                    )
                )
            } else {
                listOf(
                    CameraBindAttempt(
                        includeImageCapture = true,
                        includeVideoCapture = false,
                        includeEffect = false
                    )
                )
            }
        }

        return if (preferVideoMode) {
            if (hasEffect) {
                listOf(
                    CameraBindAttempt(
                        includeImageCapture = true,
                        includeVideoCapture = true,
                        includeEffect = true
                    ),
                    CameraBindAttempt(
                        includeImageCapture = false,
                        includeVideoCapture = true,
                        includeEffect = true
                    ),
                    CameraBindAttempt(
                        includeImageCapture = false,
                        includeVideoCapture = true,
                        includeEffect = false
                    ),
                    CameraBindAttempt(
                        includeImageCapture = true,
                        includeVideoCapture = false,
                        includeEffect = true
                    ),
                    CameraBindAttempt(
                        includeImageCapture = true,
                        includeVideoCapture = false,
                        includeEffect = false
                    )
                )
            } else {
                listOf(
                    CameraBindAttempt(
                        includeImageCapture = true,
                        includeVideoCapture = true,
                        includeEffect = false
                    ),
                    CameraBindAttempt(
                        includeImageCapture = false,
                        includeVideoCapture = true,
                        includeEffect = false
                    ),
                    CameraBindAttempt(
                        includeImageCapture = true,
                        includeVideoCapture = false,
                        includeEffect = false
                    )
                )
            }
        } else {
            if (hasEffect) {
                listOf(
                    CameraBindAttempt(
                        includeImageCapture = true,
                        includeVideoCapture = true,
                        includeEffect = true
                    ),
                    CameraBindAttempt(
                        includeImageCapture = true,
                        includeVideoCapture = false,
                        includeEffect = true
                    ),
                    CameraBindAttempt(
                        includeImageCapture = true,
                        includeVideoCapture = true,
                        includeEffect = false
                    ),
                    CameraBindAttempt(
                        includeImageCapture = true,
                        includeVideoCapture = false,
                        includeEffect = false
                    )
                )
            } else {
                listOf(
                    CameraBindAttempt(
                        includeImageCapture = true,
                        includeVideoCapture = true,
                        includeEffect = false
                    ),
                    CameraBindAttempt(
                        includeImageCapture = true,
                        includeVideoCapture = false,
                        includeEffect = false
                    )
                )
            }
        }
    }

    fun resolveBindFallbackError(
        requestedVideoUseCase: Boolean,
        boundVideoUseCase: Boolean,
        requestedEffect: Boolean,
        boundEffect: Boolean,
        preferVideoMode: Boolean
    ): String? {
        val videoDroppedInVideoMode = preferVideoMode && requestedVideoUseCase && !boundVideoUseCase
        val effectDropped = requestedEffect && !boundEffect
        return when {
            videoDroppedInVideoMode && effectDropped ->
                "VideoCapture and CameraEffect disabled by device HAL fallback"
            videoDroppedInVideoMode ->
                "VideoCapture disabled by device HAL fallback"
            effectDropped ->
                "CameraEffect disabled by device HAL fallback"
            else -> null
        }
    }

    fun toCameraXAspectRatio(preset: AspectRatioPreset): Int = when (preset) {
        AspectRatioPreset.RATIO_16_9 -> androidx.camera.core.AspectRatio.RATIO_16_9
        AspectRatioPreset.RATIO_4_3,
        AspectRatioPreset.RATIO_3_2,
        AspectRatioPreset.RATIO_1_1 -> androidx.camera.core.AspectRatio.RATIO_4_3
    }
}
