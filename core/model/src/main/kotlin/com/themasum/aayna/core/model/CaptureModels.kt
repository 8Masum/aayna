package com.themasum.aayna.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
    STAR_6PT
}

@Serializable
enum class FrameBorderStyle {
    NONE,
    INSTANT_PRINT,
    FILM_35MM
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
data class OpticalSettings(
    val evCompensation: Float = 0f,
    val iso: Int? = null,
    val shutterSpeedNs: Long? = null,
    val whiteBalance: WhiteBalancePreset = WhiteBalancePreset.AUTO,
    val warmthShift: Float = 0f,
    val bloomIntensity: Float = 0.4f,
    val lensAttachment: LensAttachment = LensAttachment.STANDARD,
    val lightLeakIntensity: Float = 0f,
    val dustIntensity: Float = 0f,
    val vignetteIntensity: Float = 0.25f,
    val flashMode: FlashMode = FlashMode.OFF,
    val aspectRatio: AspectRatioPreset = AspectRatioPreset.RATIO_4_3,
    val frameStyle: FrameBorderStyle = FrameBorderStyle.NONE
)

@Serializable
data class StampConfig(
    val enabled: Boolean = true,
    val style: StampStyle = StampStyle.DOT_MATRIX_AMBER,
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
    val resolutionPreset: ResolutionPreset = ResolutionPreset.DIGICAM_5MP,
    val opticalSettings: OpticalSettings = OpticalSettings(),
    val stampConfig: StampConfig = StampConfig(),
    val caption: String = ""
)

@Serializable
data class UserSettingsSnapshot(
    val storageMode: CaptureStorageMode = CaptureStorageMode.INSTANT_DIGICAM,
    val dualSaveEnabled: Boolean = false,
    val resolutionPreset: ResolutionPreset = ResolutionPreset.DIGICAM_5MP,
    val stampFormat: StampFormat = StampFormat.SHORT_YY_MM_DD,
    val useEraYearOffset: Boolean = false,
    val stampColor: StampStyle = StampStyle.DOT_MATRIX_AMBER,
    val stampGlow: Float = 0.35f,
    val hapticsEnabled: Boolean = true,
    val sfxEnabled: Boolean = true,
    val gridEnabled: Boolean = false,
    val rollCapacity: Int = 24
)

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
}
