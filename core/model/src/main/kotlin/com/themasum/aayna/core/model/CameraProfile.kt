package com.themasum.aayna.core.model

import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

@Serializable
enum class CameraEra {
    CCD_2000S,
    DISPOSABLE_90S,
    GRUNGE_90S,
    CLASSIC_35MM,
    INSTANT
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
data class StampDefaults(
    val style: StampStyle,
    val defaultEnabled: Boolean = true,
    val format: StampFormat = StampFormat.SHORT_YY_MM_DD
)

@Serializable
data class CameraProfile(
    val id: String,
    val codeName: String,
    val subtitle: String,
    val era: CameraEra,
    val eraYearOffset: Int,
    val lutAsset: String,
    val lutSize: Int = 32,
    val defaultAspect: AspectRatioPreset = AspectRatioPreset.RATIO_4_3,
    val defaultResolution: ResolutionPreset = ResolutionPreset.DIGICAM_5MP,
    val bloom: BloomSpec,
    val grain: GrainSpec,
    val optics: OpticsSpec,
    val grunge: GrungeSpec = GrungeSpec(),
    val stamp: StampDefaults,
    val defaultFrame: FrameBorderStyle = FrameBorderStyle.NONE,
    val optionalFrame: FrameBorderStyle = FrameBorderStyle.NONE,
    val soundAssetName: String,
    val skin: CameraSkinSpec
)

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
    val windowPx: List<Int>
)

@Serializable
data class GrungeAtlasMeta(
    val textures: Map<String, String> = emptyMap(),
    val frames: Map<String, FrameWindowMeta> = emptyMap()
)
