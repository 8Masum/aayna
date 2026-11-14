package com.themasum.aayna.core.model

object CameraCatalog {
    private val defaultCcdSkins =
        listOf(
            SkinCatalog.charcoalMetal.skinId,
            SkinCatalog.silverBrushed.skinId,
            SkinCatalog.matteObsidian.skinId,
            SkinCatalog.titaniumChampagne.skinId,
            SkinCatalog.y2kCyan.skinId,
            SkinCatalog.y2kLavender.skinId,
            SkinCatalog.vintageCream.skinId,
            SkinCatalog.studioMonochrome.skinId
        )

    private val defaultFilmSkins =
        listOf(
            SkinCatalog.rangefinderBrass.skinId,
            SkinCatalog.leatherCognac.skinId,
            SkinCatalog.leatherOlive.skinId,
            SkinCatalog.matteObsidian.skinId,
            SkinCatalog.silverBrushed.skinId,
            SkinCatalog.grungeSlate.skinId,
            SkinCatalog.studioMonochrome.skinId
        )

    private val defaultInstantSkins =
        listOf(
            SkinCatalog.polarIvory.skinId,
            SkinCatalog.instPastelMint.skinId,
            SkinCatalog.instBlush.skinId,
            SkinCatalog.vintageCream.skinId,
            SkinCatalog.y2kLavender.skinId
        )

    private val defaultCamcorderSkins =
        listOf(
            SkinCatalog.dvCobaltMetallic.skinId,
            SkinCatalog.vhsGraphite.skinId,
            SkinCatalog.charcoalMetal.skinId,
            SkinCatalog.y2kCyan.skinId,
            SkinCatalog.cine16mm.skinId
        )

    private fun profile(
        id: String,
        codeName: String,
        subtitle: String,
        sampleDescription: String,
        category: CameraCategory,
        era: CameraEra,
        eraYearOffset: Int,
        viewfinderMaskType: ViewfinderMaskType = ViewfinderMaskType.STANDARD_TFT,
        lutCubeAssetPath: String? = null,
        defaultAspect: AspectRatioPreset = AspectRatioPreset.RATIO_4_3,
        defaultResolution: ResolutionPreset = ResolutionPreset.DIGICAM_5MP,
        bloom: BloomSpec,
        grain: GrainSpec,
        optics: OpticsSpec,
        grunge: GrungeSpec = GrungeSpec(),
        defaultRecipeValues: DefaultRecipeValues = DefaultRecipeValues(),
        stamp: StampDefaults,
        defaultFrame: FrameBorderStyle = FrameBorderStyle.NONE,
        optionalFrame: FrameBorderStyle = FrameBorderStyle.NONE,
        soundAssetName: String,
        supportsVideo: Boolean = false,
        isD3dBurst: Boolean = false,
        isSpecialSeries: Boolean = false,
        defaultSkin: ChassisSkinDefinition,
        linkedSkins: List<String>
    ): CameraProfile {
        val mergedLinked = (listOf(defaultSkin.skinId) + linkedSkins).distinct()
        return CameraProfile(
            id = id,
            codeName = codeName,
            subtitle = subtitle,
            sampleDescription = sampleDescription,
            category = category,
            era = era,
            eraYearOffset = eraYearOffset,
            viewfinderMaskType = viewfinderMaskType,
            lutAssetPath = "luts_2d/$id.png",
            lutCubeAssetPath = lutCubeAssetPath,
            lutSize = 64,
            defaultAspect = defaultAspect,
            defaultResolution = defaultResolution,
            bloom = bloom,
            grain = grain,
            optics = optics,
            grunge = grunge,
            defaultRecipeValues = defaultRecipeValues,
            stamp = stamp,
            defaultFrame = defaultFrame,
            optionalFrame = optionalFrame,
            soundAssetName = soundAssetName,
            supportsVideo = supportsVideo,
            isD3dBurst = isD3dBurst,
            isSpecialSeries = isSpecialSeries,
            iconAssetPath = "skins/cam_icon_$id.png",
            defaultSkinId = defaultSkin.skinId,
            linkedSkinIds = mergedLinked,
            skin = defaultSkin.toCameraSkinSpec()
        )
    }

    // 1. Z30 (Default CCD)
    val z30 =
        profile(
            id = "z30",
            codeName = "Z30",
            subtitle = "2004 Classic Travel CCD",
            sampleDescription = "Balanced warm midtones, crisp cyan skies, and gentle 2004 highlight bloom.",
            category = CameraCategory.CCD,
            era = CameraEra.CCD_2000S,
            eraYearOffset = 2004,
            lutCubeAssetPath = "luts/dc_04_ccd.cube",
            defaultResolution = ResolutionPreset.CCD_3_2MP,
            bloom = BloomSpec(threshold = 0.80f, intensity = 0.42f, tintHex = "#FFF3E0"),
            grain = GrainSpec(type = GrainType.CCD_CHROMA_LUMA, lumaStrength = 0.14f, chromaStrength = 0.22f),
            optics = OpticsSpec(caStrength = 0.18f, vignetteStrength = 0.24f),
            defaultRecipeValues = DefaultRecipeValues(warmthShift = 0.06f, sharpen = 0.15f),
            stamp = StampDefaults(style = StampStyle.DOT_MATRIX_AMBER, fontStyle = StampFontStyle.DOT_MATRIX),
            soundAssetName = "shutter_ccd",
            defaultSkin = SkinCatalog.charcoalMetal,
            linkedSkins = defaultCcdSkins
        )

    // 2. IXUS95
    val ixus95 =
        profile(
            id = "ixus95",
            codeName = "IXUS95",
            subtitle = "2009 Pocket Metal CCD",
            sampleDescription = "Cool daylight whites, clean contrast, and subtle digital flash pop.",
            category = CameraCategory.CCD,
            era = CameraEra.CCD_2000S,
            eraYearOffset = 2009,
            bloom = BloomSpec(threshold = 0.82f, intensity = 0.36f, tintHex = "#EBF7FF"),
            grain = GrainSpec(type = GrainType.CCD_CHROMA_LUMA, lumaStrength = 0.12f, chromaStrength = 0.18f),
            optics = OpticsSpec(caStrength = 0.14f, vignetteStrength = 0.20f),
            defaultRecipeValues = DefaultRecipeValues(warmthShift = -0.05f, sharpen = 0.20f),
            stamp = StampDefaults(style = StampStyle.CLEAN_WHITE, fontStyle = StampFontStyle.DOT_MATRIX),
            soundAssetName = "shutter_ccd",
            defaultSkin = SkinCatalog.silverBrushed,
            linkedSkins = defaultCcdSkins
        )

    // 3. M532
    val m532 =
        profile(
            id = "m532",
            codeName = "M532",
            subtitle = "2006 Nocturne CCD",
            sampleDescription = "Moody cool shadows with glowing amber highlights for night street scenes.",
            category = CameraCategory.CCD,
            era = CameraEra.CCD_2000S,
            eraYearOffset = 2006,
            lutCubeAssetPath = "luts/nx_07_nocturne.cube",
            bloom = BloomSpec(threshold = 0.75f, intensity = 0.52f, tintHex = "#FFE8D6"),
            grain = GrainSpec(type = GrainType.CCD_CHROMA_LUMA, lumaStrength = 0.18f, chromaStrength = 0.28f),
            optics = OpticsSpec(caStrength = 0.20f, vignetteStrength = 0.30f),
            defaultRecipeValues = DefaultRecipeValues(shadows = 0.08f, warmthShift = -0.08f),
            stamp = StampDefaults(style = StampStyle.SEVEN_SEGMENT_EMERALD, fontStyle = StampFontStyle.SEVEN_SEGMENT),
            soundAssetName = "shutter_ccd",
            defaultSkin = SkinCatalog.charcoalMetal,
            linkedSkins = defaultCcdSkins
        )

    // 4. U300
    val u300 =
        profile(
            id = "u300",
            codeName = "U300",
            subtitle = "2003 Weatherproof CCD",
            sampleDescription = "Punchy seaside contrast, vivid aqua greens, and crisp edge definition.",
            category = CameraCategory.CCD,
            era = CameraEra.CCD_2000S,
            eraYearOffset = 2003,
            defaultResolution = ResolutionPreset.CCD_3_2MP,
            bloom = BloomSpec(threshold = 0.81f, intensity = 0.34f, tintHex = "#E6FBFF"),
            grain = GrainSpec(type = GrainType.CCD_CHROMA_LUMA, lumaStrength = 0.15f, chromaStrength = 0.20f),
            optics = OpticsSpec(caStrength = 0.16f, vignetteStrength = 0.22f),
            defaultRecipeValues = DefaultRecipeValues(highlights = -0.05f, sharpen = 0.22f),
            stamp = StampDefaults(style = StampStyle.DOT_MATRIX_AMBER, fontStyle = StampFontStyle.PIXEL),
            soundAssetName = "shutter_ccd",
            defaultSkin = SkinCatalog.y2kCyan,
            linkedSkins = defaultCcdSkins
        )

    // 5. IXUS210
    val ixus210 =
        profile(
            id = "ixus210",
            codeName = "IXUS210",
            subtitle = "2010 Pastel Wide CCD",
            sampleDescription = "Airy lifted pastel highlights and flattering soft skin rendering.",
            category = CameraCategory.CCD,
            era = CameraEra.CCD_2000S,
            eraYearOffset = 2010,
            defaultAspect = AspectRatioPreset.RATIO_16_9,
            bloom = BloomSpec(threshold = 0.78f, intensity = 0.40f, tintHex = "#FFF5F8"),
            grain = GrainSpec(type = GrainType.CCD_CHROMA_LUMA, lumaStrength = 0.10f, chromaStrength = 0.14f),
            optics = OpticsSpec(caStrength = 0.12f, vignetteStrength = 0.18f),
            defaultRecipeValues = DefaultRecipeValues(beautySoftness = 0.25f, highlights = 0.10f),
            stamp = StampDefaults(style = StampStyle.CLEAN_WHITE, fontStyle = StampFontStyle.CLEAN_SANS),
            soundAssetName = "shutter_ccd",
            defaultSkin = SkinCatalog.y2kLavender,
            linkedSkins = defaultCcdSkins
        )

    // 6. DC200
    val dc200 =
        profile(
            id = "dc200",
            codeName = "DC200",
            subtitle = "2001 Early Digicam CCD",
            sampleDescription = "Crunchy early-2000s digital grain with slight magenta shadows and warm highlights.",
            category = CameraCategory.CCD,
            era = CameraEra.CCD_2000S,
            eraYearOffset = 2001,
            defaultResolution = ResolutionPreset.CCD_3_2MP,
            bloom = BloomSpec(threshold = 0.77f, intensity = 0.44f, tintHex = "#FFEEDB"),
            grain = GrainSpec(type = GrainType.CCD_CHROMA_LUMA, lumaStrength = 0.22f, chromaStrength = 0.30f),
            optics = OpticsSpec(caStrength = 0.22f, vignetteStrength = 0.28f),
            defaultRecipeValues = DefaultRecipeValues(sharpen = 0.28f, warmthShift = 0.08f),
            stamp = StampDefaults(style = StampStyle.QUARTZ_ORANGE, fontStyle = StampFontStyle.PIXEL),
            soundAssetName = "shutter_ccd",
            defaultSkin = SkinCatalog.grungeSlate,
            linkedSkins = defaultCcdSkins
        )

    // 7. GR F
    val grf =
        profile(
            id = "grf",
            codeName = "GR F",
            subtitle = "Matte Street Positive Film",
            sampleDescription = "High-contrast urban street palette with deep blacks and punchy shadow separation.",
            category = CameraCategory.FILM,
            era = CameraEra.CLASSIC_35MM,
            eraYearOffset = 2005,
            viewfinderMaskType = ViewfinderMaskType.RANGEFINDER,
            defaultAspect = AspectRatioPreset.RATIO_3_2,
            defaultResolution = ResolutionPreset.VINTAGE_8MP,
            bloom = BloomSpec(threshold = 0.85f, intensity = 0.24f, tintHex = "#F5F5F0"),
            grain = GrainSpec(type = GrainType.FILM_ORGANIC, lumaStrength = 0.22f, chromaStrength = 0.08f),
            optics = OpticsSpec(caStrength = 0.10f, vignetteStrength = 0.34f),
            defaultRecipeValues = DefaultRecipeValues(shadows = -0.15f, sharpen = 0.30f),
            stamp = StampDefaults(
                style = StampStyle.CLASSIC_RED,
                defaultEnabled = false,
                fontStyle = StampFontStyle.CLEAN_SANS
            ),
            optionalFrame = FrameBorderStyle.FILM_35MM,
            soundAssetName = "shutter_rangefinder",
            defaultSkin = SkinCatalog.matteObsidian,
            linkedSkins = defaultFilmSkins
        )

    // 8. DCR
    val dcr =
        profile(
            id = "dcr",
            codeName = "DCR",
            subtitle = "2002 MiniDV Camcorder",
            sampleDescription = "Classic 2000s camcorder color with cool indoor tint, bloomy bulbs, and REC timecode.",
            category = CameraCategory.CAMCORDER,
            era = CameraEra.CAMCORDER_DV,
            eraYearOffset = 2002,
            viewfinderMaskType = ViewfinderMaskType.CAMCORDER_DV,
            defaultAspect = AspectRatioPreset.RATIO_4_3,
            defaultResolution = ResolutionPreset.CCD_3_2MP,
            bloom = BloomSpec(threshold = 0.72f, intensity = 0.56f, tintHex = "#E6F4FF"),
            grain = GrainSpec(type = GrainType.CCD_CHROMA_LUMA, lumaStrength = 0.20f, chromaStrength = 0.26f),
            optics = OpticsSpec(caStrength = 0.24f, vignetteStrength = 0.22f),
            stamp = StampDefaults(style = StampStyle.CLEAN_WHITE, fontStyle = StampFontStyle.VCR_OSD),
            defaultFrame = FrameBorderStyle.DV_TIMECODE,
            optionalFrame = FrameBorderStyle.NONE,
            soundAssetName = "shutter_ccd",
            supportsVideo = true,
            defaultSkin = SkinCatalog.dvCobaltMetallic,
            linkedSkins = defaultCamcorderSkins
        )

    // 9. V80
    val v80 =
        profile(
            id = "v80",
            codeName = "V80",
            subtitle = "1996 VHS / Video8 Tape",
            sampleDescription = "Analog magnetic tape warmth, chroma bleed, scanline softness, and nostalgic VCR HUD.",
            category = CameraCategory.CAMCORDER,
            era = CameraEra.CAMCORDER_DV,
            eraYearOffset = 1996,
            viewfinderMaskType = ViewfinderMaskType.VHS_TAPE,
            defaultAspect = AspectRatioPreset.RATIO_4_3,
            defaultResolution = ResolutionPreset.CCD_3_2MP,
            bloom = BloomSpec(threshold = 0.70f, intensity = 0.58f, tintHex = "#FFEBD6"),
            grain = GrainSpec(type = GrainType.CCD_CHROMA_LUMA, lumaStrength = 0.26f, chromaStrength = 0.34f),
            optics = OpticsSpec(caStrength = 0.32f, vignetteStrength = 0.30f),
            grunge = GrungeSpec(lightLeakStrength = 0.08f, dustStrength = 0.14f),
            stamp = StampDefaults(style = StampStyle.CLEAN_WHITE, fontStyle = StampFontStyle.VCR_OSD),
            defaultFrame = FrameBorderStyle.DV_TIMECODE,
            optionalFrame = FrameBorderStyle.NONE,
            soundAssetName = "shutter_ccd",
            supportsVideo = true,
            defaultSkin = SkinCatalog.vhsGraphite,
            linkedSkins = defaultCamcorderSkins
        )

    // 10. CCD-801
    val ccd801 =
        profile(
            id = "ccd801",
            codeName = "CCD-801",
            subtitle = "1999 Digital8 Camcorder",
            sampleDescription = "Early Digital8 emerald shadows and bright highlight rolloff for candid clips.",
            category = CameraCategory.CAMCORDER,
            era = CameraEra.CAMCORDER_DV,
            eraYearOffset = 1999,
            viewfinderMaskType = ViewfinderMaskType.CAMCORDER_DV,
            defaultAspect = AspectRatioPreset.RATIO_4_3,
            defaultResolution = ResolutionPreset.CCD_3_2MP,
            bloom = BloomSpec(threshold = 0.76f, intensity = 0.48f, tintHex = "#E9FFF6"),
            grain = GrainSpec(type = GrainType.CCD_CHROMA_LUMA, lumaStrength = 0.19f, chromaStrength = 0.24f),
            optics = OpticsSpec(caStrength = 0.22f, vignetteStrength = 0.24f),
            stamp = StampDefaults(style = StampStyle.SEVEN_SEGMENT_EMERALD, fontStyle = StampFontStyle.VCR_OSD),
            defaultFrame = FrameBorderStyle.DV_TIMECODE,
            optionalFrame = FrameBorderStyle.NONE,
            soundAssetName = "shutter_ccd",
            supportsVideo = true,
            defaultSkin = SkinCatalog.grungeSlate,
            linkedSkins = defaultCamcorderSkins
        )

    // 11. M20
    val m20 =
        profile(
            id = "m20",
            codeName = "M20",
            subtitle = "2008 Pocket Camcorder",
            sampleDescription = "Compact upright pocket video with punchy saturation and clean crisp edges.",
            category = CameraCategory.CAMCORDER,
            era = CameraEra.CAMCORDER_DV,
            eraYearOffset = 2008,
            viewfinderMaskType = ViewfinderMaskType.CAMCORDER_DV,
            defaultAspect = AspectRatioPreset.RATIO_16_9,
            bloom = BloomSpec(threshold = 0.79f, intensity = 0.38f, tintHex = "#FFF3E0"),
            grain = GrainSpec(type = GrainType.CCD_CHROMA_LUMA, lumaStrength = 0.14f, chromaStrength = 0.18f),
            optics = OpticsSpec(caStrength = 0.16f, vignetteStrength = 0.20f),
            stamp = StampDefaults(style = StampStyle.DOT_MATRIX_AMBER, fontStyle = StampFontStyle.VCR_OSD),
            defaultFrame = FrameBorderStyle.DV_TIMECODE,
            optionalFrame = FrameBorderStyle.NONE,
            soundAssetName = "shutter_ccd",
            supportsVideo = true,
            defaultSkin = SkinCatalog.dvCobaltMetallic,
            linkedSkins = defaultCamcorderSkins
        )

    // 12. M550
    val m550 =
        profile(
            id = "m550",
            codeName = "M550",
            subtitle = "2005 Everyday Digicam",
            sampleDescription = "Natural warm-neutral skin tones and gentle golden afternoon glow.",
            category = CameraCategory.CCD,
            era = CameraEra.CCD_2000S,
            eraYearOffset = 2005,
            bloom = BloomSpec(threshold = 0.80f, intensity = 0.36f, tintHex = "#FFF2DE"),
            grain = GrainSpec(type = GrainType.CCD_CHROMA_LUMA, lumaStrength = 0.13f, chromaStrength = 0.19f),
            optics = OpticsSpec(caStrength = 0.15f, vignetteStrength = 0.22f),
            defaultRecipeValues = DefaultRecipeValues(beautySoftness = 0.15f, warmthShift = 0.05f),
            stamp = StampDefaults(style = StampStyle.DOT_MATRIX_AMBER, fontStyle = StampFontStyle.DOT_MATRIX),
            soundAssetName = "shutter_ccd",
            defaultSkin = SkinCatalog.vintageCream,
            linkedSkins = defaultCcdSkins
        )

    // 13. T10
    val t10 =
        profile(
            id = "t10",
            codeName = "T10",
            subtitle = "2006 Slim Sliding CCD",
            sampleDescription = "Glossy contrast, saturated reds, and tight micro-contrast for flash portraits.",
            category = CameraCategory.CCD,
            era = CameraEra.CCD_2000S,
            eraYearOffset = 2006,
            bloom = BloomSpec(threshold = 0.78f, intensity = 0.44f, tintHex = "#FFF0EB"),
            grain = GrainSpec(type = GrainType.CCD_CHROMA_LUMA, lumaStrength = 0.14f, chromaStrength = 0.21f),
            optics = OpticsSpec(caStrength = 0.16f, vignetteStrength = 0.25f),
            defaultRecipeValues = DefaultRecipeValues(sharpen = 0.18f),
            stamp = StampDefaults(style = StampStyle.CLASSIC_RED, fontStyle = StampFontStyle.DOT_MATRIX),
            soundAssetName = "shutter_ccd",
            defaultSkin = SkinCatalog.studioMonochrome,
            linkedSkins = defaultCcdSkins
        )

    // 14. Inst M
    val instm =
        profile(
            id = "instm",
            codeName = "Inst M",
            subtitle = "Mini Instant Print",
            sampleDescription = "Creamy lifted blacks, pastel cyan-mint highlights, and signature mini paper border.",
            category = CameraCategory.INSTANT,
            era = CameraEra.INSTANT,
            eraYearOffset = 1998,
            viewfinderMaskType = ViewfinderMaskType.INSTANT_SQUARE,
            lutCubeAssetPath = "luts/inst_m_instant.cube",
            defaultAspect = AspectRatioPreset.RATIO_4_3,
            defaultResolution = ResolutionPreset.CCD_3_2MP,
            bloom = BloomSpec(threshold = 0.77f, intensity = 0.45f, tintHex = "#FFF6EC"),
            grain = GrainSpec(type = GrainType.FILM_ORGANIC, lumaStrength = 0.16f, chromaStrength = 0.10f),
            optics = OpticsSpec(caStrength = 0.16f, vignetteStrength = 0.28f),
            grunge = GrungeSpec(lightLeakStrength = 0.10f, dustStrength = 0.05f),
            defaultRecipeValues = DefaultRecipeValues(shadows = 0.12f, warmthShift = 0.04f),
            stamp = StampDefaults(style = StampStyle.CLEAN_WHITE, fontStyle = StampFontStyle.CLEAN_SANS),
            defaultFrame = FrameBorderStyle.INSTANT_PRINT,
            optionalFrame = FrameBorderStyle.NONE,
            soundAssetName = "shutter_instant",
            defaultSkin = SkinCatalog.instPastelMint,
            linkedSkins = defaultInstantSkins
        )

    // 15. Inst L
    val instl =
        profile(
            id = "instl",
            codeName = "Inst L",
            subtitle = "Wide Instant Print",
            sampleDescription = "Bright wide-format instant stock with soft pastel highlight rolloff.",
            category = CameraCategory.INSTANT,
            era = CameraEra.INSTANT,
            eraYearOffset = 2001,
            viewfinderMaskType = ViewfinderMaskType.INSTANT_WIDE,
            defaultAspect = AspectRatioPreset.RATIO_3_2,
            defaultResolution = ResolutionPreset.DIGICAM_5MP,
            bloom = BloomSpec(threshold = 0.78f, intensity = 0.42f, tintHex = "#FFF7F0"),
            grain = GrainSpec(type = GrainType.FILM_ORGANIC, lumaStrength = 0.15f, chromaStrength = 0.09f),
            optics = OpticsSpec(caStrength = 0.14f, vignetteStrength = 0.24f),
            grunge = GrungeSpec(lightLeakStrength = 0.08f, dustStrength = 0.04f),
            defaultRecipeValues = DefaultRecipeValues(shadows = 0.10f),
            stamp = StampDefaults(style = StampStyle.CLEAN_WHITE, fontStyle = StampFontStyle.CLEAN_SANS),
            defaultFrame = FrameBorderStyle.INSTANT_WIDE,
            optionalFrame = FrameBorderStyle.NONE,
            soundAssetName = "shutter_instant",
            defaultSkin = SkinCatalog.instBlush,
            linkedSkins = defaultInstantSkins
        )

    // 16. FXN R
    val fxnr =
        profile(
            id = "fxnr",
            codeName = "FXN R",
            subtitle = "Classic Rangefinder Negative",
            sampleDescription = "Lush emerald greens, warm amber highlights, and classic negative latitude.",
            category = CameraCategory.FILM,
            era = CameraEra.CLASSIC_35MM,
            eraYearOffset = 1984,
            viewfinderMaskType = ViewfinderMaskType.RANGEFINDER,
            defaultAspect = AspectRatioPreset.RATIO_3_2,
            defaultResolution = ResolutionPreset.VINTAGE_8MP,
            bloom = BloomSpec(threshold = 0.83f, intensity = 0.30f, tintHex = "#FFF7E8"),
            grain = GrainSpec(type = GrainType.FILM_ORGANIC, lumaStrength = 0.20f, chromaStrength = 0.10f),
            optics = OpticsSpec(caStrength = 0.12f, vignetteStrength = 0.24f),
            grunge = GrungeSpec(lightLeakStrength = 0.06f, dustStrength = 0.08f),
            defaultRecipeValues = DefaultRecipeValues(warmthShift = 0.08f),
            stamp = StampDefaults(style = StampStyle.CLASSIC_RED, fontStyle = StampFontStyle.SEVEN_SEGMENT),
            optionalFrame = FrameBorderStyle.FILM_35MM,
            soundAssetName = "shutter_rangefinder",
            defaultSkin = SkinCatalog.silverBrushed,
            linkedSkins = defaultFilmSkins
        )

    // 17. Quik
    val quik =
        profile(
            id = "quik",
            codeName = "Quik",
            subtitle = "1998 Disposable Peephole",
            sampleDescription = "Fixed-focus 800-speed daylight film with warm green shadow cast and flash punch.",
            category = CameraCategory.FILM,
            era = CameraEra.DISPOSABLE_90S,
            eraYearOffset = 1998,
            viewfinderMaskType = ViewfinderMaskType.DISPOSABLE_PEEPHOLE,
            lutCubeAssetPath = "luts/d_98_disposable.cube",
            defaultAspect = AspectRatioPreset.RATIO_3_2,
            bloom = BloomSpec(threshold = 0.82f, intensity = 0.32f, tintHex = "#FFF1D0"),
            grain = GrainSpec(type = GrainType.FILM_ORGANIC, lumaStrength = 0.24f, chromaStrength = 0.12f),
            optics = OpticsSpec(caStrength = 0.24f, vignetteStrength = 0.38f),
            grunge = GrungeSpec(lightLeakStrength = 0.20f, dustStrength = 0.15f),
            stamp = StampDefaults(style = StampStyle.QUARTZ_ORANGE, fontStyle = StampFontStyle.SEVEN_SEGMENT),
            optionalFrame = FrameBorderStyle.FILM_35MM,
            soundAssetName = "shutter_disposable",
            defaultSkin = SkinCatalog.quikYellowFilm,
            linkedSkins = defaultFilmSkins
        )

    // 18. D3D
    val d3d =
        profile(
            id = "d3d",
            codeName = "D3D",
            subtitle = "4-Lens Wigglegram Burst",
            sampleDescription = "Sequential 4-frame burst with retro 400-film punch for animated 3D wigglegrams.",
            category = CameraCategory.SPECIAL,
            era = CameraEra.SPECIAL_MULTI,
            eraYearOffset = 1989,
            viewfinderMaskType = ViewfinderMaskType.MULTI_D3D,
            defaultAspect = AspectRatioPreset.RATIO_3_2,
            defaultResolution = ResolutionPreset.CCD_3_2MP,
            bloom = BloomSpec(threshold = 0.80f, intensity = 0.36f, tintHex = "#FFF3E0"),
            grain = GrainSpec(type = GrainType.FILM_ORGANIC, lumaStrength = 0.22f, chromaStrength = 0.14f),
            optics = OpticsSpec(caStrength = 0.20f, vignetteStrength = 0.32f),
            grunge = GrungeSpec(lightLeakStrength = 0.12f, dustStrength = 0.10f),
            stamp = StampDefaults(style = StampStyle.QUARTZ_ORANGE, defaultEnabled = false),
            optionalFrame = FrameBorderStyle.FILM_35MM,
            soundAssetName = "shutter_disposable",
            supportsVideo = true,
            isD3dBurst = true,
            isSpecialSeries = true,
            defaultSkin = SkinCatalog.matteObsidian,
            linkedSkins = listOf(
                SkinCatalog.matteObsidian.skinId,
                SkinCatalog.cyberTranslucent.skinId,
                SkinCatalog.charcoalMetal.skinId,
                SkinCatalog.grungeSlate.skinId
            )
        )

    // 19. N800
    val n800 =
        profile(
            id = "n800",
            codeName = "N800",
            subtitle = "2002 Prosumer CCD",
            sampleDescription = "Deep emerald shadows and rich golden highlights built for golden hour landscapes.",
            category = CameraCategory.CCD,
            era = CameraEra.CCD_2000S,
            eraYearOffset = 2002,
            bloom = BloomSpec(threshold = 0.81f, intensity = 0.35f, tintHex = "#FFF0D6"),
            grain = GrainSpec(type = GrainType.CCD_CHROMA_LUMA, lumaStrength = 0.15f, chromaStrength = 0.20f),
            optics = OpticsSpec(caStrength = 0.16f, vignetteStrength = 0.24f),
            defaultRecipeValues = DefaultRecipeValues(warmthShift = 0.10f, sharpen = 0.16f),
            stamp = StampDefaults(style = StampStyle.DOT_MATRIX_AMBER, fontStyle = StampFontStyle.DOT_MATRIX),
            soundAssetName = "shutter_ccd",
            defaultSkin = SkinCatalog.dvCobaltMetallic,
            linkedSkins = defaultCcdSkins
        )

    // 20. F100
    val f100 =
        profile(
            id = "f100",
            codeName = "F100",
            subtitle = "1999 Golden 35mm SLR",
            sampleDescription = "Warm golden emulsion with silky skin rendering and balanced midtone saturation.",
            category = CameraCategory.FILM,
            era = CameraEra.CLASSIC_35MM,
            eraYearOffset = 1999,
            viewfinderMaskType = ViewfinderMaskType.RANGEFINDER,
            defaultAspect = AspectRatioPreset.RATIO_3_2,
            defaultResolution = ResolutionPreset.VINTAGE_8MP,
            bloom = BloomSpec(threshold = 0.82f, intensity = 0.32f, tintHex = "#FFF3D9"),
            grain = GrainSpec(type = GrainType.FILM_ORGANIC, lumaStrength = 0.19f, chromaStrength = 0.09f),
            optics = OpticsSpec(caStrength = 0.13f, vignetteStrength = 0.22f),
            grunge = GrungeSpec(lightLeakStrength = 0.05f, dustStrength = 0.06f),
            defaultRecipeValues = DefaultRecipeValues(warmthShift = 0.12f),
            stamp = StampDefaults(style = StampStyle.QUARTZ_ORANGE, fontStyle = StampFontStyle.SEVEN_SEGMENT),
            optionalFrame = FrameBorderStyle.FILM_35MM,
            soundAssetName = "shutter_rangefinder",
            defaultSkin = SkinCatalog.titaniumChampagne,
            linkedSkins = defaultFilmSkins
        )

    // 21. S20
    val s20 =
        profile(
            id = "s20",
            codeName = "S20",
            subtitle = "2000 First-Gen Pocket CCD",
            sampleDescription = "Cool cobalt skies, soft highlight halation, and authentic 2-megapixel nostalgia.",
            category = CameraCategory.CCD,
            era = CameraEra.CCD_2000S,
            eraYearOffset = 2000,
            defaultResolution = ResolutionPreset.CCD_3_2MP,
            bloom = BloomSpec(threshold = 0.76f, intensity = 0.46f, tintHex = "#EDF7FF"),
            grain = GrainSpec(type = GrainType.CCD_CHROMA_LUMA, lumaStrength = 0.18f, chromaStrength = 0.25f),
            optics = OpticsSpec(caStrength = 0.20f, vignetteStrength = 0.26f),
            defaultRecipeValues = DefaultRecipeValues(warmthShift = -0.06f),
            stamp = StampDefaults(style = StampStyle.SEVEN_SEGMENT_EMERALD, fontStyle = StampFontStyle.PIXEL),
            soundAssetName = "shutter_ccd",
            defaultSkin = SkinCatalog.silverBrushed,
            linkedSkins = defaultCcdSkins
        )

    // 22. Lomo
    val lomo =
        profile(
            id = "lomo",
            codeName = "Lomo",
            subtitle = "1994 Cross-Process Grunge",
            sampleDescription = "Heavy vignette, saturated cross-processed hues, and organic edge light leaks.",
            category = CameraCategory.FILM,
            era = CameraEra.GRUNGE_90S,
            eraYearOffset = 1994,
            lutCubeAssetPath = "luts/lomo_94_grunge.cube",
            bloom = BloomSpec(threshold = 0.78f, intensity = 0.48f, tintHex = "#FFD8C2"),
            grain = GrainSpec(type = GrainType.FILM_ORGANIC, lumaStrength = 0.32f, chromaStrength = 0.18f),
            optics = OpticsSpec(caStrength = 0.28f, vignetteStrength = 0.45f),
            grunge = GrungeSpec(lightLeakStrength = 0.55f, dustStrength = 0.45f),
            stamp = StampDefaults(style = StampStyle.QUARTZ_ORANGE, fontStyle = StampFontStyle.SEVEN_SEGMENT),
            optionalFrame = FrameBorderStyle.FILM_35MM,
            soundAssetName = "shutter_disposable",
            defaultSkin = SkinCatalog.matteObsidian,
            linkedSkins = defaultFilmSkins
        )

    // 23. A35
    val a35 =
        profile(
            id = "a35",
            codeName = "A35",
            subtitle = "1979 Vintage Golden 35mm",
            sampleDescription = "Faded honey highlights, lifted matte shadows, and gentle 70s editorial warmth.",
            category = CameraCategory.FILM,
            era = CameraEra.CLASSIC_35MM,
            eraYearOffset = 1979,
            viewfinderMaskType = ViewfinderMaskType.RANGEFINDER,
            defaultAspect = AspectRatioPreset.RATIO_3_2,
            bloom = BloomSpec(threshold = 0.80f, intensity = 0.36f, tintHex = "#FFEFD2"),
            grain = GrainSpec(type = GrainType.FILM_ORGANIC, lumaStrength = 0.22f, chromaStrength = 0.10f),
            optics = OpticsSpec(caStrength = 0.15f, vignetteStrength = 0.28f),
            grunge = GrungeSpec(lightLeakStrength = 0.12f, dustStrength = 0.14f),
            defaultRecipeValues = DefaultRecipeValues(warmthShift = 0.14f, shadows = 0.10f),
            stamp = StampDefaults(style = StampStyle.DOT_MATRIX_AMBER, fontStyle = StampFontStyle.DOT_MATRIX),
            optionalFrame = FrameBorderStyle.FILM_35MM,
            soundAssetName = "shutter_rangefinder",
            defaultSkin = SkinCatalog.leatherCognac,
            linkedSkins = defaultFilmSkins
        )

    // 24. X20
    val x20 =
        profile(
            id = "x20",
            codeName = "X20",
            subtitle = "Dual-Tone Optical Finder",
            sampleDescription = "Modern-retro chrome film simulation with crisp shadow detail and creamy skies.",
            category = CameraCategory.FILM,
            era = CameraEra.CLASSIC_35MM,
            eraYearOffset = 2013,
            viewfinderMaskType = ViewfinderMaskType.RANGEFINDER,
            defaultAspect = AspectRatioPreset.RATIO_3_2,
            defaultResolution = ResolutionPreset.VINTAGE_8MP,
            bloom = BloomSpec(threshold = 0.83f, intensity = 0.30f, tintHex = "#FFF8EE"),
            grain = GrainSpec(type = GrainType.FILM_ORGANIC, lumaStrength = 0.14f, chromaStrength = 0.06f),
            optics = OpticsSpec(caStrength = 0.10f, vignetteStrength = 0.20f),
            defaultRecipeValues = DefaultRecipeValues(sharpen = 0.20f),
            stamp = StampDefaults(style = StampStyle.CLEAN_WHITE, fontStyle = StampFontStyle.CLEAN_SANS),
            optionalFrame = FrameBorderStyle.FILM_35MM,
            soundAssetName = "shutter_rangefinder",
            defaultSkin = SkinCatalog.leatherOlive,
            linkedSkins = defaultFilmSkins
        )

    // 25. Polar
    val polar =
        profile(
            id = "polar",
            codeName = "Polar",
            subtitle = "Square SX-70 Instant",
            sampleDescription = "Dreamy 1:1 square instant emulsion with creamy amber whites and vintage teal shadows.",
            category = CameraCategory.INSTANT,
            era = CameraEra.INSTANT,
            eraYearOffset = 1982,
            viewfinderMaskType = ViewfinderMaskType.INSTANT_SQUARE,
            defaultAspect = AspectRatioPreset.RATIO_1_1,
            defaultResolution = ResolutionPreset.CCD_3_2MP,
            bloom = BloomSpec(threshold = 0.75f, intensity = 0.48f, tintHex = "#FFF2E2"),
            grain = GrainSpec(type = GrainType.FILM_ORGANIC, lumaStrength = 0.18f, chromaStrength = 0.12f),
            optics = OpticsSpec(caStrength = 0.18f, vignetteStrength = 0.32f),
            grunge = GrungeSpec(lightLeakStrength = 0.14f, dustStrength = 0.08f),
            defaultRecipeValues = DefaultRecipeValues(warmthShift = 0.08f, shadows = 0.12f),
            stamp = StampDefaults(style = StampStyle.CLEAN_WHITE, defaultEnabled = false),
            defaultFrame = FrameBorderStyle.POLAROID_SQUARE,
            optionalFrame = FrameBorderStyle.NONE,
            soundAssetName = "shutter_instant",
            defaultSkin = SkinCatalog.polarIvory,
            linkedSkins = defaultInstantSkins
        )

    // 26. DV
    val dv =
        profile(
            id = "dv",
            codeName = "DV",
            subtitle = "Pocket Digital Video",
            sampleDescription = "Clean nostalgic DV video look with warm skin contrast and crisp OSD readout.",
            category = CameraCategory.CAMCORDER,
            era = CameraEra.CAMCORDER_DV,
            eraYearOffset = 2005,
            viewfinderMaskType = ViewfinderMaskType.CAMCORDER_DV,
            defaultAspect = AspectRatioPreset.RATIO_16_9,
            bloom = BloomSpec(threshold = 0.78f, intensity = 0.42f, tintHex = "#FFF5EA"),
            grain = GrainSpec(type = GrainType.CCD_CHROMA_LUMA, lumaStrength = 0.16f, chromaStrength = 0.20f),
            optics = OpticsSpec(caStrength = 0.18f, vignetteStrength = 0.22f),
            stamp = StampDefaults(style = StampStyle.CLEAN_WHITE, fontStyle = StampFontStyle.VCR_OSD),
            defaultFrame = FrameBorderStyle.DV_TIMECODE,
            optionalFrame = FrameBorderStyle.NONE,
            soundAssetName = "shutter_ccd",
            supportsVideo = true,
            defaultSkin = SkinCatalog.dvCobaltMetallic,
            linkedSkins = defaultCamcorderSkins
        )

    // 27. G7X
    val g7x =
        profile(
            id = "g7x",
            codeName = "G7X",
            subtitle = "2014 Portrait Compact CCD",
            sampleDescription = "Luminous skin tones, gentle beauty glow, and crisp night-flash clarity.",
            category = CameraCategory.CCD,
            era = CameraEra.CCD_2000S,
            eraYearOffset = 2014,
            defaultResolution = ResolutionPreset.VINTAGE_8MP,
            bloom = BloomSpec(threshold = 0.79f, intensity = 0.40f, tintHex = "#FFF3EE"),
            grain = GrainSpec(type = GrainType.CCD_CHROMA_LUMA, lumaStrength = 0.08f, chromaStrength = 0.12f),
            optics = OpticsSpec(caStrength = 0.10f, vignetteStrength = 0.18f),
            defaultRecipeValues = DefaultRecipeValues(beautySoftness = 0.30f, highlights = -0.05f),
            stamp = StampDefaults(style = StampStyle.CLEAN_WHITE, fontStyle = StampFontStyle.CLEAN_SANS),
            soundAssetName = "shutter_ccd",
            defaultSkin = SkinCatalog.charcoalMetal,
            linkedSkins = defaultCcdSkins
        )

    // 28. Mini99
    val mini99 =
        profile(
            id = "mini99",
            codeName = "Mini99",
            subtitle = "Color-Dial Instant Analog",
            sampleDescription = "Expressive instant stock with rich vignette and subtle warm light leaks.",
            category = CameraCategory.INSTANT,
            era = CameraEra.INSTANT,
            eraYearOffset = 2004,
            viewfinderMaskType = ViewfinderMaskType.INSTANT_SQUARE,
            defaultAspect = AspectRatioPreset.RATIO_4_3,
            defaultResolution = ResolutionPreset.DIGICAM_5MP,
            bloom = BloomSpec(threshold = 0.76f, intensity = 0.44f, tintHex = "#FFE8DC"),
            grain = GrainSpec(type = GrainType.FILM_ORGANIC, lumaStrength = 0.17f, chromaStrength = 0.12f),
            optics = OpticsSpec(caStrength = 0.18f, vignetteStrength = 0.34f),
            grunge = GrungeSpec(lightLeakStrength = 0.28f, dustStrength = 0.10f),
            stamp = StampDefaults(style = StampStyle.CLASSIC_RED, fontStyle = StampFontStyle.CLEAN_SANS),
            defaultFrame = FrameBorderStyle.INSTANT_PRINT,
            optionalFrame = FrameBorderStyle.NONE,
            soundAssetName = "shutter_instant",
            defaultSkin = SkinCatalog.matteObsidian,
            linkedSkins = defaultInstantSkins
        )

    // 29. F505
    val f505 =
        profile(
            id = "f505",
            codeName = "F505",
            subtitle = "1999 Swivel-Lens Prosumer",
            sampleDescription = "Bold cobalt blues, crisp optical contrast, and vintage swivel-lens digicam look.",
            category = CameraCategory.CCD,
            era = CameraEra.CCD_2000S,
            eraYearOffset = 1999,
            defaultResolution = ResolutionPreset.CCD_3_2MP,
            bloom = BloomSpec(threshold = 0.80f, intensity = 0.38f, tintHex = "#E8F4FF"),
            grain = GrainSpec(type = GrainType.CCD_CHROMA_LUMA, lumaStrength = 0.18f, chromaStrength = 0.24f),
            optics = OpticsSpec(caStrength = 0.18f, vignetteStrength = 0.24f),
            defaultRecipeValues = DefaultRecipeValues(sharpen = 0.24f, warmthShift = -0.04f),
            stamp = StampDefaults(style = StampStyle.SEVEN_SEGMENT_EMERALD, fontStyle = StampFontStyle.SEVEN_SEGMENT),
            soundAssetName = "shutter_ccd",
            defaultSkin = SkinCatalog.silverBrushed,
            linkedSkins = defaultCcdSkins
        )

    // 30. F50
    val f50 =
        profile(
            id = "f50",
            codeName = "F50",
            subtitle = "2007 Warm SuperCCD",
            sampleDescription = "Creamy golden highlights and natural film-like dynamic range from 2007.",
            category = CameraCategory.CCD,
            era = CameraEra.CCD_2000S,
            eraYearOffset = 2007,
            bloom = BloomSpec(threshold = 0.79f, intensity = 0.40f, tintHex = "#FFF4E2"),
            grain = GrainSpec(type = GrainType.CCD_CHROMA_LUMA, lumaStrength = 0.12f, chromaStrength = 0.16f),
            optics = OpticsSpec(caStrength = 0.13f, vignetteStrength = 0.21f),
            defaultRecipeValues = DefaultRecipeValues(warmthShift = 0.08f, highlights = -0.05f),
            stamp = StampDefaults(style = StampStyle.DOT_MATRIX_AMBER, fontStyle = StampFontStyle.DOT_MATRIX),
            soundAssetName = "shutter_ccd",
            defaultSkin = SkinCatalog.titaniumChampagne,
            linkedSkins = defaultCcdSkins
        )

    // 31. C40
    val c40 =
        profile(
            id = "c40",
            codeName = "C40",
            subtitle = "2001 Compact Silver CCD",
            sampleDescription = "Soft highlight glow with milky greens and relaxed early-2000s color.",
            category = CameraCategory.CCD,
            era = CameraEra.CCD_2000S,
            eraYearOffset = 2001,
            defaultResolution = ResolutionPreset.CCD_3_2MP,
            bloom = BloomSpec(threshold = 0.77f, intensity = 0.44f, tintHex = "#F7F8E8"),
            grain = GrainSpec(type = GrainType.CCD_CHROMA_LUMA, lumaStrength = 0.17f, chromaStrength = 0.22f),
            optics = OpticsSpec(caStrength = 0.19f, vignetteStrength = 0.26f),
            stamp = StampDefaults(style = StampStyle.DOT_MATRIX_AMBER, fontStyle = StampFontStyle.PIXEL),
            soundAssetName = "shutter_ccd",
            defaultSkin = SkinCatalog.y2kCyan,
            linkedSkins = defaultCcdSkins
        )

    // 32. S600
    val s600 =
        profile(
            id = "s600",
            codeName = "S600",
            subtitle = "2006 Metallic Ultra-Compact",
            sampleDescription = "Vivid street colors, crisp contrast, and snappy flash-ready highlights.",
            category = CameraCategory.CCD,
            era = CameraEra.CCD_2000S,
            eraYearOffset = 2006,
            bloom = BloomSpec(threshold = 0.81f, intensity = 0.36f, tintHex = "#FFF4EB"),
            grain = GrainSpec(type = GrainType.CCD_CHROMA_LUMA, lumaStrength = 0.14f, chromaStrength = 0.20f),
            optics = OpticsSpec(caStrength = 0.15f, vignetteStrength = 0.23f),
            defaultRecipeValues = DefaultRecipeValues(sharpen = 0.20f),
            stamp = StampDefaults(style = StampStyle.QUARTZ_ORANGE, fontStyle = StampFontStyle.DOT_MATRIX),
            soundAssetName = "shutter_ccd",
            defaultSkin = SkinCatalog.silverBrushed,
            linkedSkins = defaultCcdSkins
        )

    // 33. NT16
    val nt16 =
        profile(
            id = "nt16",
            codeName = "NT16",
            subtitle = "16mm Cine Film",
            sampleDescription = "Organic 16mm motion-picture halation, dense grain, and gate-masked cine framing.",
            category = CameraCategory.FILM,
            era = CameraEra.CLASSIC_35MM,
            eraYearOffset = 1974,
            viewfinderMaskType = ViewfinderMaskType.CAMCORDER_DV,
            defaultAspect = AspectRatioPreset.RATIO_16_9,
            bloom = BloomSpec(threshold = 0.76f, intensity = 0.48f, tintHex = "#FFEAD1"),
            grain = GrainSpec(type = GrainType.FILM_ORGANIC, lumaStrength = 0.28f, chromaStrength = 0.14f),
            optics = OpticsSpec(caStrength = 0.16f, vignetteStrength = 0.34f),
            grunge = GrungeSpec(lightLeakStrength = 0.12f, dustStrength = 0.20f),
            stamp = StampDefaults(style = StampStyle.DOT_MATRIX_AMBER, defaultEnabled = false),
            defaultFrame = FrameBorderStyle.CINE_16MM,
            optionalFrame = FrameBorderStyle.NONE,
            soundAssetName = "shutter_rangefinder",
            supportsVideo = true,
            defaultSkin = SkinCatalog.cine16mm,
            linkedSkins = defaultFilmSkins + SkinCatalog.cine16mm.skinId
        )

    // 34. H20
    val h20 =
        profile(
            id = "h20",
            codeName = "H20",
            subtitle = "2009 Travel Zoom CCD",
            sampleDescription = "Deep contrasty blues and crisp daylight clarity for travel and cityscapes.",
            category = CameraCategory.CCD,
            era = CameraEra.CCD_2000S,
            eraYearOffset = 2009,
            bloom = BloomSpec(threshold = 0.82f, intensity = 0.32f, tintHex = "#EDF6FF"),
            grain = GrainSpec(type = GrainType.CCD_CHROMA_LUMA, lumaStrength = 0.12f, chromaStrength = 0.16f),
            optics = OpticsSpec(caStrength = 0.14f, vignetteStrength = 0.22f),
            defaultRecipeValues = DefaultRecipeValues(sharpen = 0.18f, warmthShift = -0.04f),
            stamp = StampDefaults(style = StampStyle.CLEAN_WHITE, fontStyle = StampFontStyle.DOT_MATRIX),
            soundAssetName = "shutter_ccd",
            defaultSkin = SkinCatalog.charcoalMetal,
            linkedSkins = defaultCcdSkins
        )

    // 35. H70
    val h70 =
        profile(
            id = "h70",
            codeName = "H70",
            subtitle = "2011 Superzoom CCD",
            sampleDescription = "Clean high-clarity daylight color with gentle warm highlight rolloff.",
            category = CameraCategory.CCD,
            era = CameraEra.CCD_2000S,
            eraYearOffset = 2011,
            defaultResolution = ResolutionPreset.VINTAGE_8MP,
            bloom = BloomSpec(threshold = 0.83f, intensity = 0.30f, tintHex = "#FFF7EE"),
            grain = GrainSpec(type = GrainType.CCD_CHROMA_LUMA, lumaStrength = 0.10f, chromaStrength = 0.14f),
            optics = OpticsSpec(caStrength = 0.12f, vignetteStrength = 0.19f),
            defaultRecipeValues = DefaultRecipeValues(sharpen = 0.16f),
            stamp = StampDefaults(style = StampStyle.CLEAN_WHITE, fontStyle = StampFontStyle.CLEAN_SANS),
            soundAssetName = "shutter_ccd",
            defaultSkin = SkinCatalog.leatherCognac,
            linkedSkins = defaultCcdSkins
        )

    // 36. M7
    val m7 =
        profile(
            id = "m7",
            codeName = "M7",
            subtitle = "1977 Classic 35mm Rangefinder",
            sampleDescription = "Timeless 35mm rangefinder contrast, natural warm flesh tones, and brass trim.",
            category = CameraCategory.FILM,
            era = CameraEra.CLASSIC_35MM,
            eraYearOffset = 1977,
            viewfinderMaskType = ViewfinderMaskType.RANGEFINDER,
            lutCubeAssetPath = "luts/rf_77_classic.cube",
            defaultAspect = AspectRatioPreset.RATIO_3_2,
            defaultResolution = ResolutionPreset.VINTAGE_8MP,
            bloom = BloomSpec(threshold = 0.84f, intensity = 0.28f, tintHex = "#FFF7E6"),
            grain = GrainSpec(type = GrainType.FILM_ORGANIC, lumaStrength = 0.20f, chromaStrength = 0.08f),
            optics = OpticsSpec(caStrength = 0.12f, vignetteStrength = 0.24f),
            grunge = GrungeSpec(lightLeakStrength = 0.05f, dustStrength = 0.08f),
            stamp = StampDefaults(style = StampStyle.CLASSIC_RED, fontStyle = StampFontStyle.SEVEN_SEGMENT),
            optionalFrame = FrameBorderStyle.FILM_35MM,
            soundAssetName = "shutter_rangefinder",
            defaultSkin = SkinCatalog.rangefinderBrass,
            linkedSkins = defaultFilmSkins
        )

    // 37. SX70 (Special - Instant)
    val sx70 =
        profile(
            id = "sx70",
            codeName = "SX70",
            subtitle = "Square SX-70 Instant",
            sampleDescription = "Dreamy teal-green shadows and warm highlights on classic square instant stock.",
            category = CameraCategory.INSTANT,
            era = CameraEra.INSTANT,
            eraYearOffset = 1978,
            viewfinderMaskType = ViewfinderMaskType.INSTANT_SQUARE,
            defaultAspect = AspectRatioPreset.RATIO_1_1,
            defaultResolution = ResolutionPreset.CCD_3_2MP,
            bloom = BloomSpec(threshold = 0.76f, intensity = 0.46f, tintHex = "#FFF2E2"),
            grain = GrainSpec(type = GrainType.FILM_ORGANIC, lumaStrength = 0.18f, chromaStrength = 0.11f),
            optics = OpticsSpec(caStrength = 0.16f, vignetteStrength = 0.30f),
            grunge = GrungeSpec(lightLeakStrength = 0.12f, dustStrength = 0.07f),
            defaultRecipeValues = DefaultRecipeValues(warmthShift = 0.10f, shadows = 0.10f),
            stamp = StampDefaults(style = StampStyle.CLEAN_WHITE, defaultEnabled = false),
            defaultFrame = FrameBorderStyle.POLAROID_SQUARE,
            optionalFrame = FrameBorderStyle.NONE,
            soundAssetName = "shutter_instant",
            isSpecialSeries = true,
            defaultSkin = SkinCatalog.polarIvory,
            linkedSkins = defaultInstantSkins
        )

    // 38. 2000S (Special - Bloom)
    val bloom2000s =
        profile(
            id = "2000s",
            codeName = "2000S",
            subtitle = "2000s Bloom Dreamy CCD",
            sampleDescription = "Dreamy soft-glow Y2K CCD with pastel highlights and airy contrast.",
            category = CameraCategory.SPECIAL,
            era = CameraEra.CCD_2000S,
            eraYearOffset = 2002,
            bloom = BloomSpec(threshold = 0.68f, intensity = 0.65f, tintHex = "#FFF6FA"),
            grain = GrainSpec(type = GrainType.CCD_CHROMA_LUMA, lumaStrength = 0.11f, chromaStrength = 0.15f),
            optics = OpticsSpec(caStrength = 0.14f, vignetteStrength = 0.18f),
            defaultRecipeValues = DefaultRecipeValues(
                beautySoftness = 0.35f,
                warmthShift = 0.08f,
                lensAttachment = LensAttachment.SOFT_GLOW
            ),
            stamp = StampDefaults(style = StampStyle.CLEAN_WHITE, fontStyle = StampFontStyle.PIXEL),
            soundAssetName = "shutter_ccd",
            isSpecialSeries = true,
            defaultSkin = SkinCatalog.bloom2000s,
            linkedSkins = listOf(SkinCatalog.bloom2000s.skinId) + defaultCcdSkins
        )

    // 39. BIG SHOT (Special - Studio Portrait)
    val bigShot =
        profile(
            id = "big_shot",
            codeName = "BIG SHOT",
            subtitle = "Portrait Flash Studio",
            sampleDescription = "Punchy portrait flash contrast with warm glowing skin tones.",
            category = CameraCategory.SPECIAL,
            era = CameraEra.INSTANT,
            eraYearOffset = 1974,
            viewfinderMaskType = ViewfinderMaskType.INSTANT_WIDE,
            defaultAspect = AspectRatioPreset.RATIO_4_3,
            bloom = BloomSpec(threshold = 0.78f, intensity = 0.42f, tintHex = "#FFF0E0"),
            grain = GrainSpec(type = GrainType.FILM_ORGANIC, lumaStrength = 0.16f, chromaStrength = 0.10f),
            optics = OpticsSpec(caStrength = 0.14f, vignetteStrength = 0.28f),
            defaultRecipeValues = DefaultRecipeValues(warmthShift = 0.12f),
            stamp = StampDefaults(style = StampStyle.CLEAN_WHITE, defaultEnabled = false),
            defaultFrame = FrameBorderStyle.INSTANT_WIDE,
            optionalFrame = FrameBorderStyle.NONE,
            soundAssetName = "shutter_instant",
            isSpecialSeries = true,
            defaultSkin = SkinCatalog.charcoalMetal,
            linkedSkins = defaultInstantSkins + SkinCatalog.charcoalMetal.skinId
        )

    // 40. DISPO (Special - Disposable Film)
    val dispo =
        profile(
            id = "dispo",
            codeName = "DISPO",
            subtitle = "Classic Green/Yellow Disposable",
            sampleDescription = "Lifted shadows, punchy daylight green-yellow cast, and optical peephole framing.",
            category = CameraCategory.FILM,
            era = CameraEra.DISPOSABLE_90S,
            eraYearOffset = 1998,
            viewfinderMaskType = ViewfinderMaskType.DISPOSABLE_PEEPHOLE,
            defaultAspect = AspectRatioPreset.RATIO_3_2,
            bloom = BloomSpec(threshold = 0.81f, intensity = 0.34f, tintHex = "#FFF3D2"),
            grain = GrainSpec(type = GrainType.FILM_ORGANIC, lumaStrength = 0.25f, chromaStrength = 0.12f),
            optics = OpticsSpec(caStrength = 0.24f, vignetteStrength = 0.36f),
            grunge = GrungeSpec(lightLeakStrength = 0.18f, dustStrength = 0.14f),
            stamp = StampDefaults(style = StampStyle.QUARTZ_ORANGE, fontStyle = StampFontStyle.SEVEN_SEGMENT),
            optionalFrame = FrameBorderStyle.FILM_35MM,
            soundAssetName = "shutter_disposable",
            isSpecialSeries = true,
            defaultSkin = SkinCatalog.quikYellowFilm,
            linkedSkins = defaultFilmSkins + SkinCatalog.quikYellowFilm.skinId
        )

    // 41. DISPO K (Special - Warm Disposable)
    val dispoK =
        profile(
            id = "dispo_k",
            codeName = "DISPO K",
            subtitle = "Warm Golden Disposable",
            sampleDescription = "Warm golden disposable highlights with crisp analog grain and flash pop.",
            category = CameraCategory.FILM,
            era = CameraEra.DISPOSABLE_90S,
            eraYearOffset = 1999,
            viewfinderMaskType = ViewfinderMaskType.DISPOSABLE_PEEPHOLE,
            defaultAspect = AspectRatioPreset.RATIO_3_2,
            bloom = BloomSpec(threshold = 0.80f, intensity = 0.36f, tintHex = "#FFEED0"),
            grain = GrainSpec(type = GrainType.FILM_ORGANIC, lumaStrength = 0.24f, chromaStrength = 0.12f),
            optics = OpticsSpec(caStrength = 0.22f, vignetteStrength = 0.35f),
            grunge = GrungeSpec(lightLeakStrength = 0.20f, dustStrength = 0.12f),
            defaultRecipeValues = DefaultRecipeValues(warmthShift = 0.14f),
            stamp = StampDefaults(style = StampStyle.QUARTZ_ORANGE, fontStyle = StampFontStyle.SEVEN_SEGMENT),
            optionalFrame = FrameBorderStyle.FILM_35MM,
            soundAssetName = "shutter_disposable",
            isSpecialSeries = true,
            defaultSkin = SkinCatalog.quikYellowFilm,
            linkedSkins = defaultFilmSkins + SkinCatalog.quikYellowFilm.skinId
        )

    // 42. V2014 (Special - 2014 Vintage)
    val v2014 =
        profile(
            id = "v2014",
            codeName = "V2014",
            subtitle = "2014 Nostalgic Rose-Fade",
            sampleDescription = "Faded matte blacks and warm-rose midtones evoking 2014 mobile photography.",
            category = CameraCategory.SPECIAL,
            era = CameraEra.CCD_2000S,
            eraYearOffset = 2014,
            bloom = BloomSpec(threshold = 0.78f, intensity = 0.38f, tintHex = "#FFF0F2"),
            grain = GrainSpec(type = GrainType.FILM_ORGANIC, lumaStrength = 0.14f, chromaStrength = 0.08f),
            optics = OpticsSpec(caStrength = 0.10f, vignetteStrength = 0.24f),
            defaultRecipeValues = DefaultRecipeValues(shadows = 0.14f, warmthShift = 0.10f),
            stamp = StampDefaults(style = StampStyle.CLEAN_WHITE, defaultEnabled = false),
            soundAssetName = "shutter_ccd",
            isSpecialSeries = true,
            defaultSkin = SkinCatalog.vintageCream,
            linkedSkins = defaultCcdSkins
        )

    // 43. BOOTH (Special - 4-Strip Photobooth)
    val booth =
        profile(
            id = "booth",
            codeName = "BOOTH",
            subtitle = "Retro 4-Strip Photobooth",
            sampleDescription = "High-contrast booth flash grade tailored for classic 4-panel vertical strips.",
            category = CameraCategory.SPECIAL,
            era = CameraEra.INSTANT,
            eraYearOffset = 1995,
            viewfinderMaskType = ViewfinderMaskType.PHOTO_BOOTH,
            defaultAspect = AspectRatioPreset.RATIO_4_3,
            bloom = BloomSpec(threshold = 0.80f, intensity = 0.32f, tintHex = "#FFF5E8"),
            grain = GrainSpec(type = GrainType.FILM_ORGANIC, lumaStrength = 0.18f, chromaStrength = 0.08f),
            optics = OpticsSpec(caStrength = 0.12f, vignetteStrength = 0.28f),
            stamp = StampDefaults(style = StampStyle.CLEAN_WHITE, defaultEnabled = false),
            defaultFrame = FrameBorderStyle.PHOTO_BOOTH_4STRIP,
            optionalFrame = FrameBorderStyle.NONE,
            soundAssetName = "shutter_instant",
            isSpecialSeries = true,
            defaultSkin = SkinCatalog.boothStrip,
            linkedSkins = listOf(SkinCatalog.boothStrip.skinId) + defaultInstantSkins
        )

    // 44. INST (Special - Instant Mini)
    val inst =
        profile(
            id = "inst",
            codeName = "INST",
            subtitle = "Instant Mini Pastel",
            sampleDescription = "Soft cyan shadows, creamy whites, and classic mini instant border.",
            category = CameraCategory.INSTANT,
            era = CameraEra.INSTANT,
            eraYearOffset = 2003,
            viewfinderMaskType = ViewfinderMaskType.INSTANT_SQUARE,
            defaultAspect = AspectRatioPreset.RATIO_4_3,
            defaultResolution = ResolutionPreset.CCD_3_2MP,
            bloom = BloomSpec(threshold = 0.77f, intensity = 0.44f, tintHex = "#FFF7F0"),
            grain = GrainSpec(type = GrainType.FILM_ORGANIC, lumaStrength = 0.15f, chromaStrength = 0.10f),
            optics = OpticsSpec(caStrength = 0.15f, vignetteStrength = 0.26f),
            grunge = GrungeSpec(lightLeakStrength = 0.08f, dustStrength = 0.04f),
            stamp = StampDefaults(style = StampStyle.CLEAN_WHITE, fontStyle = StampFontStyle.CLEAN_SANS),
            defaultFrame = FrameBorderStyle.INSTANT_PRINT,
            optionalFrame = FrameBorderStyle.NONE,
            soundAssetName = "shutter_instant",
            isSpecialSeries = true,
            defaultSkin = SkinCatalog.instPastelMint,
            linkedSkins = defaultInstantSkins
        )

    // 45. INST W (Special - Instant Wide)
    val instW =
        profile(
            id = "inst_w",
            codeName = "INST W",
            subtitle = "Instant Wide Print",
            sampleDescription = "Wide-frame instant emulsion with gentle fade and warm cream highlights.",
            category = CameraCategory.INSTANT,
            era = CameraEra.INSTANT,
            eraYearOffset = 2005,
            viewfinderMaskType = ViewfinderMaskType.INSTANT_WIDE,
            defaultAspect = AspectRatioPreset.RATIO_3_2,
            bloom = BloomSpec(threshold = 0.78f, intensity = 0.42f, tintHex = "#FFF7F0"),
            grain = GrainSpec(type = GrainType.FILM_ORGANIC, lumaStrength = 0.15f, chromaStrength = 0.09f),
            optics = OpticsSpec(caStrength = 0.14f, vignetteStrength = 0.24f),
            stamp = StampDefaults(style = StampStyle.CLEAN_WHITE, fontStyle = StampFontStyle.CLEAN_SANS),
            defaultFrame = FrameBorderStyle.INSTANT_WIDE,
            optionalFrame = FrameBorderStyle.NONE,
            soundAssetName = "shutter_instant",
            isSpecialSeries = true,
            defaultSkin = SkinCatalog.instBlush,
            linkedSkins = defaultInstantSkins
        )

    // 46. XS10 (Special - Handheld Console)
    val xs10 =
        profile(
            id = "xs10",
            codeName = "XS10",
            subtitle = "Retro Handheld Console Cam",
            sampleDescription = "Lo-bit crisp handheld contrast with cool-green cast and nostalgic pixel HUD.",
            category = CameraCategory.SPECIAL,
            era = CameraEra.CCD_2000S,
            eraYearOffset = 1999,
            defaultResolution = ResolutionPreset.CCD_3_2MP,
            bloom = BloomSpec(threshold = 0.80f, intensity = 0.30f, tintHex = "#E8FFE8"),
            grain = GrainSpec(type = GrainType.CCD_CHROMA_LUMA, lumaStrength = 0.24f, chromaStrength = 0.12f),
            optics = OpticsSpec(caStrength = 0.16f, vignetteStrength = 0.24f),
            defaultRecipeValues = DefaultRecipeValues(sharpen = 0.35f),
            stamp = StampDefaults(style = StampStyle.SEVEN_SEGMENT_EMERALD, fontStyle = StampFontStyle.PIXEL),
            soundAssetName = "shutter_ccd",
            isSpecialSeries = true,
            defaultSkin = SkinCatalog.gameConsole,
            linkedSkins = listOf(SkinCatalog.gameConsole.skinId) + defaultCcdSkins
        )

    // 47. Y2K (Special - Cyber Y2K)
    val y2k =
        profile(
            id = "y2k",
            codeName = "Y2K",
            subtitle = "Cyber Y2K Metallic CCD",
            sampleDescription = "Cool blue-violet shadows, glossy specular bloom, and early-millennium shimmer.",
            category = CameraCategory.SPECIAL,
            era = CameraEra.CCD_2000S,
            eraYearOffset = 2000,
            bloom = BloomSpec(threshold = 0.74f, intensity = 0.52f, tintHex = "#E8F8FF"),
            grain = GrainSpec(type = GrainType.CCD_CHROMA_LUMA, lumaStrength = 0.14f, chromaStrength = 0.22f),
            optics = OpticsSpec(caStrength = 0.20f, vignetteStrength = 0.22f),
            defaultRecipeValues = DefaultRecipeValues(warmthShift = -0.14f),
            stamp = StampDefaults(style = StampStyle.CLEAN_WHITE, fontStyle = StampFontStyle.PIXEL),
            soundAssetName = "shutter_ccd",
            isSpecialSeries = true,
            defaultSkin = SkinCatalog.y2kCyan,
            linkedSkins = defaultCcdSkins
        )

    // 48. ACTION (Special - Wide Action Cam)
    val action =
        profile(
            id = "action",
            codeName = "ACTION",
            subtitle = "Wide Outdoor Action Cam",
            sampleDescription = "Vivid skies, punchy micro-contrast, and ultra-wide fisheye outdoor spirit.",
            category = CameraCategory.SPECIAL,
            era = CameraEra.CAMCORDER_DV,
            eraYearOffset = 2012,
            viewfinderMaskType = ViewfinderMaskType.CAMCORDER_DV,
            defaultAspect = AspectRatioPreset.RATIO_16_9,
            bloom = BloomSpec(threshold = 0.82f, intensity = 0.32f, tintHex = "#EDF7FF"),
            grain = GrainSpec(type = GrainType.CCD_CHROMA_LUMA, lumaStrength = 0.12f, chromaStrength = 0.14f),
            optics = OpticsSpec(caStrength = 0.22f, vignetteStrength = 0.26f, fisheyeDefault = 0.35f),
            defaultRecipeValues = DefaultRecipeValues(
                sharpen = 0.25f,
                lensAttachment = LensAttachment.FISHEYE
            ),
            stamp = StampDefaults(style = StampStyle.CLEAN_WHITE, fontStyle = StampFontStyle.VCR_OSD),
            soundAssetName = "shutter_ccd",
            supportsVideo = true,
            isSpecialSeries = true,
            defaultSkin = SkinCatalog.matteObsidian,
            linkedSkins = defaultCamcorderSkins + SkinCatalog.matteObsidian.skinId
        )

    // 49. NX (Special - SLR Matte Film)
    val nx =
        profile(
            id = "nx",
            codeName = "NX",
            subtitle = "SLR NX Matte Film",
            sampleDescription = "Deep rich SLR blacks, refined skin tones, and crisp prism finder optics.",
            category = CameraCategory.FILM,
            era = CameraEra.CLASSIC_35MM,
            eraYearOffset = 1992,
            viewfinderMaskType = ViewfinderMaskType.SLR_OPTICAL,
            defaultAspect = AspectRatioPreset.RATIO_3_2,
            defaultResolution = ResolutionPreset.VINTAGE_8MP,
            bloom = BloomSpec(threshold = 0.83f, intensity = 0.28f, tintHex = "#FFF8EE"),
            grain = GrainSpec(type = GrainType.FILM_ORGANIC, lumaStrength = 0.17f, chromaStrength = 0.08f),
            optics = OpticsSpec(caStrength = 0.10f, vignetteStrength = 0.22f),
            stamp = StampDefaults(style = StampStyle.CLASSIC_RED, fontStyle = StampFontStyle.SEVEN_SEGMENT),
            optionalFrame = FrameBorderStyle.FILM_35MM,
            soundAssetName = "shutter_rangefinder",
            isSpecialSeries = true,
            defaultSkin = SkinCatalog.slrNx,
            linkedSkins = listOf(SkinCatalog.slrNx.skinId) + defaultFilmSkins
        )

    // 50. HDC (Special - HD Camcorder)
    val hdc =
        profile(
            id = "hdc",
            codeName = "HDC",
            subtitle = "HD Digital Camcorder",
            sampleDescription = "Crisp electronic HD camcorder contrast with cool-neutral daylight balance.",
            category = CameraCategory.CAMCORDER,
            era = CameraEra.CAMCORDER_DV,
            eraYearOffset = 2009,
            viewfinderMaskType = ViewfinderMaskType.CAMCORDER_DV,
            defaultAspect = AspectRatioPreset.RATIO_16_9,
            bloom = BloomSpec(threshold = 0.80f, intensity = 0.36f, tintHex = "#EEF7FF"),
            grain = GrainSpec(type = GrainType.CCD_CHROMA_LUMA, lumaStrength = 0.12f, chromaStrength = 0.16f),
            optics = OpticsSpec(caStrength = 0.14f, vignetteStrength = 0.18f),
            stamp = StampDefaults(style = StampStyle.CLEAN_WHITE, fontStyle = StampFontStyle.VCR_OSD),
            defaultFrame = FrameBorderStyle.DV_TIMECODE,
            optionalFrame = FrameBorderStyle.NONE,
            soundAssetName = "shutter_ccd",
            supportsVideo = true,
            isSpecialSeries = true,
            defaultSkin = SkinCatalog.dvCobaltMetallic,
            linkedSkins = defaultCamcorderSkins
        )

    // 51. M10 (Special - Classic Compact)
    val m10 =
        profile(
            id = "m10",
            codeName = "M10",
            subtitle = "2003 Warm Indoor CCD",
            sampleDescription = "Warm golden indoor skin tones and gentle highlight roll-off.",
            category = CameraCategory.CCD,
            era = CameraEra.CCD_2000S,
            eraYearOffset = 2003,
            bloom = BloomSpec(threshold = 0.79f, intensity = 0.40f, tintHex = "#FFF3DF"),
            grain = GrainSpec(type = GrainType.CCD_CHROMA_LUMA, lumaStrength = 0.15f, chromaStrength = 0.21f),
            optics = OpticsSpec(caStrength = 0.16f, vignetteStrength = 0.23f),
            defaultRecipeValues = DefaultRecipeValues(warmthShift = 0.10f),
            stamp = StampDefaults(style = StampStyle.DOT_MATRIX_AMBER, fontStyle = StampFontStyle.DOT_MATRIX),
            soundAssetName = "shutter_ccd",
            isSpecialSeries = true,
            defaultSkin = SkinCatalog.titaniumChampagne,
            linkedSkins = defaultCcdSkins
        )

    // 52..83 Classic ProCCD Lineup (aligned with luts_2d_meta.json & skin_manifest.json)
    private fun classicCcd(
        id: String,
        codeName: String,
        subtitle: String,
        description: String,
        eraYear: Int,
        defaultSkin: ChassisSkinDefinition,
        warmth: Float = 0f,
        sharpen: Float = 0.15f,
        beauty: Float = 0f,
        bloomIntensity: Float = 0.36f,
        defaultAspect: AspectRatioPreset = AspectRatioPreset.RATIO_4_3,
        defaultRes: ResolutionPreset = ResolutionPreset.DIGICAM_5MP
    ): CameraProfile = profile(
        id = id,
        codeName = codeName,
        subtitle = subtitle,
        sampleDescription = description,
        category = CameraCategory.CCD,
        era = CameraEra.CCD_2000S,
        eraYearOffset = eraYear,
        defaultAspect = defaultAspect,
        defaultResolution = defaultRes,
        bloom = BloomSpec(threshold = 0.80f, intensity = bloomIntensity, tintHex = "#FFF4E6"),
        grain = GrainSpec(type = GrainType.CCD_CHROMA_LUMA, lumaStrength = 0.14f, chromaStrength = 0.19f),
        optics = OpticsSpec(caStrength = 0.15f, vignetteStrength = 0.22f),
        defaultRecipeValues = DefaultRecipeValues(
            warmthShift = warmth,
            sharpen = sharpen,
            beautySoftness = beauty
        ),
        stamp = StampDefaults(style = StampStyle.DOT_MATRIX_AMBER, fontStyle = StampFontStyle.DOT_MATRIX),
        soundAssetName = "shutter_ccd",
        defaultSkin = defaultSkin,
        linkedSkins = defaultCcdSkins
    )

    private fun classicFilm(
        id: String,
        codeName: String,
        subtitle: String,
        description: String,
        eraYear: Int,
        defaultSkin: ChassisSkinDefinition,
        warmth: Float = 0.08f,
        vignette: Float = 0.26f,
        stampEnabled: Boolean = true
    ): CameraProfile = profile(
        id = id,
        codeName = codeName,
        subtitle = subtitle,
        sampleDescription = description,
        category = CameraCategory.FILM,
        era = CameraEra.CLASSIC_35MM,
        eraYearOffset = eraYear,
        viewfinderMaskType = ViewfinderMaskType.RANGEFINDER,
        defaultAspect = AspectRatioPreset.RATIO_3_2,
        defaultResolution = ResolutionPreset.VINTAGE_8MP,
        bloom = BloomSpec(threshold = 0.83f, intensity = 0.30f, tintHex = "#FFF7E6"),
        grain = GrainSpec(type = GrainType.FILM_ORGANIC, lumaStrength = 0.20f, chromaStrength = 0.09f),
        optics = OpticsSpec(caStrength = 0.12f, vignetteStrength = vignette),
        grunge = GrungeSpec(lightLeakStrength = 0.06f, dustStrength = 0.08f),
        defaultRecipeValues = DefaultRecipeValues(warmthShift = warmth),
        stamp = StampDefaults(
            style = StampStyle.CLASSIC_RED,
            defaultEnabled = stampEnabled,
            fontStyle = StampFontStyle.SEVEN_SEGMENT
        ),
        optionalFrame = FrameBorderStyle.FILM_35MM,
        soundAssetName = "shutter_rangefinder",
        defaultSkin = defaultSkin,
        linkedSkins = defaultFilmSkins
    )

    val a2300 = classicCcd(
        id = "a2300",
        codeName = "A2300",
        subtitle = "2012 Slim Pocket CCD",
        description = "Slim 2012 pocket CCD with crisp vibrant colors and clean daylight whites.",
        eraYear = 2012,
        defaultSkin = SkinCatalog.charcoalMetal,
        warmth = -0.04f
    )

    val a40 = classicCcd(
        id = "a40",
        codeName = "A40",
        subtitle = "2002 2MP Chunky CCD",
        description = "2002 2MP chunky CCD with nostalgic warm-green cast and soft highlights.",
        eraYear = 2002,
        defaultSkin = SkinCatalog.silverBrushed,
        warmth = 0.06f,
        defaultRes = ResolutionPreset.CCD_3_2MP
    )

    val a620 = classicCcd(
        id = "a620",
        codeName = "A620",
        subtitle = "2005 Swivel Power-CCD",
        description = "Swivel-screen 2005 Power-CCD with rich blues and warm midtones.",
        eraYear = 2005,
        defaultSkin = SkinCatalog.silverBrushed,
        warmth = 0.06f
    )

    val blue =
        profile(
            id = "blue",
            codeName = "BLUE",
            subtitle = "Cyanotype Monochrome Film",
            sampleDescription = "Monochromatic cyanotype-blue atmospheric film grade.",
            category = CameraCategory.SPECIAL,
            era = CameraEra.CLASSIC_35MM,
            eraYearOffset = 1995,
            bloom = BloomSpec(threshold = 0.80f, intensity = 0.34f, tintHex = "#D8EEFF"),
            grain = GrainSpec(type = GrainType.FILM_ORGANIC, lumaStrength = 0.22f, chromaStrength = 0.06f),
            optics = OpticsSpec(caStrength = 0.14f, vignetteStrength = 0.28f),
            defaultRecipeValues = DefaultRecipeValues(warmthShift = -0.25f),
            stamp = StampDefaults(style = StampStyle.CLEAN_WHITE, fontStyle = StampFontStyle.CLEAN_SANS),
            soundAssetName = "shutter_rangefinder",
            defaultSkin = SkinCatalog.dvCobaltMetallic,
            linkedSkins = defaultFilmSkins + SkinCatalog.dvCobaltMetallic.skinId
        )

    val gold = classicFilm(
        id = "gold",
        codeName = "GOLD",
        subtitle = "200-Speed Golden Film",
        description = "Classic 200-speed golden daylight film with warm amber highlights.",
        eraYear = 1997,
        defaultSkin = SkinCatalog.rangefinderBrass,
        warmth = 0.18f
    )

    val gr = classicFilm(
        id = "gr",
        codeName = "GR",
        subtitle = "High-Contrast Street B&W",
        description = "High-contrast B&W street positive with deep crushed blacks and crisp silver mids.",
        eraYear = 2001,
        defaultSkin = SkinCatalog.matteObsidian,
        warmth = 0f,
        vignette = 0.34f,
        stampEnabled = false
    )

    val c123 = classicCcd(
        id = "c123",
        codeName = "C123",
        subtitle = "2004 Compact Pocket CCD",
        description = "Entry-level 2004 CCD with slightly cool shadows and punchy reds.",
        eraYear = 2004,
        defaultSkin = SkinCatalog.silverBrushed,
        warmth = 0.04f,
        defaultRes = ResolutionPreset.CCD_3_2MP
    )

    val cx733 = classicCcd(
        id = "cx733",
        codeName = "CX733",
        subtitle = "2004 Warm Sunny CCD",
        description = "Easy-share warm sunny CCD with saturated yellows and crisp blues.",
        eraYear = 2004,
        defaultSkin = SkinCatalog.silverBrushed,
        warmth = 0.14f,
        defaultRes = ResolutionPreset.CCD_3_2MP
    )

    val dc210 = classicCcd(
        id = "dc210",
        codeName = "DC210",
        subtitle = "1997 Early Digital CCD",
        description = "1997 early digital CCD with lo-fi warm-amber cast and lifted shadows.",
        eraYear = 1997,
        defaultSkin = SkinCatalog.grungeSlate,
        warmth = 0.16f,
        bloomIntensity = 0.44f,
        defaultRes = ResolutionPreset.CCD_3_2MP
    )

    val f410 = classicCcd(
        id = "f410",
        codeName = "F410",
        subtitle = "2003 Pastel SuperCCD",
        description = "SuperCCD-inspired pastel portrait grade with luminous skin tones.",
        eraYear = 2003,
        defaultSkin = SkinCatalog.silverBrushed,
        warmth = 0.08f,
        beauty = 0.18f
    )

    val f71 = classicCcd(
        id = "f71",
        codeName = "F71",
        subtitle = "2008 Travel Compact CCD",
        description = "Clean travel-compact CCD with crisp greens and neutral-cool whites.",
        eraYear = 2008,
        defaultSkin = SkinCatalog.charcoalMetal,
        warmth = -0.06f
    )

    val fe402 = classicCcd(
        id = "fe402",
        codeName = "FE402",
        subtitle = "2010 Silver-Highlight CCD",
        description = "Slim metallic pocket CCD with cool-silver highlights and gentle contrast.",
        eraYear = 2010,
        defaultSkin = SkinCatalog.silverBrushed,
        warmth = -0.10f
    )

    val h10 = classicCcd(
        id = "h10",
        codeName = "H10",
        subtitle = "2008 Travel Zoom CCD",
        description = "Compact travel-zoom CCD with rich foliage and clear sky contrast.",
        eraYear = 2008,
        defaultSkin = SkinCatalog.charcoalMetal,
        warmth = 0.02f
    )

    val i10 = classicCcd(
        id = "i10",
        codeName = "I10",
        subtitle = "2006 Ultra-Slim Flash CCD",
        description = "Ultra-slim 2006 CCD with cool indoor flash balance and punchy blacks.",
        eraYear = 2006,
        defaultSkin = SkinCatalog.y2kLavender,
        warmth = -0.10f
    )

    val ixus70 = classicCcd(
        id = "ixus70",
        codeName = "IXUS70",
        subtitle = "2007 Flat-Metal CCD",
        description = "Iconic 2007 flat-metal compact CCD with balanced warm skin and crisp blues.",
        eraYear = 2007,
        defaultSkin = SkinCatalog.silverBrushed,
        warmth = 0.08f
    )

    val lx5 = classicCcd(
        id = "lx5",
        codeName = "LX5",
        subtitle = "2010 Enthusiast Compact",
        description = "Enthusiast wide-aperture compact with rich micro-contrast and clean blacks.",
        eraYear = 2010,
        defaultSkin = SkinCatalog.matteObsidian,
        warmth = 0.04f,
        sharpen = 0.24f,
        defaultRes = ResolutionPreset.VINTAGE_8MP
    )

    val mg330 =
        profile(
            id = "mg330",
            codeName = "MG330",
            subtitle = "2008 HDD Camcorder",
            sampleDescription = "Hard-drive camcorder with punchy video gamma and cool-magenta shadows.",
            category = CameraCategory.CAMCORDER,
            era = CameraEra.CAMCORDER_DV,
            eraYearOffset = 2008,
            viewfinderMaskType = ViewfinderMaskType.CAMCORDER_DV,
            defaultAspect = AspectRatioPreset.RATIO_16_9,
            bloom = BloomSpec(threshold = 0.77f, intensity = 0.44f, tintHex = "#EDF5FF"),
            grain = GrainSpec(type = GrainType.CCD_CHROMA_LUMA, lumaStrength = 0.16f, chromaStrength = 0.22f),
            optics = OpticsSpec(caStrength = 0.18f, vignetteStrength = 0.22f),
            stamp = StampDefaults(style = StampStyle.CLEAN_WHITE, fontStyle = StampFontStyle.VCR_OSD),
            defaultFrame = FrameBorderStyle.DV_TIMECODE,
            optionalFrame = FrameBorderStyle.NONE,
            soundAssetName = "shutter_ccd",
            supportsVideo = true,
            defaultSkin = SkinCatalog.vhsGraphite,
            linkedSkins = defaultCamcorderSkins
        )

    val n23 = classicCcd(
        id = "n23",
        codeName = "N23",
        subtitle = "2005 Warm Neutral CCD",
        description = "Compact 2000s CCD with neutral-warm balance and soft highlight roll-off.",
        eraYear = 2005,
        defaultSkin = SkinCatalog.vintageCream,
        warmth = 0.08f
    )

    val original = classicCcd(
        id = "original",
        codeName = "ORIGINAL",
        subtitle = "Clean Neutral CCD Baseline",
        description = "Clean neutral sensor pass-through with minimal calibrated contrast.",
        eraYear = 2006,
        defaultSkin = SkinCatalog.studioMonochrome,
        warmth = 0f,
        sharpen = 0.05f,
        bloomIntensity = 0.18f
    )

    val original2 = classicCcd(
        id = "original2",
        codeName = "ORIGINAL2",
        subtitle = "Clean Daylight Baseline",
        description = "Clean neutral daylight pass-through with subtle highlight clarity.",
        eraYear = 2008,
        defaultSkin = SkinCatalog.studioMonochrome,
        warmth = 0.02f,
        sharpen = 0.08f,
        bloomIntensity = 0.20f
    )

    val originalR = classicFilm(
        id = "original_r",
        codeName = "ORIGINAL R",
        subtitle = "Natural Film Curve Baseline",
        description = "Clean neutral film-curve pass-through with gentle shadow warmth.",
        eraYear = 1998,
        defaultSkin = SkinCatalog.studioMonochrome,
        warmth = 0.04f,
        vignette = 0.18f
    )

    val rfc = classicFilm(
        id = "rfc",
        codeName = "RFC",
        subtitle = "Classic Rangefinder Color",
        description = "Classic rangefinder color film with emerald-slate shadows and warm highlights.",
        eraYear = 1986,
        defaultSkin = SkinCatalog.silverBrushed,
        warmth = 0.10f
    )

    val sipic = classicCcd(
        id = "sipic",
        codeName = "SIPIC",
        subtitle = "2006 Bright Pop CCD",
        description = "Pocket novelty CCD with bright saturated primaries and crisp contrast.",
        eraYear = 2006,
        defaultSkin = SkinCatalog.instBlush,
        warmth = 0.08f
    )

    val sl400 = classicCcd(
        id = "sl400",
        codeName = "SL400",
        subtitle = "2003 Ultra-Slim Silver CCD",
        description = "2003 ultra-slim CCD with cool-silver tone and crisp daylight contrast.",
        eraYear = 2003,
        defaultSkin = SkinCatalog.silverBrushed,
        warmth = -0.10f
    )

    val t90 = classicCcd(
        id = "t90",
        codeName = "T90",
        subtitle = "2009 Touch-Era Slim CCD",
        description = "Touch-era slim CCD with vibrant saturated colors and glossy contrast.",
        eraYear = 2009,
        defaultSkin = SkinCatalog.charcoalMetal,
        warmth = -0.04f
    )

    val u400 = classicCcd(
        id = "u400",
        codeName = "U400",
        subtitle = "2004 All-Weather Compact",
        description = "All-weather silver compact CCD with cool oceanic blues and clean whites.",
        eraYear = 2004,
        defaultSkin = SkinCatalog.silverBrushed,
        warmth = -0.14f
    )

    val u7040 = classicCcd(
        id = "u7040",
        codeName = "U7040",
        subtitle = "2010 Vivid Foliage CCD",
        description = "2010 compact CCD with bright vivid foliage and warm-neutral skin.",
        eraYear = 2010,
        defaultSkin = SkinCatalog.y2kCyan,
        warmth = 0.06f
    )

    val wg1 = classicCcd(
        id = "wg1",
        codeName = "WG1",
        subtitle = "2011 Rugged Outdoor CCD",
        description = "Rugged outdoor CCD with punchy adventure contrast and deep blues.",
        eraYear = 2011,
        defaultSkin = SkinCatalog.leatherOlive,
        warmth = -0.05f,
        sharpen = 0.22f
    )

    val xg = classicFilm(
        id = "xg",
        codeName = "XG",
        subtitle = "1978 Japanese SLR Film",
        description = "Classic 1970s Japanese SLR film with warm amber mids and rich blacks.",
        eraYear = 1978,
        defaultSkin = SkinCatalog.charcoalMetal,
        warmth = 0.14f
    )

    val xs3 = classicCcd(
        id = "xs3",
        codeName = "XS3",
        subtitle = "2005 Minimalist Pocket CCD",
        description = "Minimalist pocket CCD with clean neutral contrast and cool shadows.",
        eraYear = 2005,
        defaultSkin = SkinCatalog.studioMonochrome,
        warmth = -0.08f
    )

    val z50 = classicCcd(
        id = "z50",
        codeName = "Z50",
        subtitle = "2006 Golden-Era Slim CCD",
        description = "Warm golden-era CCD with flattering skin tones and soft contrast.",
        eraYear = 2006,
        defaultSkin = SkinCatalog.leatherCognac,
        warmth = 0.12f
    )

    val zr1500 = classicCcd(
        id = "zr1500",
        codeName = "ZR1500",
        subtitle = "2014 Portrait Luminous CCD",
        description = "Selfie-era compact CCD with bright airy skin luminance and pastel highlights.",
        eraYear = 2014,
        defaultSkin = SkinCatalog.vintageCream,
        warmth = 0.08f,
        beauty = 0.35f
    )

    // Legacy property aliases preserved for smooth multi-module migration.
    @Deprecated("Use CameraCatalog.z30", replaceWith = ReplaceWith("CameraCatalog.z30"))
    val dc04SilverPulse: CameraProfile
        get() = z30

    @Deprecated("Use CameraCatalog.m532", replaceWith = ReplaceWith("CameraCatalog.m532"))
    val nx07Nocturne: CameraProfile
        get() = m532

    @Deprecated("Use CameraCatalog.quik", replaceWith = ReplaceWith("CameraCatalog.quik"))
    val d98Disposable: CameraProfile
        get() = quik

    @Deprecated("Use CameraCatalog.lomo", replaceWith = ReplaceWith("CameraCatalog.lomo"))
    val lomo94Grunge: CameraProfile
        get() = lomo

    @Deprecated("Use CameraCatalog.m7", replaceWith = ReplaceWith("CameraCatalog.m7"))
    val rf77Classic: CameraProfile
        get() = m7

    @Deprecated("Use CameraCatalog.instm", replaceWith = ReplaceWith("CameraCatalog.instm"))
    val instMInstant: CameraProfile
        get() = instm

    const val CAROUSEL_BADGE_ICON_ASSET: String = "skins/cam_icon_carousel.png"

    val proCcd55Ids: Set<String> =
        setOf(
            "sx70", "2000s", "big_shot", "dispo", "dispo_k", "v2014", "booth", "inst", "inst_w",
            "xs10", "y2k", "action", "nx", "hdc", "m10",
            "a2300", "a40", "a620", "blue", "gold", "gr", "c123", "cx733", "dc210", "dcr",
            "f100", "f410", "f71", "fe402", "grf", "h10", "i10", "ixus210", "ixus70", "ixus95",
            "lx5", "m532", "mg330", "n23", "original", "original2", "original_r", "rfc", "sipic",
            "sl400", "t90", "u300", "u400", "u7040", "wg1", "xg", "xs3", "z30", "z50", "zr1500"
        )

    val all: List<CameraProfile> =
        listOf(
            z30,
            ixus95,
            m532,
            u300,
            ixus210,
            dc200,
            grf,
            dcr,
            v80,
            ccd801,
            m20,
            m550,
            t10,
            instm,
            instl,
            fxnr,
            quik,
            d3d,
            n800,
            f100,
            s20,
            lomo,
            a35,
            x20,
            polar,
            dv,
            g7x,
            mini99,
            f505,
            f50,
            c40,
            s600,
            nt16,
            h20,
            h70,
            m7,
            sx70,
            bloom2000s,
            bigShot,
            dispo,
            dispoK,
            v2014,
            booth,
            inst,
            instW,
            xs10,
            y2k,
            action,
            nx,
            hdc,
            m10,
            a2300,
            a40,
            a620,
            blue,
            gold,
            gr,
            c123,
            cx733,
            dc210,
            f410,
            f71,
            fe402,
            h10,
            i10,
            ixus70,
            lx5,
            mg330,
            n23,
            original,
            original2,
            originalR,
            rfc,
            sipic,
            sl400,
            t90,
            u400,
            u7040,
            wg1,
            xg,
            xs3,
            z50,
            zr1500
        )

    val defaultCamera: CameraProfile = z30

    val legacyIdAliases: Map<String, String> =
        mapOf(
            "dc_04_silver_pulse" to z30.id,
            "nx_07_nocturne" to m532.id,
            "d_98_disposable" to quik.id,
            "lomo_94_grunge" to lomo.id,
            "rf_77_classic" to m7.id,
            "inst_m_instant" to instm.id,
            "DC-04" to z30.id,
            "NX-07" to m532.id,
            "D-98" to quik.id,
            "LOMO-94" to lomo.id,
            "RF-77" to m7.id,
            "INST-M" to instm.id,
            "BIG_SHOT" to bigShot.id,
            "DISPO_K" to dispoK.id,
            "INST_W" to instW.id,
            "ORIGINAL_R" to originalR.id
        )

    private val byId: Map<String, CameraProfile> = all.associateBy { it.id }

    fun findById(id: String): CameraProfile? {
        byId[id]?.let { return it }
        byId[id.lowercase()]?.let { return it }
        val aliased = legacyIdAliases[id] ?: legacyIdAliases[id.uppercase()] ?: return null
        return byId[aliased]
    }

    fun findByIdOrDefault(id: String): CameraProfile = findById(id) ?: defaultCamera

    fun findByCodeName(codeName: String): CameraProfile? =
        all.firstOrNull { it.codeName.equals(codeName, ignoreCase = true) }

    fun byCategory(category: CameraCategory): List<CameraProfile> = all.filter { it.category == category }

    fun classicSeries(): List<CameraProfile> = all.filter { !it.isSpecialSeries }

    fun specialSeries(): List<CameraProfile> = all.filter { it.isSpecialSeries }

    fun buildSkinManifest(): SkinConfigManifest = SkinCatalog.toManifest(
        cameraDefaultSkins = all.associate { it.id to it.defaultSkinId },
        cameraLinkedSkins = all.associate { it.id to it.linkedSkinIds }
    )
}
