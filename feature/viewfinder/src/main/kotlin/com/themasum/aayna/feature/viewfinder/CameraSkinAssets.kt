package com.themasum.aayna.feature.viewfinder

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProduceStateScope
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class SkinProportionsSpec(
    val cameraScreenHMidLine: Float = 0.4119f,
    val skinHMidLine: Float = 0.416f,
    val cameraScreenContainerRatio: Float = 0.6558f,
    val containerWidthFraction: Float = 0.8586f,
    val viewportAspect: String = "3:4",
    val viewportHeightRatio: Float = 0.8411f,
    val viewportVerticalBias: Float = 0.1153f,
    val bezelWidthPx: Int = 900,
    val bezelHeightPx: Int = 1100,
    val bezelCutoutLeftPx: Int = 103,
    val bezelCutoutTopPx: Int = 28,
    val bezelCutoutRightPx: Int = 797,
    val bezelCutoutBottomPx: Int = 953,
    val chassisWidthPx: Int = 720,
    val chassisHeightPx: Int = 1600,
    val shutterSizePx: Int = 240,
    val camIconSizePx: Int = 160
) {
    val bezelAspectRatio: Float
        get() = bezelWidthPx.toFloat() / bezelHeightPx.coerceAtLeast(1).toFloat()

    val cutoutLeftFraction: Float
        get() = bezelCutoutLeftPx.toFloat() / bezelWidthPx.coerceAtLeast(1).toFloat()

    val cutoutTopFraction: Float
        get() = bezelCutoutTopPx.toFloat() / bezelHeightPx.coerceAtLeast(1).toFloat()

    val cutoutWidthFraction: Float
        get() = (bezelCutoutRightPx - bezelCutoutLeftPx).toFloat() / bezelWidthPx.coerceAtLeast(1).toFloat()

    val cutoutHeightFraction: Float
        get() = (bezelCutoutBottomPx - bezelCutoutTopPx).toFloat() / bezelHeightPx.coerceAtLeast(1).toFloat()
}

data class ParsedSkinAssetEntry(
    val id: String,
    val name: String,
    val material: String,
    val chassisAssetPath: String,
    val bezelAssetPath: String,
    val shutterAssetPath: String,
    val screenGlassSheenPath: String,
    val accentHex: String,
    val baseHex: String
)

data class ParsedCameraIconEntry(
    val cameraId: String,
    val iconAssetPath: String,
    val style: String,
    val defaultSkinId: String
)

data class ParsedSkinManifest(
    val version: Int = 2,
    val proportions: SkinProportionsSpec = SkinProportionsSpec(),
    val screenGlassSheenAssetPath: String = DEFAULT_GLASS_SHEEN_PATH,
    val skins: Map<String, ParsedSkinAssetEntry> = emptyMap(),
    val cameraIcons: Map<String, ParsedCameraIconEntry> = emptyMap()
) {
    fun skinForId(skinId: String): ParsedSkinAssetEntry? = skins[skinId]

    fun iconPathForCamera(cameraId: String): String =
        cameraIcons[cameraId]?.iconAssetPath ?: "skins/cam_icon_$cameraId.png"

    companion object {
        const val MANIFEST_ASSET_PATH = "skins/skin_manifest.json"
        const val DEFAULT_GLASS_SHEEN_PATH = "skins/screen_glass_sheen.png"
    }
}

object SkinManifestParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(rawJson: String): ParsedSkinManifest {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val version = root.intOrDefault("version", 2)
        val defaultSheen =
            root.stringOrDefault("screenGlassSheen", ParsedSkinManifest.DEFAULT_GLASS_SHEEN_PATH)
        val proportions = parseProportions(root["proportions"]?.jsonObject)
        val skinsMap = parseSkins(root["skins"]?.jsonObject, defaultSheen)
        val iconsMap = parseCameraIcons(root["cameraIcons"]?.jsonObject)
        return ParsedSkinManifest(
            version = version,
            proportions = proportions,
            screenGlassSheenAssetPath = defaultSheen,
            skins = skinsMap,
            cameraIcons = iconsMap
        )
    }

    private fun parseProportions(obj: JsonObject?): SkinProportionsSpec {
        if (obj == null) return SkinProportionsSpec()
        val defaults = SkinProportionsSpec()
        val bezelSize = obj["bezel_size_px"]?.jsonArrayOrNull()
        val cutout = obj["bezel_cutout_px"]?.jsonArrayOrNull()
        val chassisSize = obj["chassis_size_px"]?.jsonArrayOrNull()
        val shutterSize = obj["shutter_size_px"]?.jsonArrayOrNull()
        val iconSize = obj["cam_icon_size_px"]?.jsonArrayOrNull()

        return SkinProportionsSpec(
            cameraScreenHMidLine = obj.floatOrDefault("camera_screen_h_mid_line", defaults.cameraScreenHMidLine),
            skinHMidLine = obj.floatOrDefault("skin_h_mid_line", defaults.skinHMidLine),
            cameraScreenContainerRatio =
            obj.floatOrDefault("camera_screen_container_ratio", defaults.cameraScreenContainerRatio),
            viewportAspect = obj.stringOrDefault("viewport_aspect", defaults.viewportAspect),
            viewportHeightRatio = obj.floatOrDefault("viewport_height_ratio", defaults.viewportHeightRatio),
            bezelWidthPx = bezelSize.intAt(0, defaults.bezelWidthPx),
            bezelHeightPx = bezelSize.intAt(1, defaults.bezelHeightPx),
            bezelCutoutLeftPx = cutout.intAt(0, defaults.bezelCutoutLeftPx),
            bezelCutoutTopPx = cutout.intAt(1, defaults.bezelCutoutTopPx),
            bezelCutoutRightPx = cutout.intAt(2, defaults.bezelCutoutRightPx),
            bezelCutoutBottomPx = cutout.intAt(3, defaults.bezelCutoutBottomPx),
            chassisWidthPx = chassisSize.intAt(0, defaults.chassisWidthPx),
            chassisHeightPx = chassisSize.intAt(1, defaults.chassisHeightPx),
            shutterSizePx = shutterSize.intAt(0, defaults.shutterSizePx),
            camIconSizePx = iconSize.intAt(0, defaults.camIconSizePx)
        )
    }

    private fun parseSkins(obj: JsonObject?, defaultSheen: String): Map<String, ParsedSkinAssetEntry> {
        if (obj == null) return emptyMap()
        val result = LinkedHashMap<String, ParsedSkinAssetEntry>(obj.size)
        for ((key, value) in obj) {
            val entry = value.jsonObjectOrNull() ?: continue
            val id = entry.stringOrDefault("id", key)
            result[id] =
                ParsedSkinAssetEntry(
                    id = id,
                    name = entry.stringOrDefault("name", id),
                    material = entry.stringOrDefault("material", "brushed_metal"),
                    chassisAssetPath = entry.stringOrDefault("chassis", ""),
                    bezelAssetPath = entry.stringOrDefault("bezel", ""),
                    shutterAssetPath = entry.stringOrDefault("shutter", ""),
                    screenGlassSheenPath = entry.stringOrDefault("screenGlassSheen", defaultSheen),
                    accentHex = entry.stringOrDefault("accentHex", "#4CD4E8"),
                    baseHex = entry.stringOrDefault("baseHex", "#2A282A")
                )
        }
        return result
    }

    private fun parseCameraIcons(obj: JsonObject?): Map<String, ParsedCameraIconEntry> {
        if (obj == null) return emptyMap()
        val result = LinkedHashMap<String, ParsedCameraIconEntry>(obj.size)
        for ((cameraId, value) in obj) {
            val entry = value.jsonObjectOrNull() ?: continue
            result[cameraId] =
                ParsedCameraIconEntry(
                    cameraId = cameraId,
                    iconAssetPath = entry.stringOrDefault("icon", "skins/cam_icon_$cameraId.png"),
                    style = entry.stringOrDefault("style", "ccd_compact"),
                    defaultSkinId = entry.stringOrDefault("defaultSkin", "skin_charcoal_metal")
                )
        }
        return result
    }

    private fun JsonObject.intOrDefault(key: String, fallback: Int): Int =
        this[key]?.jsonPrimitive?.intOrNull ?: fallback

    private fun JsonObject.floatOrDefault(key: String, fallback: Float): Float =
        this[key]?.jsonPrimitive?.floatOrNull ?: fallback

    private fun JsonObject.stringOrDefault(key: String, fallback: String): String =
        this[key]?.jsonPrimitive?.content ?: fallback

    private fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject

    private fun JsonElement.jsonArrayOrNull(): JsonArray? = this as? JsonArray

    private fun JsonArray?.intAt(index: Int, fallback: Int): Int {
        val arr = this ?: return fallback
        if (index !in 0 until arr.size) return fallback
        return arr[index].jsonPrimitive.intOrNull ?: fallback
    }
}

class SkinBitmapCache(maxBytes: Int = defaultCacheSizeBytes()) {
    private val cache =
        object : LruCache<String, Bitmap>(maxBytes.coerceAtLeast(MIN_CACHE_BYTES)) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount.coerceAtLeast(1)
        }

    fun get(assetPath: String): Bitmap? {
        val bmp = cache.get(assetPath) ?: return null
        return if (bmp.isRecycled) {
            cache.remove(assetPath)
            null
        } else {
            bmp
        }
    }

    suspend fun loadBitmap(assetManager: AssetManager, assetPath: String): Bitmap? {
        if (assetPath.isBlank()) return null
        get(assetPath)?.let { return it }

        val decoded =
            withContext(Dispatchers.IO) {
                runCatching {
                    val options =
                        BitmapFactory.Options().apply {
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }
                    assetManager.open(assetPath).use { input ->
                        BitmapFactory.decodeStream(input, null, options)
                    }
                }.getOrNull()
            }
        if (decoded != null) {
            cache.put(assetPath, decoded)
        }
        return decoded
    }

    fun evictAll() {
        cache.evictAll()
    }

    companion object {
        private const val MIN_CACHE_BYTES = 4 * 1024 * 1024
        private const val MAX_CACHE_BYTES = 32 * 1024 * 1024

        fun defaultCacheSizeBytes(): Int {
            val maxHeap = Runtime.getRuntime().maxMemory().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            return (maxHeap / 12).coerceIn(MIN_CACHE_BYTES, MAX_CACHE_BYTES)
        }
    }
}

@Composable
internal fun rememberSkinManifest(assetManager: AssetManager): ParsedSkinManifest {
    val state =
        produceState(initialValue = ParsedSkinManifest(), key1 = assetManager) {
            value =
                withContext(Dispatchers.IO) {
                    runCatching {
                        assetManager.open(ParsedSkinManifest.MANIFEST_ASSET_PATH).bufferedReader().use { reader ->
                            SkinManifestParser.parse(reader.readText())
                        }
                    }.getOrElse { ParsedSkinManifest() }
                }
        }
    return state.value
}

@Composable
internal fun rememberSkinImageBitmap(
    assetPath: String?,
    cache: SkinBitmapCache,
    assetManager: AssetManager
): ImageBitmap? {
    val path = assetPath?.takeIf { it.isNotBlank() }
    val state: State<ImageBitmap?> =
        produceState(
            initialValue = path?.let { cache.get(it)?.asImageBitmap() },
            key1 = path,
            key2 = cache
        ) {
            value = loadImageBitmapValue(path, cache, assetManager)
        }
    return state.value
}

private suspend fun ProduceStateScope<ImageBitmap?>.loadImageBitmapValue(
    path: String?,
    cache: SkinBitmapCache,
    assetManager: AssetManager
): ImageBitmap? {
    if (path == null) return null
    val bmp = cache.loadBitmap(assetManager, path) ?: return null
    return bmp.asImageBitmap()
}

@Composable
internal fun rememberSkinBitmapCache(): SkinBitmapCache = remember { SkinBitmapCache() }
