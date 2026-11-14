package com.themasum.aayna

import com.themasum.aayna.core.model.CameraCapabilityInfo
import com.themasum.aayna.core.model.DeviceFingerprint
import com.themasum.aayna.core.model.DeviceQuirkRegistry
import com.themasum.aayna.core.model.GlDiagnosticsInfo
import com.themasum.aayna.core.model.PassShaderStatus
import com.themasum.aayna.ui.DiagnosticsReportBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsReportBuilderTest {
    @Test
    fun `build populates fallback values when camera and gl diagnostics are null`() {
        val report =
            DiagnosticsReportBuilder.build(
                appVersion = "2.0.0 (2)",
                deviceModel = "Pixel 9",
                androidSdk = 35,
                activeCameraProfileId = "z30",
                cameraCapability = null,
                glInfo = null,
                shaderStatuses = emptyList()
            )

        assertEquals("2.0.0 (2)", report.appVersion)
        assertEquals("Pixel 9", report.deviceModel)
        assertEquals(35, report.androidSdk)
        assertEquals("z30", report.activeCameraProfileId)
        assertEquals("Not bound yet", report.cameraCapability.cameraId)
        assertFalse(report.cameraCapability.hasManualSensor)
        assertEquals("Not initialized", report.glInfo.glRenderer)
        assertTrue(report.shaderStatuses.isEmpty())
        assertNull(report.deviceQuirks)
    }

    @Test
    fun `build preserves live camera2, gl, shader status, and device quirks in shareable text`() {
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
        val quirks =
            DeviceQuirkRegistry.resolve(
                fingerprint =
                DeviceFingerprint(
                    manufacturer = "Xiaomi",
                    brand = "Redmi",
                    model = "M2007J20CG",
                    hardware = "mt6853",
                    sdkInt = 30
                ),
                userAeCompatibilityEnabled = true
            )

        val report =
            DiagnosticsReportBuilder.build(
                appVersion = "2.0.0 (2)",
                deviceModel = "M2007J20CG",
                androidSdk = 30,
                activeCameraProfileId = "z30",
                cameraCapability = cap,
                glInfo = gl,
                shaderStatuses = shaders,
                deviceQuirks = quirks
            )
        val text = report.toShareableText()

        assertNotNull(report.deviceQuirks)
        assertTrue(text.contains("LEVEL_3"))
        assertTrue(text.contains("Adreno (TM) 750"))
        assertTrue(text.contains("Pass1_Halation: OK"))
        assertTrue(text.contains("Pass4_Grain: FAIL"))
        assertTrue(text.contains("syntax error"))
        assertTrue(text.contains("[Device Quirks]"))
        assertTrue(text.contains("mediatek_manual_ae_disable"))
        assertTrue(text.contains("xiaomi_legacy_front_mirror"))
    }

    @Test
    fun `resolveBestGlDiagnostics prefers initialized gl info and merges shader statuses`() {
        val gl =
            GlDiagnosticsInfo(
                glVendor = "ARM",
                glRenderer = "Mali-G715",
                glVersion = "OpenGL ES 3.2",
                maxTextureSize = 8192,
                targetFboSize = "1920x1440"
            )
        val shaders = listOf(PassShaderStatus("Pass1", compiled = true))

        val resolved =
            DiagnosticsReportBuilder.resolveBestGlDiagnostics(
                null to emptyList(),
                gl to emptyList(),
                null to shaders
            )

        assertEquals(gl, resolved.first)
        assertEquals(shaders, resolved.second)
    }

    @Test
    fun `formatConsumerPipelineStatus summarizes attached and standby consumers`() {
        assertEquals(
            "SurfaceProcessor: Not attached",
            DiagnosticsReportBuilder.formatConsumerPipelineStatus("SurfaceProcessor", null)
        )
        assertEquals(
            "Studio: Standby (uninitialized)",
            DiagnosticsReportBuilder.formatConsumerPipelineStatus("Studio", null to emptyList())
        )
        val gl =
            GlDiagnosticsInfo(
                glVendor = "Qualcomm",
                glRenderer = "Adreno 740",
                glVersion = "3.2",
                maxTextureSize = 16384,
                targetFboSize = "1080x1440"
            )
        val shaders =
            listOf(
                PassShaderStatus("Pass1", compiled = true),
                PassShaderStatus("Pass2", compiled = true)
            )
        assertEquals(
            "Exporter: Adreno 740 (2/2 passes OK)",
            DiagnosticsReportBuilder.formatConsumerPipelineStatus("Exporter", gl to shaders)
        )
    }
}
