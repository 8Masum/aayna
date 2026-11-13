package com.themasum.aayna.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureModelsTest {
    @Test
    fun `AaynaJson roundtrips CaptureRecipe and CameraProfile`() {
        val recipe =
            CaptureRecipe(
                recipeId = "rec-001",
                cameraId = CameraCatalog.nx07Nocturne.id,
                capturedAtEpochMs = 1794674481000L,
                randomSeed = 42L,
                resolutionPreset = ResolutionPreset.DIGICAM_5MP,
                opticalSettings =
                OpticalSettings(
                    evCompensation = -0.5f,
                    iso = 400,
                    shutterSpeedNs = 16_666_667L,
                    whiteBalance = WhiteBalancePreset.TUNGSTEN,
                    warmthShift = 0.1f,
                    bloomIntensity = 0.6f,
                    lensAttachment = LensAttachment.STAR_4PT,
                    lightLeakIntensity = 0.25f,
                    dustIntensity = 0.15f,
                    vignetteIntensity = 0.35f,
                    flashMode = FlashMode.OPTICAL_FLASH_SIM,
                    aspectRatio = AspectRatioPreset.RATIO_3_2,
                    frameStyle = FrameBorderStyle.FILM_35MM
                ),
                stampConfig =
                StampConfig(
                    enabled = true,
                    style = StampStyle.SEVEN_SEGMENT_EMERALD,
                    format = StampFormat.SHORT_YY_MM_DD,
                    useEraYearOffset = true,
                    eraYear = 2007,
                    glowStrength = 0.5f
                ),
                caption = "Night walk"
            )

        val encoded = AaynaJson.encodeRecipe(recipe)
        val decoded = AaynaJson.decodeRecipe(encoded)
        assertEquals(recipe, decoded)

        val profile = CameraCatalog.dc04SilverPulse
        val decodedProfile = AaynaJson.decodeProfile(AaynaJson.encodeProfile(profile))
        assertEquals(profile, decodedProfile)
    }

    @Test
    fun `DiagnosticsReport toShareableText includes key sections`() {
        val report =
            AaynaDiagnosticsReport(
                appVersion = "1.0.0",
                deviceModel = "Pixel Test",
                androidSdk = 35,
                activeCameraProfileId = "dc_04_silver_pulse",
                cameraCapability =
                CameraCapabilityInfo(
                    cameraId = "0",
                    hardwareLevel = "FULL",
                    hasManualSensor = true,
                    isoRange = "100..3200",
                    exposureRangeNs = "100000..500000000",
                    edgeModeApplied = "OFF",
                    noiseReductionApplied = "OFF"
                ),
                glInfo =
                GlDiagnosticsInfo(
                    glVendor = "TestVendor",
                    glRenderer = "TestRenderer",
                    glVersion = "OpenGL ES 3.2",
                    maxTextureSize = 4096,
                    targetFboSize = "2048x1536"
                ),
                shaderStatuses = listOf(PassShaderStatus("Pass1_Geometry", compiled = true))
            )

        val text = report.toShareableText()
        assertTrue(text.contains("Project Aayna Diagnostics"))
        assertTrue(text.contains("Pixel Test"))
        assertTrue(text.contains("Pass1_Geometry: OK"))
    }
}
