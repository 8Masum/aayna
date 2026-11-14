package com.themasum.aayna.core.model

import kotlinx.serialization.Serializable

@Serializable
data class DeviceFingerprint(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String = "",
    val hardware: String = "",
    val sdkInt: Int = 34
)

@Serializable
data class DeviceQuirkFlags(
    val forceCompatPreview: Boolean = false,
    val swapFrontMirror: Boolean = false,
    val clampAeRange: Boolean = false,
    val disableManualAe: Boolean = false,
    val disableEdgeOffOverride: Boolean = true,
    val disableNoiseReductionOffOverride: Boolean = true,
    val notes: String = ""
)

/**
 * Hardware quirk rule matched against [DeviceFingerprint] fields.
 *
 * String patterns accept comma-separated case-insensitive substrings (e.g. `"mt67,mt68,mediatek"`).
 * A null or blank pattern matches any device.
 */
@Serializable
data class DeviceQuirkRule(
    val id: String,
    val manufacturerPattern: String? = null,
    val brandPattern: String? = null,
    val modelPattern: String? = null,
    val hardwarePattern: String? = null,
    val minSdk: Int? = null,
    val maxSdk: Int? = null,
    val flags: DeviceQuirkFlags
) {
    fun matches(fingerprint: DeviceFingerprint): Boolean {
        if (minSdk != null && fingerprint.sdkInt < minSdk) return false
        if (maxSdk != null && fingerprint.sdkInt > maxSdk) return false
        if (!matchesPattern(fingerprint.manufacturer, manufacturerPattern)) return false
        if (!matchesPattern(fingerprint.brand, brandPattern)) return false
        if (!matchesPattern(fingerprint.model, modelPattern)) return false
        if (!matchesPattern(fingerprint.hardware, hardwarePattern)) return false
        return true
    }

    private fun matchesPattern(value: String, pattern: String?): Boolean {
        val raw = pattern?.trim().orEmpty()
        if (raw.isEmpty()) return true
        val tokens = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return true
        return tokens.any { token -> value.contains(token, ignoreCase = true) }
    }
}

@Serializable
data class ResolvedDeviceQuirks(
    val fingerprint: DeviceFingerprint,
    val flags: DeviceQuirkFlags,
    val matchedRuleIds: List<String> = emptyList()
)

object DeviceQuirkRegistry {
    val defaultRules: List<DeviceQuirkRule> =
        listOf(
            DeviceQuirkRule(
                id = "legacy_pre_android_10_compat_preview",
                maxSdk = 28,
                flags =
                DeviceQuirkFlags(
                    forceCompatPreview = true,
                    notes = "Force TextureView-compatible PreviewView on API <= 28."
                )
            ),
            DeviceQuirkRule(
                id = "mediatek_manual_ae_disable",
                hardwarePattern = "mt67,mt68,mt87,mediatek",
                flags =
                DeviceQuirkFlags(
                    clampAeRange = true,
                    disableManualAe = true,
                    notes = "Disable full manual AE_MODE_OFF and clamp EV range on MediaTek HALs."
                )
            ),
            DeviceQuirkRule(
                id = "unisoc_spreadtrum_compat",
                hardwarePattern = "unisoc,sprd,ums",
                flags =
                DeviceQuirkFlags(
                    forceCompatPreview = true,
                    clampAeRange = true,
                    disableManualAe = true,
                    notes = "Force compatible preview and safe AE bounds on Unisoc/Spreadtrum SoCs."
                )
            ),
            DeviceQuirkRule(
                id = "samsung_exynos_ae_clamp",
                manufacturerPattern = "samsung",
                hardwarePattern = "exynos,s5e",
                flags =
                DeviceQuirkFlags(
                    clampAeRange = true,
                    notes = "Clamp hardware EV compensation bounds on Exynos Camera2 HAL."
                )
            ),
            DeviceQuirkRule(
                id = "xiaomi_legacy_front_mirror",
                manufacturerPattern = "xiaomi,redmi,poco",
                maxSdk = 30,
                flags =
                DeviceQuirkFlags(
                    forceCompatPreview = true,
                    swapFrontMirror = true,
                    notes = "Compensate inverted front-camera sensor orientation on legacy MIUI HALs."
                )
            ),
            DeviceQuirkRule(
                id = "transsion_infinix_tecno_compat",
                manufacturerPattern = "tecno,infinix,itel,transsion",
                flags =
                DeviceQuirkFlags(
                    forceCompatPreview = true,
                    clampAeRange = true,
                    disableManualAe = true,
                    notes = "Enable TextureView preview and safe AE fallback on Transsion devices."
                )
            ),
            DeviceQuirkRule(
                id = "motorola_legacy_front_mirror",
                manufacturerPattern = "motorola",
                maxSdk = 31,
                flags =
                DeviceQuirkFlags(
                    swapFrontMirror = true,
                    notes = "Adjust front-camera horizontal mirror default on legacy Motorola HALs."
                )
            ),
            DeviceQuirkRule(
                id = "oppo_realme_legacy_preview_clamp",
                manufacturerPattern = "oppo,realme",
                maxSdk = 30,
                flags =
                DeviceQuirkFlags(
                    forceCompatPreview = true,
                    clampAeRange = true,
                    notes = "Force compatible preview surface and clamp EV steps on API <= 30."
                )
            )
        )

    fun resolve(
        fingerprint: DeviceFingerprint,
        rules: List<DeviceQuirkRule> = defaultRules,
        userAeCompatibilityEnabled: Boolean = false
    ): ResolvedDeviceQuirks {
        val matched = rules.filter { it.matches(fingerprint) }
        var forceCompatPreview = false
        var swapFrontMirror = false
        var clampAeRange = userAeCompatibilityEnabled
        var disableManualAe = userAeCompatibilityEnabled
        var disableEdgeOffOverride = true
        var disableNoiseReductionOffOverride = true
        val notes = mutableListOf<String>()

        for (rule in matched) {
            val f = rule.flags
            forceCompatPreview = forceCompatPreview || f.forceCompatPreview
            swapFrontMirror = swapFrontMirror || f.swapFrontMirror
            clampAeRange = clampAeRange || f.clampAeRange
            disableManualAe = disableManualAe || f.disableManualAe
            disableEdgeOffOverride = disableEdgeOffOverride && f.disableEdgeOffOverride
            disableNoiseReductionOffOverride =
                disableNoiseReductionOffOverride &&
                f.disableNoiseReductionOffOverride
            if (f.notes.isNotBlank()) {
                notes += f.notes
            }
        }
        if (userAeCompatibilityEnabled) {
            notes += "User AE compatibility fallback enabled."
        }

        return ResolvedDeviceQuirks(
            fingerprint = fingerprint,
            flags =
            DeviceQuirkFlags(
                forceCompatPreview = forceCompatPreview,
                swapFrontMirror = swapFrontMirror,
                clampAeRange = clampAeRange,
                disableManualAe = disableManualAe,
                disableEdgeOffOverride = disableEdgeOffOverride,
                disableNoiseReductionOffOverride = disableNoiseReductionOffOverride,
                notes = notes.distinct().joinToString(" ")
            ),
            matchedRuleIds = matched.map { it.id }
        )
    }
}
