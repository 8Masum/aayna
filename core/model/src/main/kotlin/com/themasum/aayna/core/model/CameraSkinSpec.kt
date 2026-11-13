package com.themasum.aayna.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ChassisMaterial {
    BRUSHED_SILVER,
    ANODIZED_GUNMETAL,
    RIBBED_PLASTIC,
    WEATHERED_GRUNGE_SLATE,
    PEBBLE_LEATHERETTE,
    WARM_CREAM_INSTANT
}

@Serializable
enum class LcdTheme {
    CYAN_DOT_MATRIX,
    EMERALD_SEVEN_SEG,
    AMBER_COUNTER,
    WARM_MONOCHROME
}

@Serializable
enum class ShutterStyle {
    PILL_CHROME,
    RECESSED_METAL_RING,
    OVERSIZED_ORANGE,
    CHUNKY_TOGGLE,
    KNURLED_CLASSIC,
    PASTEL_MINT_BUTTON
}

@Serializable
enum class SkinAccentMotif {
    NONE,
    CHARCOAL_GRIP,
    TITANIUM_BEZEL,
    RED_ADVANCE_WHEEL,
    RED_TAPE,
    SATIN_SILVER_TOP_PLATE,
    TRICOLOR_STRIPE
}

@Serializable
data class CameraSkinSpec(
    val skinId: String,
    val chassisMaterial: ChassisMaterial,
    val lcdTheme: LcdTheme,
    val shutterStyle: ShutterStyle,
    val accentMotif: SkinAccentMotif = SkinAccentMotif.NONE,
    val bodyPrimaryHex: String,
    val bodySecondaryHex: String,
    val accentHex: String,
    val lcdBackgroundHex: String,
    val lcdForegroundHex: String,
    val shutterHex: String,
    val labelHex: String
)
