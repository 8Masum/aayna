package com.themasum.aayna.core.camera

import android.hardware.camera2.CaptureRequest
import androidx.camera.core.AspectRatio
import com.themasum.aayna.core.model.AspectRatioPreset
import com.themasum.aayna.core.model.DeviceFingerprint
import com.themasum.aayna.core.model.DeviceQuirkFlags
import com.themasum.aayna.core.model.DeviceQuirkRegistry
import com.themasum.aayna.core.model.OpticalSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraPolicyMathTest {
    @Test
    fun `resolveAeDecision enables AE_MODE_OFF only for full manual on compliant sensor`() {
        val decision =
            CameraPolicyMath.resolveAeDecision(
                settings =
                OpticalSettings(
                    evCompensation = 1.0f,
                    iso = 3200,
                    shutterSpeedNs = 8_000_000L
                ),
                hasManualSensor = true,
                minIso = 100,
                maxIso = 1600,
                minExposureTimeNs = 1_000_000L,
                maxExposureTimeNs = 250_000_000L,
                aeCompensationMinIndex = -12,
                aeCompensationMaxIndex = 12,
                aeCompensationStepNumerator = 1,
                aeCompensationStepDenominator = 3,
                quirkFlags = DeviceQuirkFlags(),
                useHardwareAeCompatibility = false
            )

        assertTrue(decision.useManualAeOff)
        assertEquals(1600, decision.clampedIso)
        assertEquals(8_000_000L, decision.clampedExposureTimeNs)
        assertFalse(decision.hardwareEvApplied)
        assertEquals(0f, decision.shaderFallback.evGainStops, 0.001f)
        assertEquals(0f, decision.shaderFallback.digitalBoostStops, 0.001f)
        assertTrue(decision.shaderFallback.shaderNoiseBoost > 0f)
    }

    @Test
    fun `resolveAeDecision keeps AE_MODE_ON for partial manual and supplies shader fallback`() {
        val isoOnlyDecision =
            CameraPolicyMath.resolveAeDecision(
                settings = OpticalSettings(iso = 800, shutterSpeedNs = null, evCompensation = 1.0f),
                hasManualSensor = true,
                minIso = 100,
                maxIso = 1600,
                minExposureTimeNs = 1_000_000L,
                maxExposureTimeNs = 250_000_000L,
                aeCompensationMinIndex = -12,
                aeCompensationMaxIndex = 12,
                aeCompensationStepNumerator = 1,
                aeCompensationStepDenominator = 3,
                quirkFlags = DeviceQuirkFlags()
            )

        assertFalse(isoOnlyDecision.useManualAeOff)
        assertNull(isoOnlyDecision.clampedIso)
        assertTrue(isoOnlyDecision.hardwareEvApplied)
        assertEquals(3, isoOnlyDecision.evCompensationIndex)
        assertEquals(0f, isoOnlyDecision.shaderFallback.evGainStops, 0.001f)
        assertTrue(isoOnlyDecision.shaderFallback.digitalBoostStops > 0f)
    }

    @Test
    fun `resolveAeDecision blocks AE_MODE_OFF when disableManualAe or compatibility mode is active`() {
        val mtkQuirk =
            DeviceQuirkRegistry.resolve(
                DeviceFingerprint(
                    manufacturer = "Xiaomi",
                    brand = "Redmi",
                    model = "Redmi Note 12 Pro",
                    hardware = "mt6877",
                    sdkInt = 33
                )
            )
        assertTrue(mtkQuirk.flags.disableManualAe)

        val mtkDecision =
            CameraPolicyMath.resolveAeDecision(
                settings = OpticalSettings(iso = 400, shutterSpeedNs = 16_666_667L),
                hasManualSensor = true,
                minIso = 100,
                maxIso = 3200,
                minExposureTimeNs = 1_000_000L,
                maxExposureTimeNs = 500_000_000L,
                aeCompensationMinIndex = -12,
                aeCompensationMaxIndex = 12,
                aeCompensationStepNumerator = 1,
                aeCompensationStepDenominator = 3,
                quirkFlags = mtkQuirk.flags
            )
        assertFalse(mtkDecision.useManualAeOff)
        assertTrue(mtkDecision.shaderFallback.digitalBoostStops > 0f)

        val userCompatDecision =
            CameraPolicyMath.resolveAeDecision(
                settings = OpticalSettings(iso = 400, shutterSpeedNs = 16_666_667L),
                hasManualSensor = true,
                minIso = 100,
                maxIso = 3200,
                minExposureTimeNs = 1_000_000L,
                maxExposureTimeNs = 500_000_000L,
                aeCompensationMinIndex = -12,
                aeCompensationMaxIndex = 12,
                aeCompensationStepNumerator = 1,
                aeCompensationStepDenominator = 3,
                quirkFlags = DeviceQuirkFlags(),
                useHardwareAeCompatibility = true
            )
        assertFalse(userCompatDecision.useManualAeOff)
        assertTrue(userCompatDecision.shaderFallback.digitalBoostStops > 0f)
    }

    @Test
    fun `resolveAeDecision clamps EV range when clampAeRange is true and falls back when unbound`() {
        val clamped =
            CameraPolicyMath.resolveAeDecision(
                settings = OpticalSettings(evCompensation = 2.0f),
                hasManualSensor = false,
                minIso = 100,
                maxIso = 1600,
                minExposureTimeNs = 1_000_000L,
                maxExposureTimeNs = 250_000_000L,
                aeCompensationMinIndex = -24,
                aeCompensationMaxIndex = 24,
                aeCompensationStepNumerator = 1,
                aeCompensationStepDenominator = 6,
                quirkFlags = DeviceQuirkFlags(clampAeRange = true),
                hasBoundCamera = true
            )
        // 1.5 EV at 1/6 EV step = 9 steps (instead of 12 steps for 2.0 EV)
        assertEquals(9, clamped.evCompensationIndex)
        assertTrue(clamped.hardwareEvApplied)

        val unbound =
            CameraPolicyMath.resolveAeDecision(
                settings = OpticalSettings(evCompensation = -1.25f),
                hasManualSensor = false,
                minIso = 100,
                maxIso = 1600,
                minExposureTimeNs = 1_000_000L,
                maxExposureTimeNs = 250_000_000L,
                aeCompensationMinIndex = -12,
                aeCompensationMaxIndex = 12,
                aeCompensationStepNumerator = 1,
                aeCompensationStepDenominator = 3,
                quirkFlags = DeviceQuirkFlags(),
                hasBoundCamera = false
            )
        assertFalse(unbound.hardwareEvApplied)
        assertEquals(-1.25f, unbound.shaderFallback.evGainStops, 0.001f)
    }

    @Test
    fun `resolveDeviceQuirks matches expected OEM rules across sample devices`() {
        val pixel =
            DeviceQuirkRegistry.resolve(
                DeviceFingerprint(
                    manufacturer = "Google",
                    brand = "google",
                    model = "Pixel 8",
                    hardware = "zuma",
                    sdkInt = 34
                )
            )
        assertTrue(pixel.matchedRuleIds.isEmpty())
        assertFalse(pixel.flags.forceCompatPreview)
        assertFalse(pixel.flags.disableManualAe)
        assertTrue(pixel.flags.disableEdgeOffOverride)
        assertTrue(pixel.flags.disableNoiseReductionOffOverride)

        val samsungExynos =
            DeviceQuirkRegistry.resolve(
                DeviceFingerprint(
                    manufacturer = "samsung",
                    brand = "samsung",
                    model = "SM-S901B",
                    hardware = "exynos2200",
                    sdkInt = 34
                )
            )
        assertTrue(samsungExynos.matchedRuleIds.contains("samsung_exynos_ae_clamp"))
        assertTrue(samsungExynos.flags.clampAeRange)
        assertFalse(samsungExynos.flags.disableManualAe)

        val infinix =
            DeviceQuirkRegistry.resolve(
                DeviceFingerprint(
                    manufacturer = "INFINIX",
                    brand = "Infinix",
                    model = "Infinix X6831",
                    hardware = "mt6789",
                    sdkInt = 33
                )
            )
        assertTrue(infinix.flags.forceCompatPreview)
        assertTrue(infinix.flags.clampAeRange)
        assertTrue(infinix.flags.disableManualAe)

        val legacyXiaomi =
            DeviceQuirkRegistry.resolve(
                DeviceFingerprint(
                    manufacturer = "Xiaomi",
                    brand = "Redmi",
                    model = "M2007J20CG",
                    hardware = "qcom",
                    sdkInt = 30
                )
            )
        assertTrue(legacyXiaomi.flags.forceCompatPreview)
        assertTrue(legacyXiaomi.flags.swapFrontMirror)

        val api28 =
            DeviceQuirkRegistry.resolve(
                DeviceFingerprint(
                    manufacturer = "Google",
                    brand = "google",
                    model = "Pixel 2",
                    hardware = "walleye",
                    sdkInt = 28
                )
            )
        assertTrue(api28.flags.forceCompatPreview)
    }

    @Test
    fun `resolveIspOverrides avoids unconditional EDGE_MODE_OFF and NR_OFF by default`() {
        val edgeModes = intArrayOf(CaptureRequest.EDGE_MODE_OFF, CaptureRequest.EDGE_MODE_FAST)
        val nrModes =
            intArrayOf(
                CaptureRequest.NOISE_REDUCTION_MODE_OFF,
                CaptureRequest.NOISE_REDUCTION_MODE_MINIMAL
            )

        val guarded =
            CameraPolicyMath.resolveIspOverrides(
                availableEdgeModes = edgeModes,
                availableNoiseReductionModes = nrModes,
                quirkFlags = DeviceQuirkFlags()
            )
        assertNull(guarded.edgeMode)
        assertEquals("DEFAULT", guarded.edgeModeLabel)
        assertNull(guarded.noiseReductionMode)
        assertEquals("DEFAULT", guarded.noiseReductionLabel)

        val unguarded =
            CameraPolicyMath.resolveIspOverrides(
                availableEdgeModes = edgeModes,
                availableNoiseReductionModes = nrModes,
                quirkFlags =
                DeviceQuirkFlags(
                    disableEdgeOffOverride = false,
                    disableNoiseReductionOffOverride = false
                )
            )
        assertEquals(CaptureRequest.EDGE_MODE_OFF, unguarded.edgeMode)
        assertEquals("OFF", unguarded.edgeModeLabel)
        assertEquals(CaptureRequest.NOISE_REDUCTION_MODE_OFF, unguarded.noiseReductionMode)
        assertEquals("OFF", unguarded.noiseReductionLabel)
    }

    @Test
    fun `resolvePreviewBindingPolicy honors compat quirk and effect fallback`() {
        val quirkForced =
            CameraPolicyMath.resolvePreviewBindingPolicy(
                preferCompatPreview = false,
                hasSurfaceProcessor = true,
                quirkFlags = DeviceQuirkFlags(forceCompatPreview = true),
                effectBindingFailed = false
            )
        assertTrue(quirkForced.useCompatPreview)
        assertTrue(quirkForced.enableCameraEffect)

        val effectFallback =
            CameraPolicyMath.resolvePreviewBindingPolicy(
                preferCompatPreview = true,
                hasSurfaceProcessor = true,
                quirkFlags = DeviceQuirkFlags(),
                effectBindingFailed = true
            )
        assertTrue(effectFallback.useCompatPreview)
        assertFalse(effectFallback.enableCameraEffect)
    }

    @Test
    fun `ZoomBounds clamps ratios and computes rocker presets`() {
        val ultraWide = ZoomBounds(minZoomRatio = 0.5f, maxZoomRatio = 10f)
        assertTrue(ultraWide.supportsUltraWide)
        assertEquals(0.5f, ultraWide.clampRatio(0.2f), 0.001f)
        assertEquals(10f, ultraWide.clampRatio(15f), 0.001f)
        assertEquals(listOf(0.5f, 1f, 2f, 3f), ultraWide.availablePresetRatios())

        val standardRear = ZoomBounds(minZoomRatio = 1f, maxZoomRatio = 4f)
        assertFalse(standardRear.supportsUltraWide)
        assertEquals(listOf(1f, 2f, 3f), standardRear.availablePresetRatios())

        val fixedFront = ZoomBounds(minZoomRatio = 1f, maxZoomRatio = 1f)
        assertEquals(listOf(1f), fixedFront.availablePresetRatios())
    }

    @Test
    fun `toCameraXAspectRatio maps AspectRatioPreset cleanly`() {
        assertEquals(AspectRatio.RATIO_16_9, CameraPolicyMath.toCameraXAspectRatio(AspectRatioPreset.RATIO_16_9))
        assertEquals(AspectRatio.RATIO_4_3, CameraPolicyMath.toCameraXAspectRatio(AspectRatioPreset.RATIO_4_3))
        assertEquals(AspectRatio.RATIO_4_3, CameraPolicyMath.toCameraXAspectRatio(AspectRatioPreset.RATIO_3_2))
        assertEquals(AspectRatio.RATIO_4_3, CameraPolicyMath.toCameraXAspectRatio(AspectRatioPreset.RATIO_1_1))
    }
}
