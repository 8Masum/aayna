package com.themasum.aayna.ui

import com.themasum.aayna.core.model.AaynaDiagnosticsReport
import com.themasum.aayna.core.model.CameraCapabilityInfo
import com.themasum.aayna.core.model.GlDiagnosticsInfo
import com.themasum.aayna.core.model.PassShaderStatus
import com.themasum.aayna.core.model.ResolvedDeviceQuirks

object DiagnosticsReportBuilder {
    fun build(
        appVersion: String,
        deviceModel: String,
        androidSdk: Int,
        activeCameraProfileId: String,
        cameraCapability: CameraCapabilityInfo?,
        glInfo: GlDiagnosticsInfo?,
        shaderStatuses: List<PassShaderStatus>,
        deviceQuirks: ResolvedDeviceQuirks? = null
    ): AaynaDiagnosticsReport = AaynaDiagnosticsReport(
        appVersion = appVersion,
        deviceModel = deviceModel,
        androidSdk = androidSdk,
        activeCameraProfileId = activeCameraProfileId,
        cameraCapability = cameraCapability ?: defaultCameraCapability(),
        glInfo = glInfo ?: defaultGlDiagnostics(),
        shaderStatuses = shaderStatuses,
        deviceQuirks = deviceQuirks
    )

    fun resolveBestGlDiagnostics(
        vararg candidates: Pair<GlDiagnosticsInfo?, List<PassShaderStatus>>?
    ): Pair<GlDiagnosticsInfo?, List<PassShaderStatus>> {
        val nonNull = candidates.filterNotNull()
        val withInfo = nonNull.firstOrNull { it.first != null }
        if (withInfo != null) {
            val shaders =
                withInfo.second.ifEmpty {
                    nonNull.firstOrNull { it.second.isNotEmpty() }?.second ?: emptyList()
                }
            return withInfo.first to shaders
        }
        val withShaders = nonNull.firstOrNull { it.second.isNotEmpty() }
        return withShaders ?: (null to emptyList())
    }

    fun formatConsumerPipelineStatus(
        consumerName: String,
        diagnostics: Pair<GlDiagnosticsInfo?, List<PassShaderStatus>>?
    ): String {
        if (diagnostics == null) return "$consumerName: Not attached"
        val (info, statuses) = diagnostics
        if (info == null && statuses.isEmpty()) return "$consumerName: Standby (uninitialized)"
        val passedCount = statuses.count { it.compiled }
        val totalCount = statuses.size
        val rendererLabel = info?.glRenderer?.ifBlank { "Initialized" } ?: "Initialized"
        return if (totalCount > 0) {
            "$consumerName: $rendererLabel ($passedCount/$totalCount passes OK)"
        } else {
            "$consumerName: $rendererLabel"
        }
    }

    private fun defaultCameraCapability(): CameraCapabilityInfo = CameraCapabilityInfo(
        cameraId = "Not bound yet",
        hardwareLevel = "Unknown",
        hasManualSensor = false,
        isoRange = "N/A",
        exposureRangeNs = "N/A",
        edgeModeApplied = "N/A",
        noiseReductionApplied = "N/A"
    )

    private fun defaultGlDiagnostics(): GlDiagnosticsInfo = GlDiagnosticsInfo(
        glVendor = "Not initialized",
        glRenderer = "Not initialized",
        glVersion = "OpenGL ES 3.0+",
        maxTextureSize = 0,
        targetFboSize = "N/A"
    )
}
