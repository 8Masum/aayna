package com.themasum.aayna.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExposureMathTest {
    @Test
    fun `clampIso handles null and range boundaries`() {
        assertNull(ExposureMath.clampIso(null, 100, 1600))
        assertEquals(100, ExposureMath.clampIso(50, 100, 1600))
        assertEquals(1600, ExposureMath.clampIso(3200, 100, 1600))
        assertEquals(400, ExposureMath.clampIso(400, 100, 1600))
    }

    @Test
    fun `clampExposureTimeNs handles null and range boundaries`() {
        assertNull(ExposureMath.clampExposureTimeNs(null, 1_000_000L, 250_000_000L))
        assertEquals(1_000_000L, ExposureMath.clampExposureTimeNs(500_000L, 1_000_000L, 250_000_000L))
        assertEquals(250_000_000L, ExposureMath.clampExposureTimeNs(500_000_000L, 1_000_000L, 250_000_000L))
        assertEquals(16_666_667L, ExposureMath.clampExposureTimeNs(16_666_667L, 1_000_000L, 250_000_000L))
    }

    @Test
    fun `evToCompensationSteps converts EV to clamped hardware steps`() {
        assertEquals(6, ExposureMath.evToCompensationSteps(1.0f, 1, 6, -12, 12))
        assertEquals(-12, ExposureMath.evToCompensationSteps(-3.0f, 1, 6, -12, 12))
        assertEquals(0, ExposureMath.evToCompensationSteps(1.0f, 0, 6, -12, 12))
    }

    @Test
    fun `isFullManualExposure requires manual sensor and both ISO and shutter`() {
        assertTrue(ExposureMath.isFullManualExposure(true, iso = 400, shutterNs = 16_666_667L))
        assertFalse(ExposureMath.isFullManualExposure(false, iso = 400, shutterNs = 16_666_667L))
        assertFalse(ExposureMath.isFullManualExposure(true, iso = 400, shutterNs = null))
        assertFalse(ExposureMath.isFullManualExposure(true, iso = null, shutterNs = 16_666_667L))
        assertFalse(ExposureMath.isFullManualExposure(true, iso = null, shutterNs = null))
    }

    @Test
    fun `computeShaderFallbackGains differentiates full manual vs AE and partial manual`() {
        val manual = ExposureMath.computeShaderFallbackGains(true, ev = 1.0f, iso = 800, shutterNs = 1_000_000L)
        assertEquals(0f, manual.evGainStops, 0.001f)
        assertEquals(0f, manual.digitalBoostStops, 0.001f)
        assertTrue(manual.shaderNoiseBoost > 0f)

        val fallback = ExposureMath.computeShaderFallbackGains(false, ev = 1.5f, iso = 800, shutterNs = 33_333_333L)
        assertEquals(1.5f, fallback.evGainStops, 0.001f)
        assertTrue(fallback.digitalBoostStops > 0f)
        assertTrue(fallback.shaderNoiseBoost > 0f)

        val manualSensorAe = ExposureMath.computeShaderFallbackGains(true, ev = -1.25f, iso = null, shutterNs = null)
        assertEquals(-1.25f, manualSensorAe.evGainStops, 0.001f)
        assertEquals(0f, manualSensorAe.digitalBoostStops, 0.001f)

        val manualSensorHardwareEv =
            ExposureMath.computeShaderFallbackGains(
                hasManualSensor = true,
                ev = -1.25f,
                iso = null,
                shutterNs = null,
                hardwareEvApplied = true
            )
        assertEquals(0f, manualSensorHardwareEv.evGainStops, 0.001f)

        val partialIsoOnly = ExposureMath.computeShaderFallbackGains(true, ev = 0.75f, iso = 400, shutterNs = null)
        assertEquals(0.75f, partialIsoOnly.evGainStops, 0.001f)
        assertTrue(partialIsoOnly.digitalBoostStops > 0f)
        assertTrue(partialIsoOnly.shaderNoiseBoost > 0f)

        val partialShutterOnly =
            ExposureMath.computeShaderFallbackGains(true, ev = -0.5f, iso = null, shutterNs = 1_000_000L)
        assertEquals(-0.5f, partialShutterOnly.evGainStops, 0.001f)
        assertTrue(partialShutterOnly.digitalBoostStops < 0f)
    }

    @Test
    fun `iso 50 applies negative stops relative to iso 100`() {
        val iso50Studio =
            ExposureMath.computeShaderFallbackGains(
                hasManualSensor = false,
                ev = 0f,
                iso = 50,
                shutterNs = null
            )
        assertEquals(-0.35f, iso50Studio.digitalBoostStops, 0.001f)
        assertEquals(0f, iso50Studio.shaderNoiseBoost, 0.001f)

        val iso50PartialOnManual =
            ExposureMath.computeShaderFallbackGains(
                hasManualSensor = true,
                ev = 0f,
                iso = 50,
                shutterNs = null
            )
        assertEquals(-0.35f, iso50PartialOnManual.digitalBoostStops, 0.001f)
        assertEquals(0f, iso50PartialOnManual.shaderNoiseBoost, 0.001f)
    }
}
