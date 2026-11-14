package com.themasum.aayna.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
enum class MediaType {
    STILL,
    VIDEO,
    D3D_BURST
}

@Serializable
enum class CaptureStorageMode {
    INSTANT_DIGICAM,
    NON_DESTRUCTIVE_ROLL,
    ANALOG_DEVELOPING_RITUAL
}

@Serializable
enum class FlashMode {
    OFF,
    AUTO,
    HARDWARE_ON,
    OPTICAL_FLASH_SIM
}

@Serializable
enum class WhiteBalancePreset(val label: String, val temperatureShift: Float, val tintShift: Float) {
    AUTO("Auto", 0.0f, 0.0f),
    DAYLIGHT("Daylight", 0.05f, 0.0f),
    CLOUDY("Cloudy", 0.18f, 0.02f),
    TUNGSTEN("Tungsten", -0.22f, -0.04f),
    FLUORESCENT("Fluorescent", -0.08f, 0.15f)
}

@Serializable
enum class TimerDuration(val seconds: Int) {
    OFF(0),
    THREE_SECONDS(3),
    TEN_SECONDS(10)
}

@Serializable
enum class LensAttachment {
    STANDARD,
    FISHEYE,
    STAR_4PT,
    STAR_6PT,
    SOFT_GLOW,
    PRISM,
    KALEIDOSCOPE,
    GRID_2X,
    GRID_4X
}

@Serializable
enum class FrameBorderStyle {
    NONE,
    INSTANT_PRINT,
    INSTANT_WIDE,
    POLAROID_SQUARE,
    FILM_35MM,
    DV_TIMECODE,
    CINE_16MM,
    PHOTO_BOOTH_4STRIP
}

@Serializable
enum class StampStyle(val hexColor: String) {
    DOT_MATRIX_AMBER("#FFA726"),
    SEVEN_SEGMENT_EMERALD("#39FFAE"),
    QUARTZ_ORANGE("#FF7A2F"),
    CLASSIC_RED("#FF453A"),
    CLEAN_WHITE("#F5F5F0")
}

@Serializable
enum class StampFormat {
    SHORT_YY_MM_DD,
    DOT_YYYY_MM_DD,
    EURO_DD_MM_YY,
    CUSTOM
}

@Serializable
enum class StampFontStyle {
    DOT_MATRIX,
    SEVEN_SEGMENT,
    VCR_OSD,
    PIXEL,
    CLEAN_SANS
}

@Serializable
enum class StampPosition {
    BOTTOM_RIGHT,
    BOTTOM_LEFT,
    TOP_RIGHT,
    TOP_LEFT
}

@Serializable
data class OpticalSettings(
    val evCompensation: Float = 0f,
    val iso: Int? = null,
    val shutterSpeedNs: Long? = null,
    val whiteBalance: WhiteBalancePreset = WhiteBalancePreset.AUTO,
    val warmthShift: Float = 0f,
    val bloomIntensity: Float = 0.4f,
    val beautySoftness: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val sharpen: Float = 0f,
    val lensAttachment: LensAttachment = LensAttachment.STANDARD,
    val lightLeakIntensity: Float = 0f,
    val dustIntensity: Float = 0f,
    val vignetteIntensity: Float = 0.25f,
    val flashMode: FlashMode = FlashMode.OFF,
    val aspectRatio: AspectRatioPreset = AspectRatioPreset.RATIO_4_3,
    val cropAspectRatio: AspectRatioPreset? = null,
    val straightenDegrees: Float = 0f,
    val frameStyle: FrameBorderStyle = FrameBorderStyle.NONE,
    val rotationDegrees: Int = 0,
    val grainBoost: Float = 0f,
    val cropPortraitOverride: Boolean? = null
) {
    /**
     * Returns a copy whose normalized UI slider adjustments are clamped to canonical bounds.
     */
    fun clamped(): OpticalSettings = copy(
        evCompensation = evCompensation.coerceIn(MIN_EV, MAX_EV),
        warmthShift = warmthShift.coerceIn(MIN_WARMTH_SHIFT, MAX_WARMTH_SHIFT),
        bloomIntensity = bloomIntensity.coerceIn(0f, 1f),
        beautySoftness = beautySoftness.coerceIn(0f, 1f),
        highlights = highlights.coerceIn(MIN_TONAL_SHIFT, MAX_TONAL_SHIFT),
        shadows = shadows.coerceIn(MIN_TONAL_SHIFT, MAX_TONAL_SHIFT),
        sharpen = sharpen.coerceIn(0f, 1f),
        lightLeakIntensity = lightLeakIntensity.coerceIn(0f, 1f),
        dustIntensity = dustIntensity.coerceIn(0f, 1f),
        vignetteIntensity = vignetteIntensity.coerceIn(0f, 1f),
        straightenDegrees = straightenDegrees.coerceIn(MIN_STRAIGHTEN_DEGREES, MAX_STRAIGHTEN_DEGREES),
        rotationDegrees = normalizeRotationDegrees(rotationDegrees),
        grainBoost = grainBoost.coerceIn(MIN_GRAIN_BOOST, MAX_GRAIN_BOOST)
    )

    companion object {
        const val MIN_EV = -2f
        const val MAX_EV = 2f
        const val MIN_WARMTH_SHIFT = -1f
        const val MAX_WARMTH_SHIFT = 1f
        const val MIN_TONAL_SHIFT = -1f
        const val MAX_TONAL_SHIFT = 1f
        const val MIN_STRAIGHTEN_DEGREES = -15f
        const val MAX_STRAIGHTEN_DEGREES = 15f
        const val MIN_GRAIN_BOOST = 0f
        const val MAX_GRAIN_BOOST = 1f

        fun normalizeRotationDegrees(degrees: Int): Int {
            val mod = ((degrees % 360) + 360) % 360
            return ((mod + 45) / 90 * 90) % 360
        }
    }
}

@Serializable
data class StampConfig(
    val enabled: Boolean = true,
    val style: StampStyle = StampStyle.DOT_MATRIX_AMBER,
    val fontStyle: StampFontStyle = StampFontStyle.DOT_MATRIX,
    val position: StampPosition = StampPosition.BOTTOM_RIGHT,
    val format: StampFormat = StampFormat.SHORT_YY_MM_DD,
    val useEraYearOffset: Boolean = false,
    val eraYear: Int = 2004,
    val glowStrength: Float = 0.35f,
    val customText: String = ""
)

@Serializable
data class CaptureRecipe(
    val recipeId: String,
    val cameraId: String,
    val capturedAtEpochMs: Long,
    val randomSeed: Long,
    val mediaType: MediaType = MediaType.STILL,
    val skinId: String = "",
    val resolutionPreset: ResolutionPreset = ResolutionPreset.DIGICAM_5MP,
    val opticalSettings: OpticalSettings = OpticalSettings(),
    val stampConfig: StampConfig = StampConfig(),
    val caption: String = "",
    val videoDurationMs: Long = 0L,
    val burstFrameCount: Int = 1,
    val isPreComposited: Boolean = false
)

@Serializable
data class UserSettingsSnapshot(
    val storageMode: CaptureStorageMode = CaptureStorageMode.INSTANT_DIGICAM,
    val dualSaveEnabled: Boolean = false,
    val resolutionPreset: ResolutionPreset = ResolutionPreset.DIGICAM_5MP,
    val jpegQuality: Int = DEFAULT_JPEG_QUALITY,
    val isFrontMirrorEnabled: Boolean = true,
    val useHardwareAeCompatibility: Boolean = false,
    val stampFormat: StampFormat = StampFormat.SHORT_YY_MM_DD,
    val useEraYearOffset: Boolean = false,
    val stampColor: StampStyle = StampStyle.DOT_MATRIX_AMBER,
    val stampFontStyle: StampFontStyle = StampFontStyle.DOT_MATRIX,
    val stampPosition: StampPosition = StampPosition.BOTTOM_RIGHT,
    val stampGlow: Float = 0.35f,
    val hapticsEnabled: Boolean = true,
    val sfxEnabled: Boolean = true,
    val gridEnabled: Boolean = false,
    val rollCapacity: Int = 24,
    val selectedSkinByCamera: Map<String, String> = emptyMap()
) {
    fun clampedJpegQuality(): Int = jpegQuality.coerceIn(MIN_JPEG_QUALITY, MAX_JPEG_QUALITY)

    fun skinIdForCamera(cameraId: String, defaultSkinId: String): String =
        selectedSkinByCamera[cameraId]?.takeIf { it.isNotBlank() } ?: defaultSkinId

    companion object {
        const val MIN_JPEG_QUALITY = 60
        const val MAX_JPEG_QUALITY = 100
        const val DEFAULT_JPEG_QUALITY = 92
    }
}

object AaynaJson {
    val json: Json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    fun encodeRecipe(recipe: CaptureRecipe): String = json.encodeToString(CaptureRecipe.serializer(), recipe)

    fun decodeRecipe(raw: String): CaptureRecipe = json.decodeFromString(CaptureRecipe.serializer(), raw)

    fun encodeProfile(profile: CameraProfile): String = json.encodeToString(CameraProfile.serializer(), profile)

    fun decodeProfile(raw: String): CameraProfile = json.decodeFromString(CameraProfile.serializer(), raw)

    fun encodeSkinManifest(manifest: SkinConfigManifest): String =
        json.encodeToString(SkinConfigManifest.serializer(), manifest)

    fun decodeSkinManifest(raw: String): SkinConfigManifest =
        json.decodeFromString(SkinConfigManifest.serializer(), raw)

    fun encodeUserSettings(snapshot: UserSettingsSnapshot): String =
        json.encodeToString(UserSettingsSnapshot.serializer(), snapshot)

    fun decodeUserSettings(raw: String): UserSettingsSnapshot =
        json.decodeFromString(UserSettingsSnapshot.serializer(), raw)

    fun encodeQuirkRules(rules: List<DeviceQuirkRule>): String =
        json.encodeToString(ListSerializer(DeviceQuirkRule.serializer()), rules)

    fun decodeQuirkRules(raw: String): List<DeviceQuirkRule> =
        json.decodeFromString(ListSerializer(DeviceQuirkRule.serializer()), raw)
}
