package com.themasum.aayna.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureModelsTest {
    @Test
    fun `AaynaJson roundtrips CaptureRecipe CameraProfile and UserSettingsSnapshot`() {
        val recipe =
            CaptureRecipe(
                recipeId = "rec-001",
                cameraId = CameraCatalog.m532.id,
                capturedAtEpochMs = 1794674481000L,
                randomSeed = 42L,
                mediaType = MediaType.VIDEO,
                skinId = SkinCatalog.charcoalMetal.skinId,
                resolutionPreset = ResolutionPreset.DIGICAM_5MP,
                opticalSettings =
                OpticalSettings(
                    evCompensation = -0.5f,
                    iso = 400,
                    shutterSpeedNs = 16_666_667L,
                    whiteBalance = WhiteBalancePreset.TUNGSTEN,
                    warmthShift = 0.35f,
                    bloomIntensity = 0.6f,
                    beautySoftness = 0.25f,
                    highlights = -0.2f,
                    shadows = 0.3f,
                    sharpen = 0.4f,
                    lensAttachment = LensAttachment.PRISM,
                    lightLeakIntensity = 0.25f,
                    dustIntensity = 0.15f,
                    vignetteIntensity = 0.35f,
                    flashMode = FlashMode.OPTICAL_FLASH_SIM,
                    aspectRatio = AspectRatioPreset.RATIO_3_2,
                    cropAspectRatio = AspectRatioPreset.RATIO_1_1,
                    straightenDegrees = 2.5f,
                    frameStyle = FrameBorderStyle.DV_TIMECODE
                ),
                stampConfig =
                StampConfig(
                    enabled = true,
                    style = StampStyle.SEVEN_SEGMENT_EMERALD,
                    fontStyle = StampFontStyle.VCR_OSD,
                    position = StampPosition.TOP_LEFT,
                    format = StampFormat.SHORT_YY_MM_DD,
                    useEraYearOffset = true,
                    eraYear = 2006,
                    glowStrength = 0.5f
                ),
                caption = "Night walk",
                videoDurationMs = 12_500L,
                burstFrameCount = 1
            )

        val encoded = AaynaJson.encodeRecipe(recipe)
        val decoded = AaynaJson.decodeRecipe(encoded)
        assertEquals(recipe, decoded)

        val profile = CameraCatalog.z30
        val decodedProfile = AaynaJson.decodeProfile(AaynaJson.encodeProfile(profile))
        assertEquals(profile, decodedProfile)

        val settings =
            UserSettingsSnapshot(
                storageMode = CaptureStorageMode.ANALOG_DEVELOPING_RITUAL,
                dualSaveEnabled = true,
                jpegQuality = 95,
                isFrontMirrorEnabled = false,
                useHardwareAeCompatibility = true,
                stampFontStyle = StampFontStyle.PIXEL,
                stampPosition = StampPosition.BOTTOM_LEFT,
                selectedSkinByCamera = mapOf("z30" to SkinCatalog.y2kCyan.skinId)
            )
        val decodedSettings = AaynaJson.decodeUserSettings(AaynaJson.encodeUserSettings(settings))
        assertEquals(settings, decodedSettings)
        assertEquals(SkinCatalog.y2kCyan.skinId, settings.skinIdForCamera("z30", SkinCatalog.silverBrushed.skinId))
        assertEquals(
            SkinCatalog.matteObsidian.skinId,
            settings.skinIdForCamera("m532", SkinCatalog.matteObsidian.skinId)
        )
    }

    @Test
    fun `OpticalSettings clamped enforces canonical slider contracts`() {
        val extreme =
            OpticalSettings(
                evCompensation = 5f,
                warmthShift = -2.5f,
                bloomIntensity = 2f,
                beautySoftness = -1f,
                highlights = 3f,
                shadows = -3f,
                sharpen = 5f,
                straightenDegrees = 45f
            ).clamped()

        assertEquals(OpticalSettings.MAX_EV, extreme.evCompensation, 0.0001f)
        assertEquals(OpticalSettings.MIN_WARMTH_SHIFT, extreme.warmthShift, 0.0001f)
        assertEquals(1f, extreme.bloomIntensity, 0.0001f)
        assertEquals(0f, extreme.beautySoftness, 0.0001f)
        assertEquals(1f, extreme.highlights, 0.0001f)
        assertEquals(-1f, extreme.shadows, 0.0001f)
        assertEquals(1f, extreme.sharpen, 0.0001f)
        assertEquals(OpticalSettings.MAX_STRAIGHTEN_DEGREES, extreme.straightenDegrees, 0.0001f)
    }

    @Test
    fun `UserSettingsSnapshot clamps jpeg quality and defaults era offset off`() {
        val defaultSettings = UserSettingsSnapshot()
        assertEquals(false, defaultSettings.useEraYearOffset)
        assertEquals(true, defaultSettings.isFrontMirrorEnabled)
        assertEquals(92, defaultSettings.clampedJpegQuality())

        assertEquals(60, UserSettingsSnapshot(jpegQuality = 10).clampedJpegQuality())
        assertEquals(100, UserSettingsSnapshot(jpegQuality = 120).clampedJpegQuality())
    }

    @Test
    fun `DiagnosticsReport toShareableText includes key sections and device quirks`() {
        val fingerprint = DeviceFingerprint("Xiaomi", "Redmi", "Note 9", hardware = "mt6768", sdkInt = 29)
        val quirks = DeviceQuirkRegistry.resolve(fingerprint)
        val report =
            AaynaDiagnosticsReport(
                appVersion = "2.0.0",
                deviceModel = "Pixel Test",
                androidSdk = 35,
                activeCameraProfileId = "z30",
                cameraCapability =
                CameraCapabilityInfo(
                    cameraId = "0",
                    hardwareLevel = "FULL",
                    hasManualSensor = true,
                    isoRange = "100..3200",
                    exposureRangeNs = "100000..500000000",
                    edgeModeApplied = "DEFAULT",
                    noiseReductionApplied = "DEFAULT"
                ),
                glInfo =
                GlDiagnosticsInfo(
                    glVendor = "TestVendor",
                    glRenderer = "TestRenderer",
                    glVersion = "OpenGL ES 3.2",
                    maxTextureSize = 4096,
                    targetFboSize = "2048x1536"
                ),
                shaderStatuses = listOf(PassShaderStatus("Pass1_Geometry", compiled = true)),
                deviceQuirks = quirks
            )

        val text = report.toShareableText()
        assertTrue(text.contains("Project Aayna Diagnostics"))
        assertTrue(text.contains("Pixel Test"))
        assertTrue(text.contains("Pass1_Geometry: OK"))
        assertTrue(text.contains("[Device Quirks]"))
        assertTrue(text.contains("mediatek_manual_ae_disable"))
    }
}
