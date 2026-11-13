package com.themasum.aayna.ui

import com.themasum.aayna.core.model.AaynaDiagnosticsReport
import com.themasum.aayna.core.model.CameraCapabilityInfo
import com.themasum.aayna.core.model.GlDiagnosticsInfo
import com.themasum.aayna.core.model.PassShaderStatus

object DiagnosticsReportBuilder {
    fun build(
        appVersion: String,
        deviceModel: String,
        androidSdk: Int,
        activeCameraProfileId: String,
        cameraCapability: CameraCapabilityInfo?,
        glInfo: GlDiagnosticsInfo?,
        shaderStatuses: List<PassShaderStatus>
    ): AaynaDiagnosticsReport = AaynaDiagnosticsReport(
        appVersion = appVersion,
        deviceModel = deviceModel,
        androidSdk = androidSdk,
        activeCameraProfileId = activeCameraProfileId,
        cameraCapability = cameraCapability ?: defaultCameraCapability(),
        glInfo = glInfo ?: defaultGlDiagnostics(),
        shaderStatuses = shaderStatuses
    )

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
