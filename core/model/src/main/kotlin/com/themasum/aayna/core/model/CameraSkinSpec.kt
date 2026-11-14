package com.themasum.aayna.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ChassisMaterial {
    BRUSHED_SILVER,
    ANODIZED_GUNMETAL,
    RIBBED_PLASTIC,
    WEATHERED_GRUNGE_SLATE,
    PEBBLE_LEATHERETTE,
    WARM_CREAM_INSTANT,
    CHARCOAL_METAL,
    MATTE_OBSIDIAN,
    COGNAC_LEATHER,
    OLIVE_LEATHER,
    CHAMPAGNE_TITANIUM,
    Y2K_TRANSLUCENT,
    PASTEL_POLYMER,
    COBALT_METALLIC,
    VHS_GRAPHITE,
    BRASS_RANGEFINDER,
    CINE_ANODIZED,
    STUDIO_MONOCHROME,
    SLR_PRISM_GRIP,
    PHOTOBOOTH_BRONZE,
    PEARLESCENT_Y2K,
    HANDHELD_ANODIZED
}

@Serializable
enum class LcdTheme {
    CYAN_DOT_MATRIX,
    EMERALD_SEVEN_SEG,
    AMBER_COUNTER,
    WARM_MONOCHROME,
    VCR_BLUE_OSD,
    CLASSIC_RED_LED
}

@Serializable
enum class ShutterStyle {
    PILL_CHROME,
    RECESSED_METAL_RING,
    OVERSIZED_ORANGE,
    CHUNKY_TOGGLE,
    KNURLED_CLASSIC,
    PASTEL_MINT_BUTTON,
    PASTEL_BLUSH_BUTTON,
    BRUSHED_DOME,
    CAMCORDER_REC_BUTTON,
    BRASS_MECHANICAL
}

@Serializable
enum class SkinAccentMotif {
    NONE,
    CHARCOAL_GRIP,
    TITANIUM_BEZEL,
    RED_ADVANCE_WHEEL,
    RED_TAPE,
    SATIN_SILVER_TOP_PLATE,
    TRICOLOR_STRIPE,
    Y2K_RIBBON,
    BRASS_ENGRAVING,
    CINE_TALLY_LAMP,
    KNURLED_DIAL_RING
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
    val labelHex: String,
    val displayName: String = skinId,
    val chassisBgAsset: String = "",
    val bezelOverlayAsset: String = "",
    val screenGlassAsset: String = SkinCatalog.DEFAULT_SCREEN_GLASS_ASSET,
    val shutterButtonAsset: String = "",
    val textHex: String = labelHex,
    val dialHex: String = bodySecondaryHex,
    val ledIdleHex: String = "#3A404A",
    val ledActiveHex: String = "#39FFAE",
    val ledFlashHex: String = "#FF453A",
    val isLightBody: Boolean = false,
    val hardwareFinishTags: List<String> = emptyList()
)

@Serializable
data class ChassisSkinDefinition(
    val skinId: String,
    val displayName: String,
    val chassisMaterial: ChassisMaterial,
    val lcdTheme: LcdTheme,
    val shutterStyle: ShutterStyle,
    val accentMotif: SkinAccentMotif = SkinAccentMotif.NONE,
    val chassisBgAsset: String,
    val bezelOverlayAsset: String,
    val screenGlassAsset: String = SkinCatalog.DEFAULT_SCREEN_GLASS_ASSET,
    val shutterButtonAsset: String,
    val bodyPrimaryHex: String,
    val bodySecondaryHex: String,
    val accentHex: String,
    val textHex: String,
    val lcdBackgroundHex: String,
    val lcdForegroundHex: String,
    val dialHex: String,
    val shutterHex: String,
    val ledIdleHex: String = "#3A404A",
    val ledActiveHex: String = "#39FFAE",
    val ledFlashHex: String = "#FF453A",
    val isLightBody: Boolean = false,
    val hardwareFinishTags: List<String> = emptyList(),
    val supportedCategories: List<CameraCategory> = CameraCategory.entries
) {
    fun toCameraSkinSpec(): CameraSkinSpec = CameraSkinSpec(
        skinId = skinId,
        chassisMaterial = chassisMaterial,
        lcdTheme = lcdTheme,
        shutterStyle = shutterStyle,
        accentMotif = accentMotif,
        bodyPrimaryHex = bodyPrimaryHex,
        bodySecondaryHex = bodySecondaryHex,
        accentHex = accentHex,
        lcdBackgroundHex = lcdBackgroundHex,
        lcdForegroundHex = lcdForegroundHex,
        shutterHex = shutterHex,
        labelHex = textHex,
        displayName = displayName,
        chassisBgAsset = chassisBgAsset,
        bezelOverlayAsset = bezelOverlayAsset,
        screenGlassAsset = screenGlassAsset,
        shutterButtonAsset = shutterButtonAsset,
        textHex = textHex,
        dialHex = dialHex,
        ledIdleHex = ledIdleHex,
        ledActiveHex = ledActiveHex,
        ledFlashHex = ledFlashHex,
        isLightBody = isLightBody,
        hardwareFinishTags = hardwareFinishTags
    )
}

@Serializable
data class SkinConfigManifest(
    val version: Int = 1,
    val defaultScreenGlassAsset: String = SkinCatalog.DEFAULT_SCREEN_GLASS_ASSET,
    val skins: List<ChassisSkinDefinition> = emptyList(),
    val cameraDefaultSkins: Map<String, String> = emptyMap(),
    val cameraLinkedSkins: Map<String, List<String>> = emptyMap()
)

object SkinCatalog {
    const val DEFAULT_SCREEN_GLASS_ASSET: String = "skins/screen_glass_sheen.png"

    private fun skinEntry(
        skinId: String,
        slug: String,
        displayName: String,
        chassisMaterial: ChassisMaterial,
        lcdTheme: LcdTheme,
        shutterStyle: ShutterStyle,
        accentMotif: SkinAccentMotif,
        bodyPrimaryHex: String,
        bodySecondaryHex: String,
        accentHex: String,
        textHex: String,
        lcdBackgroundHex: String,
        lcdForegroundHex: String,
        dialHex: String,
        shutterHex: String,
        ledIdleHex: String = "#3A404A",
        ledActiveHex: String = "#39FFAE",
        ledFlashHex: String = "#FF453A",
        isLightBody: Boolean = false,
        hardwareFinishTags: List<String>,
        supportedCategories: List<CameraCategory> = CameraCategory.entries
    ): ChassisSkinDefinition = ChassisSkinDefinition(
        skinId = skinId,
        displayName = displayName,
        chassisMaterial = chassisMaterial,
        lcdTheme = lcdTheme,
        shutterStyle = shutterStyle,
        accentMotif = accentMotif,
        chassisBgAsset = "skins/chassis_$slug.png",
        bezelOverlayAsset = "skins/bezel_$slug.png",
        screenGlassAsset = DEFAULT_SCREEN_GLASS_ASSET,
        shutterButtonAsset = "skins/shutter_$slug.png",
        bodyPrimaryHex = bodyPrimaryHex,
        bodySecondaryHex = bodySecondaryHex,
        accentHex = accentHex,
        textHex = textHex,
        lcdBackgroundHex = lcdBackgroundHex,
        lcdForegroundHex = lcdForegroundHex,
        dialHex = dialHex,
        shutterHex = shutterHex,
        ledIdleHex = ledIdleHex,
        ledActiveHex = ledActiveHex,
        ledFlashHex = ledFlashHex,
        isLightBody = isLightBody,
        hardwareFinishTags = hardwareFinishTags,
        supportedCategories = supportedCategories
    )

    val charcoalMetal =
        skinEntry(
            skinId = "skin_charcoal_metal",
            slug = "charcoal_metal",
            displayName = "Charcoal Metal",
            chassisMaterial = ChassisMaterial.CHARCOAL_METAL,
            lcdTheme = LcdTheme.AMBER_COUNTER,
            shutterStyle = ShutterStyle.RECESSED_METAL_RING,
            accentMotif = SkinAccentMotif.CHARCOAL_GRIP,
            bodyPrimaryHex = "#242222",
            bodySecondaryHex = "#34373D",
            accentHex = "#FFA726",
            textHex = "#E4E6EB",
            lcdBackgroundHex = "#121316",
            lcdForegroundHex = "#FFB74D",
            dialHex = "#3D414A",
            shutterHex = "#8E94A0",
            ledActiveHex = "#39FFAE",
            hardwareFinishTags = listOf("charcoal", "anodized-metal", "ccd-studio"),
            supportedCategories = listOf(CameraCategory.CCD, CameraCategory.SPECIAL)
        )

    val silverBrushed =
        skinEntry(
            skinId = "skin_silver_brushed",
            slug = "silver_brushed",
            displayName = "Brushed Silver",
            chassisMaterial = ChassisMaterial.BRUSHED_SILVER,
            lcdTheme = LcdTheme.CYAN_DOT_MATRIX,
            shutterStyle = ShutterStyle.PILL_CHROME,
            accentMotif = SkinAccentMotif.CHARCOAL_GRIP,
            bodyPrimaryHex = "#C9CDD3",
            bodySecondaryHex = "#25282E",
            accentHex = "#4CD4E8",
            textHex = "#1A1D22",
            lcdBackgroundHex = "#0B1E24",
            lcdForegroundHex = "#62F0FF",
            dialHex = "#A7ADB7",
            shutterHex = "#E4E7EC",
            ledActiveHex = "#4CD4E8",
            isLightBody = true,
            hardwareFinishTags = listOf("brushed-aluminum", "silver", "y2k-digicam"),
            supportedCategories = listOf(CameraCategory.CCD, CameraCategory.FILM)
        )

    val vintageCream =
        skinEntry(
            skinId = "skin_vintage_cream",
            slug = "vintage_cream",
            displayName = "Vintage Cream",
            chassisMaterial = ChassisMaterial.WARM_CREAM_INSTANT,
            lcdTheme = LcdTheme.WARM_MONOCHROME,
            shutterStyle = ShutterStyle.BRUSHED_DOME,
            accentMotif = SkinAccentMotif.SATIN_SILVER_TOP_PLATE,
            bodyPrimaryHex = "#EDE4D3",
            bodySecondaryHex = "#7A6A58",
            accentHex = "#C97D45",
            textHex = "#2B241D",
            lcdBackgroundHex = "#211D17",
            lcdForegroundHex = "#F3E9D2",
            dialHex = "#D4C6B0",
            shutterHex = "#F7F1E5",
            ledActiveHex = "#FFB74D",
            isLightBody = true,
            hardwareFinishTags = listOf("retro-cream", "matte-polymer", "classic"),
            supportedCategories = listOf(CameraCategory.CCD, CameraCategory.FILM, CameraCategory.INSTANT)
        )

    val y2kCyan =
        skinEntry(
            skinId = "skin_y2k_cyan",
            slug = "y2k_cyan",
            displayName = "Y2K Ice Cyan",
            chassisMaterial = ChassisMaterial.Y2K_TRANSLUCENT,
            lcdTheme = LcdTheme.CYAN_DOT_MATRIX,
            shutterStyle = ShutterStyle.PILL_CHROME,
            accentMotif = SkinAccentMotif.Y2K_RIBBON,
            bodyPrimaryHex = "#7DD3E8",
            bodySecondaryHex = "#3E8CA3",
            accentHex = "#00E5FF",
            textHex = "#102830",
            lcdBackgroundHex = "#082028",
            lcdForegroundHex = "#70F8FF",
            dialHex = "#B3EAF5",
            shutterHex = "#E6FAFF",
            ledActiveHex = "#00E5FF",
            isLightBody = true,
            hardwareFinishTags = listOf("y2k", "gloss-cyan", "translucent-shell"),
            supportedCategories = listOf(CameraCategory.CCD, CameraCategory.CAMCORDER)
        )

    val y2kLavender =
        skinEntry(
            skinId = "skin_y2k_lavender",
            slug = "y2k_lavender",
            displayName = "Y2K Lavender",
            chassisMaterial = ChassisMaterial.Y2K_TRANSLUCENT,
            lcdTheme = LcdTheme.CYAN_DOT_MATRIX,
            shutterStyle = ShutterStyle.PILL_CHROME,
            accentMotif = SkinAccentMotif.Y2K_RIBBON,
            bodyPrimaryHex = "#B8A9E8",
            bodySecondaryHex = "#6B5CA5",
            accentHex = "#D2B8FF",
            textHex = "#1E1930",
            lcdBackgroundHex = "#18132B",
            lcdForegroundHex = "#E4D4FF",
            dialHex = "#D7CCF7",
            shutterHex = "#F2EDFF",
            ledActiveHex = "#D2B8FF",
            isLightBody = true,
            hardwareFinishTags = listOf("y2k", "pastel-lavender", "gloss"),
            supportedCategories = listOf(CameraCategory.CCD, CameraCategory.INSTANT)
        )

    val matteObsidian =
        skinEntry(
            skinId = "skin_matte_obsidian",
            slug = "matte_obsidian",
            displayName = "Matte Obsidian",
            chassisMaterial = ChassisMaterial.MATTE_OBSIDIAN,
            lcdTheme = LcdTheme.EMERALD_SEVEN_SEG,
            shutterStyle = ShutterStyle.RECESSED_METAL_RING,
            accentMotif = SkinAccentMotif.TITANIUM_BEZEL,
            bodyPrimaryHex = "#16171B",
            bodySecondaryHex = "#292C33",
            accentHex = "#2CE59B",
            textHex = "#EAECEF",
            lcdBackgroundHex = "#07140F",
            lcdForegroundHex = "#39FFAE",
            dialHex = "#31353E",
            shutterHex = "#4C5361",
            ledActiveHex = "#39FFAE",
            hardwareFinishTags = listOf("matte-black", "street", "magnesium"),
            supportedCategories = listOf(CameraCategory.CCD, CameraCategory.FILM, CameraCategory.SPECIAL)
        )

    val leatherCognac =
        skinEntry(
            skinId = "skin_leather_cognac",
            slug = "leather_cognac",
            displayName = "Cognac Leatherette",
            chassisMaterial = ChassisMaterial.COGNAC_LEATHER,
            lcdTheme = LcdTheme.WARM_MONOCHROME,
            shutterStyle = ShutterStyle.KNURLED_CLASSIC,
            accentMotif = SkinAccentMotif.SATIN_SILVER_TOP_PLATE,
            bodyPrimaryHex = "#6E3F24",
            bodySecondaryHex = "#D3D6DC",
            accentHex = "#D98A4E",
            textHex = "#F7EFE6",
            lcdBackgroundHex = "#1B1510",
            lcdForegroundHex = "#F2DEC2",
            dialHex = "#C4C9D1",
            shutterHex = "#E0E3E8",
            ledActiveHex = "#FFB74D",
            hardwareFinishTags = listOf("leatherette", "cognac", "silver-top-plate"),
            supportedCategories = listOf(CameraCategory.FILM, CameraCategory.CCD)
        )

    val leatherOlive =
        skinEntry(
            skinId = "skin_leather_olive",
            slug = "leather_olive",
            displayName = "Olive Field Leather",
            chassisMaterial = ChassisMaterial.OLIVE_LEATHER,
            lcdTheme = LcdTheme.AMBER_COUNTER,
            shutterStyle = ShutterStyle.KNURLED_CLASSIC,
            accentMotif = SkinAccentMotif.BRASS_ENGRAVING,
            bodyPrimaryHex = "#3D4735",
            bodySecondaryHex = "#23281F",
            accentHex = "#C8A656",
            textHex = "#ECE8DB",
            lcdBackgroundHex = "#141811",
            lcdForegroundHex = "#FFBE55",
            dialHex = "#A78B45",
            shutterHex = "#C9B06B",
            ledActiveHex = "#C8A656",
            hardwareFinishTags = listOf("olive-leather", "field-edition", "brass-dial"),
            supportedCategories = listOf(CameraCategory.FILM, CameraCategory.CCD)
        )

    val grungeSlate =
        skinEntry(
            skinId = "skin_grunge_slate",
            slug = "grunge_slate",
            displayName = "Weathered Grunge Slate",
            chassisMaterial = ChassisMaterial.WEATHERED_GRUNGE_SLATE,
            lcdTheme = LcdTheme.AMBER_COUNTER,
            shutterStyle = ShutterStyle.CHUNKY_TOGGLE,
            accentMotif = SkinAccentMotif.RED_TAPE,
            bodyPrimaryHex = "#3A4038",
            bodySecondaryHex = "#242823",
            accentHex = "#C63D2F",
            textHex = "#E8E4D8",
            lcdBackgroundHex = "#1E1409",
            lcdForegroundHex = "#FF9933",
            dialHex = "#4C5349",
            shutterHex = "#B83A2D",
            ledActiveHex = "#FF7A2F",
            hardwareFinishTags = listOf("grunge", "distressed", "90s-lomo"),
            supportedCategories = listOf(CameraCategory.FILM, CameraCategory.SPECIAL)
        )

    val titaniumChampagne =
        skinEntry(
            skinId = "skin_titanium_champagne",
            slug = "titanium_champagne",
            displayName = "Champagne Titanium",
            chassisMaterial = ChassisMaterial.CHAMPAGNE_TITANIUM,
            lcdTheme = LcdTheme.AMBER_COUNTER,
            shutterStyle = ShutterStyle.BRUSHED_DOME,
            accentMotif = SkinAccentMotif.TITANIUM_BEZEL,
            bodyPrimaryHex = "#C6B89E",
            bodySecondaryHex = "#8D7F68",
            accentHex = "#E8C374",
            textHex = "#221E17",
            lcdBackgroundHex = "#1C1913",
            lcdForegroundHex = "#FFD580",
            dialHex = "#B0A188",
            shutterHex = "#E5DAC4",
            ledActiveHex = "#FFD580",
            isLightBody = true,
            hardwareFinishTags = listOf("champagne", "titanium", "premium-compact"),
            supportedCategories = listOf(CameraCategory.CCD, CameraCategory.FILM)
        )

    val polarIvory =
        skinEntry(
            skinId = "skin_polar_ivory",
            slug = "polar_ivory",
            displayName = "Polar Ivory",
            chassisMaterial = ChassisMaterial.WARM_CREAM_INSTANT,
            lcdTheme = LcdTheme.WARM_MONOCHROME,
            shutterStyle = ShutterStyle.OVERSIZED_ORANGE,
            accentMotif = SkinAccentMotif.TRICOLOR_STRIPE,
            bodyPrimaryHex = "#F4EFE4",
            bodySecondaryHex = "#D8D1C4",
            accentHex = "#E5533D",
            textHex = "#25221E",
            lcdBackgroundHex = "#22201C",
            lcdForegroundHex = "#F7F3E9",
            dialHex = "#CFC7B8",
            shutterHex = "#EE5D43",
            ledActiveHex = "#6EC7B4",
            isLightBody = true,
            hardwareFinishTags = listOf("instant", "ivory", "rainbow-stripe"),
            supportedCategories = listOf(CameraCategory.INSTANT)
        )

    val instPastelMint =
        skinEntry(
            skinId = "skin_inst_pastel_mint",
            slug = "inst_pastel_mint",
            displayName = "Pastel Mint Instant",
            chassisMaterial = ChassisMaterial.PASTEL_POLYMER,
            lcdTheme = LcdTheme.WARM_MONOCHROME,
            shutterStyle = ShutterStyle.PASTEL_MINT_BUTTON,
            accentMotif = SkinAccentMotif.TRICOLOR_STRIPE,
            bodyPrimaryHex = "#CFECE2",
            bodySecondaryHex = "#9ECBB9",
            accentHex = "#4EBA9C",
            textHex = "#1B2E27",
            lcdBackgroundHex = "#182621",
            lcdForegroundHex = "#E6FFF6",
            dialHex = "#B2DFCF",
            shutterHex = "#78D1BD",
            ledActiveHex = "#4EBA9C",
            isLightBody = true,
            hardwareFinishTags = listOf("instant", "pastel-mint", "molded-polymer"),
            supportedCategories = listOf(CameraCategory.INSTANT, CameraCategory.CCD)
        )

    val instBlush =
        skinEntry(
            skinId = "skin_inst_blush",
            slug = "inst_blush",
            displayName = "Blush Rose Instant",
            chassisMaterial = ChassisMaterial.PASTEL_POLYMER,
            lcdTheme = LcdTheme.WARM_MONOCHROME,
            shutterStyle = ShutterStyle.PASTEL_BLUSH_BUTTON,
            accentMotif = SkinAccentMotif.TRICOLOR_STRIPE,
            bodyPrimaryHex = "#F2D5D9",
            bodySecondaryHex = "#D6A8AF",
            accentHex = "#E06C82",
            textHex = "#2E1C20",
            lcdBackgroundHex = "#26181B",
            lcdForegroundHex = "#FFEAF0",
            dialHex = "#E3BCC2",
            shutterHex = "#EA8C9E",
            ledActiveHex = "#FF8FA3",
            isLightBody = true,
            hardwareFinishTags = listOf("instant", "pastel-blush", "soft-matte"),
            supportedCategories = listOf(CameraCategory.INSTANT, CameraCategory.CCD)
        )

    val quikYellowFilm =
        skinEntry(
            skinId = "skin_quik_yellow_film",
            slug = "quik_yellow_film",
            displayName = "Quik Yellow Disposable",
            chassisMaterial = ChassisMaterial.RIBBED_PLASTIC,
            lcdTheme = LcdTheme.AMBER_COUNTER,
            shutterStyle = ShutterStyle.OVERSIZED_ORANGE,
            accentMotif = SkinAccentMotif.RED_ADVANCE_WHEEL,
            bodyPrimaryHex = "#202226",
            bodySecondaryHex = "#E5B724",
            accentHex = "#E24B2C",
            textHex = "#17181B",
            lcdBackgroundHex = "#1C1408",
            lcdForegroundHex = "#FFA82E",
            dialHex = "#D1A319",
            shutterHex = "#F05A28",
            ledActiveHex = "#FFA82E",
            hardwareFinishTags = listOf("disposable", "ribbed-plastic", "yellow-wrap"),
            supportedCategories = listOf(CameraCategory.FILM)
        )

    val dvCobaltMetallic =
        skinEntry(
            skinId = "skin_dv_cobalt_metallic",
            slug = "dv_cobalt_metallic",
            displayName = "Cobalt DV Metallic",
            chassisMaterial = ChassisMaterial.COBALT_METALLIC,
            lcdTheme = LcdTheme.VCR_BLUE_OSD,
            shutterStyle = ShutterStyle.CAMCORDER_REC_BUTTON,
            accentMotif = SkinAccentMotif.CINE_TALLY_LAMP,
            bodyPrimaryHex = "#2E4366",
            bodySecondaryHex = "#1E2A3F",
            accentHex = "#FF3B30",
            textHex = "#E6EEF8",
            lcdBackgroundHex = "#0B1629",
            lcdForegroundHex = "#6CB4FF",
            dialHex = "#48638F",
            shutterHex = "#D9382E",
            ledActiveHex = "#FF3B30",
            hardwareFinishTags = listOf("camcorder", "cobalt-metal", "minidv"),
            supportedCategories = listOf(CameraCategory.CAMCORDER)
        )

    val vhsGraphite =
        skinEntry(
            skinId = "skin_vhs_graphite",
            slug = "vhs_graphite",
            displayName = "VHS Graphite Deck",
            chassisMaterial = ChassisMaterial.VHS_GRAPHITE,
            lcdTheme = LcdTheme.VCR_BLUE_OSD,
            shutterStyle = ShutterStyle.CAMCORDER_REC_BUTTON,
            accentMotif = SkinAccentMotif.CINE_TALLY_LAMP,
            bodyPrimaryHex = "#2A2D34",
            bodySecondaryHex = "#181A1F",
            accentHex = "#FF453A",
            textHex = "#DDE2EA",
            lcdBackgroundHex = "#0D1322",
            lcdForegroundHex = "#8EC5FF",
            dialHex = "#424752",
            shutterHex = "#CC352C",
            ledActiveHex = "#FF453A",
            hardwareFinishTags = listOf("vhs", "video8", "graphite-polymer"),
            supportedCategories = listOf(CameraCategory.CAMCORDER)
        )

    val rangefinderBrass =
        skinEntry(
            skinId = "skin_rangefinder_brass",
            slug = "rangefinder_brass",
            displayName = "Black Paint & Brass",
            chassisMaterial = ChassisMaterial.BRASS_RANGEFINDER,
            lcdTheme = LcdTheme.CLASSIC_RED_LED,
            shutterStyle = ShutterStyle.BRASS_MECHANICAL,
            accentMotif = SkinAccentMotif.BRASS_ENGRAVING,
            bodyPrimaryHex = "#191A1D",
            bodySecondaryHex = "#B3934B",
            accentHex = "#D94436",
            textHex = "#F2EFE9",
            lcdBackgroundHex = "#181410",
            lcdForegroundHex = "#F5CF7A",
            dialHex = "#C2A159",
            shutterHex = "#D6B76C",
            ledActiveHex = "#FF5A4E",
            hardwareFinishTags = listOf("rangefinder", "brass-patina", "pebble-vulcanite"),
            supportedCategories = listOf(CameraCategory.FILM, CameraCategory.CCD)
        )

    val cine16mm =
        skinEntry(
            skinId = "skin_16mm_cine",
            slug = "16mm_cine",
            displayName = "16mm Cine Crinkle",
            chassisMaterial = ChassisMaterial.CINE_ANODIZED,
            lcdTheme = LcdTheme.AMBER_COUNTER,
            shutterStyle = ShutterStyle.CHUNKY_TOGGLE,
            accentMotif = SkinAccentMotif.CINE_TALLY_LAMP,
            bodyPrimaryHex = "#232528",
            bodySecondaryHex = "#444850",
            accentHex = "#E85D2A",
            textHex = "#E6E4DC",
            lcdBackgroundHex = "#141210",
            lcdForegroundHex = "#FFAE42",
            dialHex = "#626873",
            shutterHex = "#C44C23",
            ledActiveHex = "#E85D2A",
            hardwareFinishTags = listOf("16mm", "crinkle-enamel", "cine-turret"),
            supportedCategories = listOf(CameraCategory.FILM, CameraCategory.CAMCORDER)
        )

    val cyberTranslucent =
        skinEntry(
            skinId = "skin_cyber_translucent",
            slug = "cyber_translucent",
            displayName = "Cyber Smoke Translucent",
            chassisMaterial = ChassisMaterial.Y2K_TRANSLUCENT,
            lcdTheme = LcdTheme.CYAN_DOT_MATRIX,
            shutterStyle = ShutterStyle.RECESSED_METAL_RING,
            accentMotif = SkinAccentMotif.KNURLED_DIAL_RING,
            bodyPrimaryHex = "#2D3340",
            bodySecondaryHex = "#4C5870",
            accentHex = "#00F0B5",
            textHex = "#E6F4F1",
            lcdBackgroundHex = "#09181D",
            lcdForegroundHex = "#4DFFD4",
            dialHex = "#5A6782",
            shutterHex = "#7A89A6",
            ledActiveHex = "#00F0B5",
            hardwareFinishTags = listOf("translucent-smoke", "multi-lens", "cyber-y2k"),
            supportedCategories = listOf(CameraCategory.SPECIAL, CameraCategory.CCD, CameraCategory.CAMCORDER)
        )

    val studioMonochrome =
        skinEntry(
            skinId = "skin_studio_monochrome",
            slug = "studio_monochrome",
            displayName = "Studio Monochrome",
            chassisMaterial = ChassisMaterial.STUDIO_MONOCHROME,
            lcdTheme = LcdTheme.WARM_MONOCHROME,
            shutterStyle = ShutterStyle.KNURLED_CLASSIC,
            accentMotif = SkinAccentMotif.KNURLED_DIAL_RING,
            bodyPrimaryHex = "#1D1E20",
            bodySecondaryHex = "#D2D5DA",
            accentHex = "#EAECEF",
            textHex = "#F5F6F8",
            lcdBackgroundHex = "#121315",
            lcdForegroundHex = "#F0F2F5",
            dialHex = "#A8AEB8",
            shutterHex = "#D8DCE2",
            ledActiveHex = "#F5F6F8",
            hardwareFinishTags = listOf("monochrome", "studio", "minimal"),
            supportedCategories = CameraCategory.entries
        )

    val slrNx =
        skinEntry(
            skinId = "skin_slr_nx",
            slug = "slr_nx",
            displayName = "Matte SLR Grip",
            chassisMaterial = ChassisMaterial.SLR_PRISM_GRIP,
            lcdTheme = LcdTheme.EMERALD_SEVEN_SEG,
            shutterStyle = ShutterStyle.RECESSED_METAL_RING,
            accentMotif = SkinAccentMotif.TITANIUM_BEZEL,
            bodyPrimaryHex = "#1B1D22",
            bodySecondaryHex = "#343942",
            accentHex = "#2CE59B",
            textHex = "#E8ECEF",
            lcdBackgroundHex = "#081812",
            lcdForegroundHex = "#39FFAE",
            dialHex = "#484E5A",
            shutterHex = "#5A6270",
            ledActiveHex = "#2CE59B",
            hardwareFinishTags = listOf("slr", "rubber-grip", "prism-bezel"),
            supportedCategories = listOf(CameraCategory.CCD, CameraCategory.FILM)
        )

    val boothStrip =
        skinEntry(
            skinId = "skin_booth_strip",
            slug = "booth_strip",
            displayName = "Retro Photobooth Bronze",
            chassisMaterial = ChassisMaterial.PHOTOBOOTH_BRONZE,
            lcdTheme = LcdTheme.AMBER_COUNTER,
            shutterStyle = ShutterStyle.OVERSIZED_ORANGE,
            accentMotif = SkinAccentMotif.BRASS_ENGRAVING,
            bodyPrimaryHex = "#5E432F",
            bodySecondaryHex = "#8C6849",
            accentHex = "#FFB74D",
            textHex = "#FAF0E1",
            lcdBackgroundHex = "#21160C",
            lcdForegroundHex = "#FFCC70",
            dialHex = "#A8815B",
            shutterHex = "#E87A3D",
            ledActiveHex = "#FFB74D",
            hardwareFinishTags = listOf("photobooth", "bronze", "4-strip"),
            supportedCategories = listOf(CameraCategory.SPECIAL, CameraCategory.INSTANT)
        )

    val bloom2000s =
        skinEntry(
            skinId = "skin_bloom_2000s",
            slug = "bloom_2000s",
            displayName = "2000s Pearlescent Bloom",
            chassisMaterial = ChassisMaterial.PEARLESCENT_Y2K,
            lcdTheme = LcdTheme.CYAN_DOT_MATRIX,
            shutterStyle = ShutterStyle.PILL_CHROME,
            accentMotif = SkinAccentMotif.Y2K_RIBBON,
            bodyPrimaryHex = "#E2DFEC",
            bodySecondaryHex = "#B3ACC8",
            accentHex = "#87E5FF",
            textHex = "#252130",
            lcdBackgroundHex = "#161B26",
            lcdForegroundHex = "#9EF0FF",
            dialHex = "#CCC6DC",
            shutterHex = "#F4F2FA",
            ledActiveHex = "#87E5FF",
            isLightBody = true,
            hardwareFinishTags = listOf("pearlescent", "y2k-bloom", "dreamy-metallic"),
            supportedCategories = listOf(CameraCategory.SPECIAL, CameraCategory.CCD)
        )

    val gameConsole =
        skinEntry(
            skinId = "skin_game_console",
            slug = "game_console",
            displayName = "Handheld Console Plum",
            chassisMaterial = ChassisMaterial.HANDHELD_ANODIZED,
            lcdTheme = LcdTheme.CYAN_DOT_MATRIX,
            shutterStyle = ShutterStyle.CHUNKY_TOGGLE,
            accentMotif = SkinAccentMotif.KNURLED_DIAL_RING,
            bodyPrimaryHex = "#2E2C36",
            bodySecondaryHex = "#633D8E",
            accentHex = "#C97BFF",
            textHex = "#EFEAF7",
            lcdBackgroundHex = "#14111C",
            lcdForegroundHex = "#D69CFF",
            dialHex = "#7A52A8",
            shutterHex = "#9B6AD6",
            ledActiveHex = "#C97BFF",
            hardwareFinishTags = listOf("handheld", "anodized-grey", "plum-trim"),
            supportedCategories = listOf(CameraCategory.SPECIAL, CameraCategory.CCD)
        )

    val all: List<ChassisSkinDefinition> =
        listOf(
            charcoalMetal,
            silverBrushed,
            vintageCream,
            y2kCyan,
            y2kLavender,
            matteObsidian,
            leatherCognac,
            leatherOlive,
            grungeSlate,
            titaniumChampagne,
            polarIvory,
            instPastelMint,
            instBlush,
            quikYellowFilm,
            dvCobaltMetallic,
            vhsGraphite,
            rangefinderBrass,
            cine16mm,
            cyberTranslucent,
            studioMonochrome,
            slrNx,
            boothStrip,
            bloom2000s,
            gameConsole
        )

    val defaultSkin: ChassisSkinDefinition = charcoalMetal

    private val byId: Map<String, ChassisSkinDefinition> = all.associateBy { it.skinId }

    fun findById(skinId: String): ChassisSkinDefinition? = byId[skinId]

    fun findByIdOrDefault(skinId: String): ChassisSkinDefinition = byId[skinId] ?: defaultSkin

    fun skinsForCategory(category: CameraCategory): List<ChassisSkinDefinition> =
        all.filter { category in it.supportedCategories }

    fun skinsForIds(ids: List<String>): List<ChassisSkinDefinition> = ids.mapNotNull { byId[it] }

    fun toManifest(
        cameraDefaultSkins: Map<String, String> = emptyMap(),
        cameraLinkedSkins: Map<String, List<String>> = emptyMap()
    ): SkinConfigManifest = SkinConfigManifest(
        version = 1,
        defaultScreenGlassAsset = DEFAULT_SCREEN_GLASS_ASSET,
        skins = all,
        cameraDefaultSkins = cameraDefaultSkins,
        cameraLinkedSkins = cameraLinkedSkins
    )
}
