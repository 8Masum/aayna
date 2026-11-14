package com.themasum.aayna.core.model

import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

@Serializable
enum class CameraCategory(val label: String) {
    CCD("CCD"),
    FILM("Film"),
    INSTANT("Instant"),
    CAMCORDER("Camcorder"),
    SPECIAL("Special")
}

@Serializable
enum class CameraEra {
    CCD_2000S,
    DISPOSABLE_90S,
    GRUNGE_90S,
    CLASSIC_35MM,
    INSTANT,
    CAMCORDER_DV,
    SPECIAL_MULTI
}

@Serializable
enum class ViewfinderMaskType {
    STANDARD_TFT,
    DISPOSABLE_PEEPHOLE,
    CAMCORDER_DV,
    VHS_TAPE,
    RANGEFINDER,
    SLR_OPTICAL,
    INSTANT_SQUARE,
    INSTANT_WIDE,
    MULTI_D3D,
    PHOTO_BOOTH
}

@Serializable
enum class AspectRatioPreset(val label: String, val widthRatio: Int, val heightRatio: Int) {
    RATIO_4_3("4:3", 4, 3),
    RATIO_3_2("3:2", 3, 2),
    RATIO_1_1("1:1", 1, 1),
    RATIO_16_9("16:9", 16, 9)
}

@Serializable
enum class ResolutionPreset(val label: String, val megapixels: Float, val maxLongEdgePx: Int) {
    CCD_3_2MP("3.2MP", 3.2f, 2048),
    DIGICAM_5MP("5MP", 5.0f, 2560),
    VINTAGE_8MP("8MP", 8.0f, 3264)
    ;

    fun computeTargetDimensions(aspect: AspectRatioPreset, glMaxTextureSize: Int): Pair<Int, Int> {
        val safeMax = minOf(maxLongEdgePx, glMaxTextureSize.coerceAtLeast(256), MAX_FBO_EDGE_PX)
        val longEdge = ((safeMax / 2) * 2).coerceAtLeast(2)
        val rawShort = (longEdge.toDouble() * aspect.heightRatio / aspect.widthRatio).roundToInt()
        val shortEdge = ((rawShort / 2) * 2).coerceIn(2, longEdge)
        return longEdge to shortEdge
    }

    companion object {
        const val MAX_FBO_EDGE_PX = 4096
    }
}

@Serializable
enum class GrainType {
    CCD_CHROMA_LUMA,
    FILM_ORGANIC
}

@Serializable
data class BloomSpec(val threshold: Float, val intensity: Float, val tintHex: String = "#FFF3E0")

@Serializable
data class GrainSpec(val type: GrainType, val lumaStrength: Float, val chromaStrength: Float)

@Serializable
data class OpticsSpec(
    val caStrength: Float,
    val vignetteStrength: Float,
    val fisheyeDefault: Float = 0f,
    val starPoints: Int = 0
)

@Serializable
data class GrungeSpec(val lightLeakStrength: Float = 0f, val dustStrength: Float = 0f)

@Serializable
data class DefaultRecipeValues(
    val evCompensation: Float = 0f,
    val warmthShift: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val sharpen: Float = 0f,
    val beautySoftness: Float = 0f,
    val lensAttachment: LensAttachment = LensAttachment.STANDARD
)

@Serializable
data class StampDefaults(
    val style: StampStyle,
    val defaultEnabled: Boolean = true,
    val format: StampFormat = StampFormat.SHORT_YY_MM_DD,
    val fontStyle: StampFontStyle = StampFontStyle.DOT_MATRIX,
    val position: StampPosition = StampPosition.BOTTOM_RIGHT
)

@Serializable
data class CameraProfile(
    val id: String,
    val codeName: String,
    val subtitle: String,
    val sampleDescription: String = "",
    val category: CameraCategory = CameraCategory.CCD,
    val era: CameraEra,
    val eraYearOffset: Int,
    val viewfinderMaskType: ViewfinderMaskType = ViewfinderMaskType.STANDARD_TFT,
    val lutAssetPath: String,
    val lutCubeAssetPath: String? = null,
    val lutSize: Int = 64,
    val defaultAspect: AspectRatioPreset = AspectRatioPreset.RATIO_4_3,
    val defaultResolution: ResolutionPreset = ResolutionPreset.DIGICAM_5MP,
    val bloom: BloomSpec,
    val grain: GrainSpec,
    val optics: OpticsSpec,
    val grunge: GrungeSpec = GrungeSpec(),
    val defaultRecipeValues: DefaultRecipeValues = DefaultRecipeValues(),
    val stamp: StampDefaults,
    val defaultFrame: FrameBorderStyle = FrameBorderStyle.NONE,
    val optionalFrame: FrameBorderStyle = FrameBorderStyle.NONE,
    val soundAssetName: String,
    val supportsVideo: Boolean = false,
    val isD3dBurst: Boolean = false,
    val isSpecialSeries: Boolean = false,
    val iconAssetPath: String = "skins/cam_icon_$id.png",
    val defaultSkinId: String = "skin_charcoal_metal",
    val linkedSkinIds: List<String> = emptyList(),
    val skin: CameraSkinSpec
) {
    /**
     * Backwards-compatible alias pointing to the primary 2D LUT PNG asset path.
     */
    val lutAsset: String
        get() = lutAssetPath

    fun buildDefaultOpticalSettings(): OpticalSettings = OpticalSettings(
        evCompensation = defaultRecipeValues.evCompensation,
        warmthShift = defaultRecipeValues.warmthShift,
        bloomIntensity = bloom.intensity,
        beautySoftness = defaultRecipeValues.beautySoftness,
        highlights = defaultRecipeValues.highlights,
        shadows = defaultRecipeValues.shadows,
        sharpen = defaultRecipeValues.sharpen,
        lensAttachment = defaultRecipeValues.lensAttachment,
        lightLeakIntensity = grunge.lightLeakStrength,
        dustIntensity = grunge.dustStrength,
        vignetteIntensity = optics.vignetteStrength,
        aspectRatio = defaultAspect,
        frameStyle = defaultFrame
    ).clamped()
}

@Serializable
data class StampAtlasMeta(
    val atlasWidth: Int,
    val atlasHeight: Int,
    val columns: Int,
    val rows: Int,
    val cellWidth: Int,
    val cellHeight: Int,
    val glyphPadding: Int,
    val chars: String,
    val atlases: Map<String, String> = emptyMap()
)

@Serializable
data class FrameWindowMeta(
    val uMin: Float,
    val vMin: Float,
    val uMax: Float,
    val vMax: Float,
    val widthPx: Int,
    val heightPx: Int,
    val windowPx: List<Int>,
    val panels: List<List<Int>> = emptyList()
)

@Serializable
data class GrungeAtlasMeta(
    val textures: Map<String, String> = emptyMap(),
    val frames: Map<String, FrameWindowMeta> = emptyMap()
)

@Serializable
data class Lut2dMetaEntry(
    val id: String,
    val assetPath: String,
    val cubeSize: Int = 64,
    val gridColumns: Int = 8,
    val gridRows: Int = 8,
    val widthPx: Int = 512,
    val heightPx: Int = 512
)

@Serializable
data class Lut2dMetaManifest(val version: Int = 1, val luts: List<Lut2dMetaEntry> = emptyList())
