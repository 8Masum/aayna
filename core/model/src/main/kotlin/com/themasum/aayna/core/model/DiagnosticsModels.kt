package com.themasum.aayna.core.model

import kotlinx.serialization.Serializable

@Serializable
data class CameraCapabilityInfo(
    val cameraId: String,
    val hardwareLevel: String,
    val hasManualSensor: Boolean,
    val isoRange: String,
    val exposureRangeNs: String,
    val edgeModeApplied: String,
    val noiseReductionApplied: String
)

@Serializable
data class GlDiagnosticsInfo(
    val glVendor: String,
    val glRenderer: String,
    val glVersion: String,
    val maxTextureSize: Int,
    val targetFboSize: String
)

@Serializable
data class PassShaderStatus(val passName: String, val compiled: Boolean, val errorLog: String = "")

@Serializable
data class AaynaDiagnosticsReport(
    val appVersion: String,
    val deviceModel: String,
    val androidSdk: Int,
    val activeCameraProfileId: String,
    val cameraCapability: CameraCapabilityInfo,
    val glInfo: GlDiagnosticsInfo,
    val shaderStatuses: List<PassShaderStatus> = emptyList(),
    val deviceQuirks: ResolvedDeviceQuirks? = null
) {
    fun toShareableText(): String = buildString {
        appendLine("=== Project Aayna Diagnostics ===")
        appendLine("App Version: $appVersion")
        appendLine("Device: $deviceModel (API $androidSdk)")
        appendLine("Active Profile: $activeCameraProfileId")
        appendLine()
        appendLine("[Camera2 Capability]")
        appendLine("Camera ID: ${cameraCapability.cameraId}")
        appendLine("Hardware Level: ${cameraCapability.hardwareLevel}")
        appendLine("Manual Sensor: ${cameraCapability.hasManualSensor}")
        appendLine("ISO Range: ${cameraCapability.isoRange}")
        appendLine("Exposure Range (ns): ${cameraCapability.exposureRangeNs}")
        appendLine("Edge Mode: ${cameraCapability.edgeModeApplied}")
        appendLine("Noise Reduction: ${cameraCapability.noiseReductionApplied}")
        appendLine()
        appendLine("[OpenGL ES]")
        appendLine("Vendor: ${glInfo.glVendor}")
        appendLine("Renderer: ${glInfo.glRenderer}")
        appendLine("Version: ${glInfo.glVersion}")
        appendLine("GL_MAX_TEXTURE_SIZE: ${glInfo.maxTextureSize}")
        appendLine("Target FBO: ${glInfo.targetFboSize}")
        appendLine()
        appendLine("[Shader Passes]")
        if (shaderStatuses.isEmpty()) {
            appendLine("No shader statuses recorded.")
        } else {
            for (status in shaderStatuses) {
                val state = if (status.compiled) "OK" else "FAIL"
                appendLine("- ${status.passName}: $state")
                if (!status.compiled && status.errorLog.isNotBlank()) {
                    appendLine("  Error: ${status.errorLog}")
                }
            }
        }
        deviceQuirks?.let { quirks ->
            appendLine()
            appendLine("[Device Quirks]")
            appendLine("Matched Rules: ${quirks.matchedRuleIds.ifEmpty { listOf("none") }.joinToString(", ")}")
            appendLine("Force Compat Preview: ${quirks.flags.forceCompatPreview}")
            appendLine("Swap Front Mirror: ${quirks.flags.swapFrontMirror}")
            appendLine("Clamp AE Range: ${quirks.flags.clampAeRange}")
            appendLine("Disable Manual AE: ${quirks.flags.disableManualAe}")
            if (quirks.flags.notes.isNotBlank()) {
                appendLine("Notes: ${quirks.flags.notes}")
            }
        }
    }
}
