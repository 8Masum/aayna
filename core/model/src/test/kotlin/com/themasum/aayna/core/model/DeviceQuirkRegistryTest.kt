package com.themasum.aayna.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceQuirkRegistryTest {
    @Test
    fun `modern pixel matches no OEM quirk rules by default`() {
        val pixel =
            DeviceFingerprint(
                manufacturer = "Google",
                brand = "google",
                model = "Pixel 9",
                device = "tokay",
                hardware = "tokay",
                sdkInt = 35
            )
        val resolved = DeviceQuirkRegistry.resolve(pixel)
        assertTrue(resolved.matchedRuleIds.isEmpty())
        assertFalse(resolved.flags.forceCompatPreview)
        assertFalse(resolved.flags.swapFrontMirror)
        assertFalse(resolved.flags.clampAeRange)
        assertFalse(resolved.flags.disableManualAe)
        assertTrue(resolved.flags.disableEdgeOffOverride)
        assertTrue(resolved.flags.disableNoiseReductionOffOverride)
    }

    @Test
    fun `mediatek and legacy xiaomi devices trigger expected quirks`() {
        val xiaomiMtk =
            DeviceFingerprint(
                manufacturer = "Xiaomi",
                brand = "Redmi",
                model = "M2004J19C",
                hardware = "mt6768",
                sdkInt = 29
            )
        val resolved = DeviceQuirkRegistry.resolve(xiaomiMtk)
        assertTrue("mediatek_manual_ae_disable" in resolved.matchedRuleIds)
        assertTrue("xiaomi_legacy_front_mirror" in resolved.matchedRuleIds)
        assertTrue(resolved.flags.forceCompatPreview)
        assertTrue(resolved.flags.swapFrontMirror)
        assertTrue(resolved.flags.clampAeRange)
        assertTrue(resolved.flags.disableManualAe)
    }

    @Test
    fun `samsung exynos clamps AE without disabling manual or swapping mirror`() {
        val exynos =
            DeviceFingerprint(
                manufacturer = "samsung",
                brand = "samsung",
                model = "SM-S911B",
                hardware = "s5e9925",
                sdkInt = 34
            )
        val resolved = DeviceQuirkRegistry.resolve(exynos)
        assertEquals(listOf("samsung_exynos_ae_clamp"), resolved.matchedRuleIds)
        assertTrue(resolved.flags.clampAeRange)
        assertFalse(resolved.flags.disableManualAe)
        assertFalse(resolved.flags.swapFrontMirror)
        assertFalse(resolved.flags.forceCompatPreview)
    }

    @Test
    fun `user compatibility toggle enforces AE clamp and manual AE disable`() {
        val pixel =
            DeviceFingerprint(
                manufacturer = "Google",
                brand = "google",
                model = "Pixel 9",
                hardware = "tokay",
                sdkInt = 35
            )
        val resolved = DeviceQuirkRegistry.resolve(pixel, userAeCompatibilityEnabled = true)
        assertTrue(resolved.flags.clampAeRange)
        assertTrue(resolved.flags.disableManualAe)
        assertTrue(resolved.flags.notes.contains("User AE compatibility"))
    }

    @Test
    fun `quirk rules roundtrip through AaynaJson`() {
        val encoded = AaynaJson.encodeQuirkRules(DeviceQuirkRegistry.defaultRules)
        val decoded = AaynaJson.decodeQuirkRules(encoded)
        assertEquals(DeviceQuirkRegistry.defaultRules, decoded)
    }
}
