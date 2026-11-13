package com.themasum.aayna.core.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import com.themasum.aayna.core.model.CameraCapabilityInfo

internal data class CameraHardwareBounds(
    val cameraId: String,
    val hardwareLevel: String,
    val hasManualSensor: Boolean,
    val minIso: Int,
    val maxIso: Int,
    val minExposureTimeNs: Long,
    val maxExposureTimeNs: Long,
    val aeCompensationMinIndex: Int,
    val aeCompensationMaxIndex: Int,
    val aeCompensationStepNumerator: Int,
    val aeCompensationStepDenominator: Int,
    val supportedAwbModes: Set<Int>,
    val preferredEdgeMode: Int?,
    val preferredNoiseReductionMode: Int?,
    val hasFlashUnit: Boolean,
    val sensorOrientationDegrees: Int
)

internal data class CameraProbeResult(val capabilityInfo: CameraCapabilityInfo, val bounds: CameraHardwareBounds)

object Camera2CapabilityProbe {
    private const val DEFAULT_MIN_ISO = 100
    private const val DEFAULT_MAX_ISO = 1600
    private const val DEFAULT_MIN_EXPOSURE_NS = 1_000_000L
    private const val DEFAULT_MAX_EXPOSURE_NS = 250_000_000L

    fun probeCapabilityInfo(cameraManager: CameraManager, cameraId: String): CameraCapabilityInfo =
        probe(cameraManager, cameraId).capabilityInfo

    fun probeCharacteristics(cameraId: String, characteristics: CameraCharacteristics): CameraCapabilityInfo =
        probeCharacteristicsDetailed(cameraId, characteristics).capabilityInfo

    internal fun probe(cameraManager: CameraManager, cameraId: String): CameraProbeResult {
        val characteristics =
            try {
                cameraManager.getCameraCharacteristics(cameraId)
            } catch (_: Exception) {
                return fallbackProbeResult(cameraId)
            }
        return probeCharacteristicsDetailed(cameraId, characteristics)
    }

    internal fun probeCharacteristicsDetailed(
        cameraId: String,
        characteristics: CameraCharacteristics
    ): CameraProbeResult {
        val hardwareLevel =
            mapHardwareLevel(
                safeGet(characteristics, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
            )
        val capabilities =
            safeGet(characteristics, CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                ?: IntArray(0)
        val hasManualSensor =
            capabilities.contains(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR
            )

        val sensitivityRange =
            safeGet(characteristics, CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val minIso = sensitivityRange?.lower ?: DEFAULT_MIN_ISO
        val maxIso = sensitivityRange?.upper ?: DEFAULT_MAX_ISO
        val isoRangeText =
            if (sensitivityRange != null) {
                "${sensitivityRange.lower}..${sensitivityRange.upper}"
            } else {
                "Unavailable"
            }

        val exposureRange =
            safeGet(characteristics, CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val minExposureNs = exposureRange?.lower ?: DEFAULT_MIN_EXPOSURE_NS
        val maxExposureNs = exposureRange?.upper ?: DEFAULT_MAX_EXPOSURE_NS
        val exposureRangeText =
            if (exposureRange != null) {
                "${exposureRange.lower}..${exposureRange.upper}"
            } else {
                "Unavailable"
            }

        val aeRange =
            safeGet(characteristics, CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
        val aeStep =
            safeGet(characteristics, CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)

        val awbModes =
            safeGet(characteristics, CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)
                ?.toSet()
                ?: emptySet()

        val edgeModes =
            safeGet(characteristics, CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES)
                ?: IntArray(0)
        val (preferredEdgeMode, edgeLabel) = resolvePreferredEdgeMode(edgeModes)

        val nrModes =
            safeGet(
                characteristics,
                CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES
            ) ?: IntArray(0)
        val (preferredNrMode, nrLabel) = resolvePreferredNoiseReductionMode(nrModes)

        val hasFlashUnit =
            safeGet(characteristics, CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
        val sensorOrientation =
            safeGet(characteristics, CameraCharacteristics.SENSOR_ORIENTATION) ?: 0

        val capabilityInfo =
            CameraCapabilityInfo(
                cameraId = cameraId,
                hardwareLevel = hardwareLevel,
                hasManualSensor = hasManualSensor,
                isoRange = isoRangeText,
                exposureRangeNs = exposureRangeText,
                edgeModeApplied = edgeLabel,
                noiseReductionApplied = nrLabel
            )
        val bounds =
            CameraHardwareBounds(
                cameraId = cameraId,
                hardwareLevel = hardwareLevel,
                hasManualSensor = hasManualSensor,
                minIso = minIso,
                maxIso = maxIso,
                minExposureTimeNs = minExposureNs,
                maxExposureTimeNs = maxExposureNs,
                aeCompensationMinIndex = aeRange?.lower ?: 0,
                aeCompensationMaxIndex = aeRange?.upper ?: 0,
                aeCompensationStepNumerator = aeStep?.numerator ?: 1,
                aeCompensationStepDenominator = aeStep?.denominator ?: 1,
                supportedAwbModes = awbModes,
                preferredEdgeMode = preferredEdgeMode,
                preferredNoiseReductionMode = preferredNrMode,
                hasFlashUnit = hasFlashUnit,
                sensorOrientationDegrees = sensorOrientation
            )
        return CameraProbeResult(capabilityInfo = capabilityInfo, bounds = bounds)
    }

    private fun resolvePreferredEdgeMode(availableModes: IntArray): Pair<Int?, String> = when {
        availableModes.contains(CaptureRequest.EDGE_MODE_OFF) -> {
            CaptureRequest.EDGE_MODE_OFF to "OFF"
        }

        availableModes.contains(CaptureRequest.EDGE_MODE_FAST) -> {
            CaptureRequest.EDGE_MODE_FAST to "FAST"
        }

        else -> {
            null to "DEFAULT"
        }
    }

    private fun resolvePreferredNoiseReductionMode(availableModes: IntArray): Pair<Int?, String> = when {
        availableModes.contains(CaptureRequest.NOISE_REDUCTION_MODE_OFF) -> {
            CaptureRequest.NOISE_REDUCTION_MODE_OFF to "OFF"
        }

        availableModes.contains(CaptureRequest.NOISE_REDUCTION_MODE_MINIMAL) -> {
            CaptureRequest.NOISE_REDUCTION_MODE_MINIMAL to "MINIMAL"
        }

        else -> {
            null to "DEFAULT"
        }
    }

    private fun mapHardwareLevel(level: Int?): String = when (level) {
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
        else -> "UNKNOWN"
    }

    private fun <T> safeGet(characteristics: CameraCharacteristics, key: CameraCharacteristics.Key<T>): T? = try {
        characteristics.get(key)
    } catch (_: Exception) {
        null
    }

    private fun fallbackProbeResult(cameraId: String): CameraProbeResult {
        val capabilityInfo =
            CameraCapabilityInfo(
                cameraId = cameraId,
                hardwareLevel = "UNKNOWN",
                hasManualSensor = false,
                isoRange = "Unavailable",
                exposureRangeNs = "Unavailable",
                edgeModeApplied = "DEFAULT",
                noiseReductionApplied = "DEFAULT"
            )
        val bounds =
            CameraHardwareBounds(
                cameraId = cameraId,
                hardwareLevel = "UNKNOWN",
                hasManualSensor = false,
                minIso = DEFAULT_MIN_ISO,
                maxIso = DEFAULT_MAX_ISO,
                minExposureTimeNs = DEFAULT_MIN_EXPOSURE_NS,
                maxExposureTimeNs = DEFAULT_MAX_EXPOSURE_NS,
                aeCompensationMinIndex = 0,
                aeCompensationMaxIndex = 0,
                aeCompensationStepNumerator = 1,
                aeCompensationStepDenominator = 1,
                supportedAwbModes = emptySet(),
                preferredEdgeMode = null,
                preferredNoiseReductionMode = null,
                hasFlashUnit = false,
                sensorOrientationDegrees = 0
            )
        return CameraProbeResult(capabilityInfo = capabilityInfo, bounds = bounds)
    }
}
