package com.themasum.aayna.core.model

object CameraCatalog {
    val dc04SilverPulse =
        CameraProfile(
            id = "dc_04_silver_pulse",
            codeName = "DC-04",
            subtitle = "Silver Pulse \u2022 2004 CCD",
            era = CameraEra.CCD_2000S,
            eraYearOffset = 2004,
            lutAsset = "luts/dc_04_ccd.cube",
            defaultAspect = AspectRatioPreset.RATIO_4_3,
            defaultResolution = ResolutionPreset.CCD_3_2MP,
            bloom = BloomSpec(threshold = 0.80f, intensity = 0.42f, tintHex = "#FFF3E0"),
            grain = GrainSpec(type = GrainType.CCD_CHROMA_LUMA, lumaStrength = 0.14f, chromaStrength = 0.22f),
            optics = OpticsSpec(caStrength = 0.18f, vignetteStrength = 0.25f),
            grunge = GrungeSpec(lightLeakStrength = 0f, dustStrength = 0f),
            stamp = StampDefaults(style = StampStyle.DOT_MATRIX_AMBER, defaultEnabled = true),
            soundAssetName = "shutter_ccd",
            skin =
            CameraSkinSpec(
                skinId = "skin_brushed_silver_dc04",
                chassisMaterial = ChassisMaterial.BRUSHED_SILVER,
                lcdTheme = LcdTheme.CYAN_DOT_MATRIX,
                shutterStyle = ShutterStyle.PILL_CHROME,
                accentMotif = SkinAccentMotif.CHARCOAL_GRIP,
                bodyPrimaryHex = "#C9CDD3",
                bodySecondaryHex = "#25282E",
                accentHex = "#4CD4E8",
                lcdBackgroundHex = "#0B1E24",
                lcdForegroundHex = "#62F0FF",
                shutterHex = "#E4E7EC",
                labelHex = "#1A1D22"
            )
        )

    val nx07Nocturne =
        CameraProfile(
            id = "nx_07_nocturne",
            codeName = "NX-07",
            subtitle = "Nocturne \u2022 2007 Night CCD",
            era = CameraEra.CCD_2000S,
            eraYearOffset = 2007,
            lutAsset = "luts/nx_07_nocturne.cube",
            defaultAspect = AspectRatioPreset.RATIO_4_3,
            defaultResolution = ResolutionPreset.DIGICAM_5MP,
            bloom = BloomSpec(threshold = 0.76f, intensity = 0.52f, tintHex = "#FFE8D6"),
            grain = GrainSpec(type = GrainType.CCD_CHROMA_LUMA, lumaStrength = 0.18f, chromaStrength = 0.28f),
            optics = OpticsSpec(caStrength = 0.20f, vignetteStrength = 0.30f, starPoints = 0),
            grunge = GrungeSpec(lightLeakStrength = 0f, dustStrength = 0f),
            stamp = StampDefaults(style = StampStyle.SEVEN_SEGMENT_EMERALD, defaultEnabled = true),
            soundAssetName = "shutter_ccd",
            skin =
            CameraSkinSpec(
                skinId = "skin_gunmetal_nx07",
                chassisMaterial = ChassisMaterial.ANODIZED_GUNMETAL,
                lcdTheme = LcdTheme.EMERALD_SEVEN_SEG,
                shutterStyle = ShutterStyle.RECESSED_METAL_RING,
                accentMotif = SkinAccentMotif.TITANIUM_BEZEL,
                bodyPrimaryHex = "#1F2228",
                bodySecondaryHex = "#343942",
                accentHex = "#2CE59B",
                lcdBackgroundHex = "#081C14",
                lcdForegroundHex = "#39FFAE",
                shutterHex = "#5A6270",
                labelHex = "#D7DCE3"
            )
        )

    val d98Disposable =
        CameraProfile(
            id = "d_98_disposable",
            codeName = "D-98",
            subtitle = "1998 Disposable Film",
            era = CameraEra.DISPOSABLE_90S,
            eraYearOffset = 1998,
            lutAsset = "luts/d_98_disposable.cube",
            defaultAspect = AspectRatioPreset.RATIO_3_2,
            defaultResolution = ResolutionPreset.DIGICAM_5MP,
            bloom = BloomSpec(threshold = 0.82f, intensity = 0.32f, tintHex = "#FFF1D0"),
            grain = GrainSpec(type = GrainType.FILM_ORGANIC, lumaStrength = 0.24f, chromaStrength = 0.12f),
            optics = OpticsSpec(caStrength = 0.24f, vignetteStrength = 0.38f),
            grunge = GrungeSpec(lightLeakStrength = 0.20f, dustStrength = 0.15f),
            stamp = StampDefaults(style = StampStyle.QUARTZ_ORANGE, defaultEnabled = true),
            soundAssetName = "shutter_disposable",
            skin =
            CameraSkinSpec(
                skinId = "skin_disposable_d98",
                chassisMaterial = ChassisMaterial.RIBBED_PLASTIC,
                lcdTheme = LcdTheme.AMBER_COUNTER,
                shutterStyle = ShutterStyle.OVERSIZED_ORANGE,
                accentMotif = SkinAccentMotif.RED_ADVANCE_WHEEL,
                bodyPrimaryHex = "#202226",
                bodySecondaryHex = "#E5B724",
                accentHex = "#E24B2C",
                lcdBackgroundHex = "#1C1408",
                lcdForegroundHex = "#FFA82E",
                shutterHex = "#F05A28",
                labelHex = "#17181B"
            )
        )

    val lomo94Grunge =
        CameraProfile(
            id = "lomo_94_grunge",
            codeName = "LOMO-94",
            subtitle = "1994 Heavy Grunge",
            era = CameraEra.GRUNGE_90S,
            eraYearOffset = 1994,
            lutAsset = "luts/lomo_94_grunge.cube",
            defaultAspect = AspectRatioPreset.RATIO_4_3,
            defaultResolution = ResolutionPreset.DIGICAM_5MP,
            bloom = BloomSpec(threshold = 0.78f, intensity = 0.48f, tintHex = "#FFD8C2"),
            grain = GrainSpec(type = GrainType.FILM_ORGANIC, lumaStrength = 0.32f, chromaStrength = 0.18f),
            optics = OpticsSpec(caStrength = 0.28f, vignetteStrength = 0.45f),
            grunge = GrungeSpec(lightLeakStrength = 0.55f, dustStrength = 0.45f),
            stamp = StampDefaults(style = StampStyle.QUARTZ_ORANGE, defaultEnabled = true),
            soundAssetName = "shutter_disposable",
            skin =
            CameraSkinSpec(
                skinId = "skin_grunge_lomo94",
                chassisMaterial = ChassisMaterial.WEATHERED_GRUNGE_SLATE,
                lcdTheme = LcdTheme.AMBER_COUNTER,
                shutterStyle = ShutterStyle.CHUNKY_TOGGLE,
                accentMotif = SkinAccentMotif.RED_TAPE,
                bodyPrimaryHex = "#3A4038",
                bodySecondaryHex = "#242823",
                accentHex = "#C63D2F",
                lcdBackgroundHex = "#1E1409",
                lcdForegroundHex = "#FF9933",
                shutterHex = "#B83A2D",
                labelHex = "#E8E4D8"
            )
        )

    val rf77Classic =
        CameraProfile(
            id = "rf_77_classic",
            codeName = "RF-77",
            subtitle = "Classic 35mm Rangefinder",
            era = CameraEra.CLASSIC_35MM,
            eraYearOffset = 1977,
            lutAsset = "luts/rf_77_classic.cube",
            defaultAspect = AspectRatioPreset.RATIO_3_2,
            defaultResolution = ResolutionPreset.VINTAGE_8MP,
            bloom = BloomSpec(threshold = 0.84f, intensity = 0.28f, tintHex = "#FFF7E6"),
            grain = GrainSpec(type = GrainType.FILM_ORGANIC, lumaStrength = 0.20f, chromaStrength = 0.08f),
            optics = OpticsSpec(caStrength = 0.12f, vignetteStrength = 0.24f),
            grunge = GrungeSpec(lightLeakStrength = 0.05f, dustStrength = 0.08f),
            stamp = StampDefaults(style = StampStyle.CLASSIC_RED, defaultEnabled = true),
            defaultFrame = FrameBorderStyle.NONE,
            optionalFrame = FrameBorderStyle.FILM_35MM,
            soundAssetName = "shutter_rangefinder",
            skin =
            CameraSkinSpec(
                skinId = "skin_rangefinder_rf77",
                chassisMaterial = ChassisMaterial.PEBBLE_LEATHERETTE,
                lcdTheme = LcdTheme.WARM_MONOCHROME,
                shutterStyle = ShutterStyle.KNURLED_CLASSIC,
                accentMotif = SkinAccentMotif.SATIN_SILVER_TOP_PLATE,
                bodyPrimaryHex = "#191A1D",
                bodySecondaryHex = "#D2D5DA",
                accentHex = "#C93B3B",
                lcdBackgroundHex = "#191815",
                lcdForegroundHex = "#EDE6D6",
                shutterHex = "#C7CBD1",
                labelHex = "#F2EFE9"
            )
        )

    val instMInstant =
        CameraProfile(
            id = "inst_m_instant",
            codeName = "INST-M",
            subtitle = "Retro Instant Print",
            era = CameraEra.INSTANT,
            eraYearOffset = 1986,
            lutAsset = "luts/inst_m_instant.cube",
            defaultAspect = AspectRatioPreset.RATIO_1_1,
            defaultResolution = ResolutionPreset.CCD_3_2MP,
            bloom = BloomSpec(threshold = 0.77f, intensity = 0.45f, tintHex = "#FFF6EC"),
            grain = GrainSpec(type = GrainType.FILM_ORGANIC, lumaStrength = 0.16f, chromaStrength = 0.10f),
            optics = OpticsSpec(caStrength = 0.16f, vignetteStrength = 0.28f),
            grunge = GrungeSpec(lightLeakStrength = 0.10f, dustStrength = 0.05f),
            stamp = StampDefaults(style = StampStyle.CLEAN_WHITE, defaultEnabled = true),
            defaultFrame = FrameBorderStyle.INSTANT_PRINT,
            optionalFrame = FrameBorderStyle.INSTANT_PRINT,
            soundAssetName = "shutter_instant",
            skin =
            CameraSkinSpec(
                skinId = "skin_instant_instm",
                chassisMaterial = ChassisMaterial.WARM_CREAM_INSTANT,
                lcdTheme = LcdTheme.WARM_MONOCHROME,
                shutterStyle = ShutterStyle.PASTEL_MINT_BUTTON,
                accentMotif = SkinAccentMotif.TRICOLOR_STRIPE,
                bodyPrimaryHex = "#F2ECE0",
                bodySecondaryHex = "#D9D0C1",
                accentHex = "#6EC7B4",
                lcdBackgroundHex = "#24221E",
                lcdForegroundHex = "#F7F3E9",
                shutterHex = "#78D1BD",
                labelHex = "#2A2825"
            )
        )

    val all: List<CameraProfile> =
        listOf(
            dc04SilverPulse,
            nx07Nocturne,
            d98Disposable,
            lomo94Grunge,
            rf77Classic,
            instMInstant
        )

    val defaultCamera: CameraProfile = dc04SilverPulse

    fun findById(id: String): CameraProfile? = all.firstOrNull { it.id == id }

    fun findByIdOrDefault(id: String): CameraProfile = findById(id) ?: defaultCamera
}
