package com.themasum.aayna

import com.themasum.aayna.core.model.CameraCapabilityInfo
import com.themasum.aayna.core.model.GlDiagnosticsInfo
import com.themasum.aayna.core.model.PassShaderStatus
import com.themasum.aayna.ui.DiagnosticsReportBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsReportBuilderTest {
    @Test
    fun `build populates fallback values when camera and gl diagnostics are null`() {
        val report =
            DiagnosticsReportBuilder.build(
                appVersion = "1.0.0 (1)",
                deviceModel = "Pixel 9",
                androidSdk = 35,
                activeCameraProfileId = "ccd_cool_2004",
                cameraCapability = null,
                glInfo = null,
                shaderStatuses = emptyList()
            )

        assertEquals("1.0.0 (1)", report.appVersion)
        assertEquals("Pixel 9", report.deviceModel)
        assertEquals(35, report.androidSdk)
        assertEquals("ccd_cool_2004", report.activeCameraProfileId)
        assertEquals("Not bound yet", report.cameraCapability.cameraId)
        assertFalse(report.cameraCapability.hasManualSensor)
        assertEquals("Not initialized", report.glInfo.glRenderer)
        assertTrue(report.shaderStatuses.isEmpty())
    }

    @Test
    fun `build preserves live camera2, gl, and shader status details in shareable text`() {
        val cap =
            CameraCapabilityInfo(
                cameraId = "0",
                hardwareLevel = "LEVEL_3",
                hasManualSensor = true,
                isoRange = "50..6400",
                exposureRangeNs = "10000..1000000000",
                edgeModeApplied = "OFF",
                noiseReductionApplied = "MINIMAL"
            )
        val gl =
            GlDiagnosticsInfo(
                glVendor = "Qualcomm",
                glRenderer = "Adreno (TM) 750",
                glVersion = "OpenGL ES 3.2",
                maxTextureSize = 16384,
                targetFboSize = "2560x1920"
            )
        val shaders =
            listOf(
                PassShaderStatus(passName = "Pass1_Halation", compiled = true),
                PassShaderStatus(passName = "Pass4_Grain", compiled = false, errorLog = "syntax error")
            )

        val report =
            DiagnosticsReportBuilder.build(
                appVersion = "1.2.0 (12)",
                deviceModel = "SM-S928B",
                androidSdk = 36,
                activeCameraProfileId = "kodak_gold_200",
                cameraCapability = cap,
                glInfo = gl,
                shaderStatuses = shaders
            )
        val text = report.toShareableText()

        assertTrue(text.contains("LEVEL_3"))
        assertTrue(text.contains("Adreno (TM) 750"))
        assertTrue(text.contains("Pass1_Halation: OK"))
        assertTrue(text.contains("Pass4_Grain: FAIL"))
        assertTrue(text.contains("syntax error"))
    }
}
