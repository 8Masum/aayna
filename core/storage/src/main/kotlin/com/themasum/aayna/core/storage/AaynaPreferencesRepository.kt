package com.themasum.aayna.core.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.themasum.aayna.core.model.CameraCatalog
import com.themasum.aayna.core.model.CaptureStorageMode
import com.themasum.aayna.core.model.ResolutionPreset
import com.themasum.aayna.core.model.StampFontStyle
import com.themasum.aayna.core.model.StampFormat
import com.themasum.aayna.core.model.StampPosition
import com.themasum.aayna.core.model.StampStyle
import com.themasum.aayna.core.model.UserSettingsSnapshot
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.aaynaDataStore: DataStore<Preferences> by preferencesDataStore(name = "aayna_settings")

/**
 * Pure-Kotlin JSON codec for `Map<String, String>` so `selectedSkinByCamera` works cleanly
 * across both Android runtime and JVM unit tests without `org.json` stubs.
 */
object SkinMapJsonCodec {
    fun encode(map: Map<String, String>): String {
        val entries =
            map.entries
                .filter { (k, v) -> k.isNotBlank() && v.isNotBlank() }
                .sortedBy { it.key }
        if (entries.isEmpty()) return "{}"
        return entries.joinToString(prefix = "{", postfix = "}", separator = ",") { (k, v) ->
            "\"${escapeJson(k)}\":\"${escapeJson(v)}\""
        }
    }

    fun decode(raw: String?): Map<String, String> {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed == "{}" || !trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return emptyMap()
        }
        val result = linkedMapOf<String, String>()
        var index = 1
        val length = trimmed.length - 1
        while (index < length) {
            index = skipWhitespaceAndCommas(trimmed, index, length)
            if (index >= length) break
            if (trimmed[index] != '"') return emptyMap()
            val (key, afterKey) = readJsonString(trimmed, index, length) ?: return emptyMap()
            var cursor = skipWhitespace(trimmed, afterKey, length)
            if (cursor >= length || trimmed[cursor] != ':') return emptyMap()
            cursor = skipWhitespace(trimmed, cursor + 1, length)
            if (cursor >= length || trimmed[cursor] != '"') return emptyMap()
            val (value, afterValue) = readJsonString(trimmed, cursor, length) ?: return emptyMap()
            if (key.isNotBlank() && value.isNotBlank()) {
                result[key] = value
            }
            index = afterValue
        }
        return result
    }

    private fun skipWhitespaceAndCommas(input: String, start: Int, end: Int): Int {
        var idx = start
        while (idx < end && (input[idx].isWhitespace() || input[idx] == ',')) {
            idx++
        }
        return idx
    }

    private fun skipWhitespace(input: String, start: Int, end: Int): Int {
        var idx = start
        while (idx < end && input[idx].isWhitespace()) {
            idx++
        }
        return idx
    }

    private fun readJsonString(input: String, startQuoteIndex: Int, end: Int): Pair<String, Int>? {
        val sb = StringBuilder()
        var idx = startQuoteIndex + 1
        while (idx < end) {
            val ch = input[idx]
            when (ch) {
                '\\' -> {
                    idx++
                    if (idx >= end) return null
                    when (val esc = input[idx]) {
                        '"', '\\', '/' -> sb.append(esc)
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        else -> sb.append(esc)
                    }
                }
                '"' -> return sb.toString() to (idx + 1)
                else -> sb.append(ch)
            }
            idx++
        }
        return null
    }

    private fun escapeJson(value: String): String = buildString(value.length) {
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }
}

class AaynaPreferencesRepository(private val context: Context) {
    private val dataStore: DataStore<Preferences>
        get() = context.applicationContext.aaynaDataStore

    private val safePreferences: Flow<Preferences> =
        dataStore.data.catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }

    val settingsFlow: Flow<UserSettingsSnapshot> =
        safePreferences.map(::mapPreferencesToSnapshot)

    val activeCameraIdFlow: Flow<String> =
        safePreferences.map { prefs ->
            prefs[KEY_ACTIVE_CAMERA_ID] ?: CameraCatalog.defaultCamera.id
        }

    val favoriteCameraIdsFlow: Flow<Set<String>> =
        safePreferences.map { prefs ->
            prefs[KEY_FAVORITE_CAMERA_IDS]?.toSet() ?: emptySet()
        }

    suspend fun readInitialSettings(): UserSettingsSnapshot = settingsFlow.first()

    suspend fun readInitialActiveCameraId(): String = activeCameraIdFlow.first()

    suspend fun readInitialFavoriteCameraIds(): Set<String> = favoriteCameraIdsFlow.first()

    suspend fun setStorageMode(mode: CaptureStorageMode) {
        dataStore.edit { it[KEY_STORAGE_MODE] = mode.name }
    }

    suspend fun setDualSaveEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_DUAL_SAVE_ENABLED] = enabled }
    }

    suspend fun setResolutionPreset(preset: ResolutionPreset) {
        dataStore.edit { it[KEY_RESOLUTION_PRESET] = preset.name }
    }

    suspend fun setJpegQuality(quality: Int) {
        dataStore.edit {
            it[KEY_JPEG_QUALITY] =
                quality.coerceIn(
                    UserSettingsSnapshot.MIN_JPEG_QUALITY,
                    UserSettingsSnapshot.MAX_JPEG_QUALITY
                )
        }
    }

    suspend fun setFrontMirrorEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_FRONT_MIRROR_ENABLED] = enabled }
    }

    suspend fun setHardwareAeCompatibility(enabled: Boolean) {
        dataStore.edit { it[KEY_HARDWARE_AE_COMPAT] = enabled }
    }

    suspend fun setStampFormat(format: StampFormat) {
        dataStore.edit { it[KEY_STAMP_FORMAT] = format.name }
    }

    suspend fun setUseEraYearOffset(enabled: Boolean) {
        dataStore.edit { it[KEY_USE_ERA_YEAR_OFFSET] = enabled }
    }

    suspend fun setStampColor(style: StampStyle) {
        dataStore.edit { it[KEY_STAMP_COLOR] = style.name }
    }

    suspend fun setStampFontStyle(fontStyle: StampFontStyle) {
        dataStore.edit { it[KEY_STAMP_FONT_STYLE] = fontStyle.name }
    }

    suspend fun setStampPosition(position: StampPosition) {
        dataStore.edit { it[KEY_STAMP_POSITION] = position.name }
    }

    suspend fun setStampGlow(glow: Float) {
        dataStore.edit { it[KEY_STAMP_GLOW] = glow.coerceIn(0f, 1f) }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_HAPTICS_ENABLED] = enabled }
    }

    suspend fun setSfxEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_SFX_ENABLED] = enabled }
    }

    suspend fun setGridEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_GRID_ENABLED] = enabled }
    }

    suspend fun setRollCapacity(capacity: Int) {
        dataStore.edit { it[KEY_ROLL_CAPACITY] = capacity.coerceAtLeast(1) }
    }

    suspend fun setActiveCameraId(cameraId: String) {
        dataStore.edit { it[KEY_ACTIVE_CAMERA_ID] = cameraId }
    }

    suspend fun setSkinForCamera(cameraId: String, skinId: String) {
        if (cameraId.isBlank()) return
        dataStore.edit { prefs ->
            val current = SkinMapJsonCodec.decode(prefs[KEY_SELECTED_SKIN_BY_CAMERA]).toMutableMap()
            if (skinId.isBlank()) {
                current.remove(cameraId)
            } else {
                current[cameraId] = skinId
            }
            prefs[KEY_SELECTED_SKIN_BY_CAMERA] = SkinMapJsonCodec.encode(current)
        }
    }

    suspend fun clearSkinForCamera(cameraId: String) {
        setSkinForCamera(cameraId = cameraId, skinId = "")
    }

    suspend fun toggleFavoriteCamera(cameraId: String) {
        if (cameraId.isBlank()) return
        dataStore.edit { prefs ->
            val current = prefs[KEY_FAVORITE_CAMERA_IDS]?.toMutableSet() ?: mutableSetOf()
            if (!current.add(cameraId)) {
                current.remove(cameraId)
            }
            prefs[KEY_FAVORITE_CAMERA_IDS] = current
        }
    }

    suspend fun setFavoriteCameras(cameraIds: Set<String>) {
        dataStore.edit { prefs ->
            prefs[KEY_FAVORITE_CAMERA_IDS] = cameraIds.filter { it.isNotBlank() }.toSet()
        }
    }

    companion object {
        internal fun mapPreferencesToSnapshot(prefs: Preferences): UserSettingsSnapshot {
            val defaults = UserSettingsSnapshot()
            val rawQuality = prefs[KEY_JPEG_QUALITY] ?: defaults.jpegQuality
            return UserSettingsSnapshot(
                storageMode =
                parseEnum(
                    raw = prefs[KEY_STORAGE_MODE],
                    default = defaults.storageMode
                ),
                dualSaveEnabled = prefs[KEY_DUAL_SAVE_ENABLED] ?: defaults.dualSaveEnabled,
                resolutionPreset =
                parseEnum(
                    raw = prefs[KEY_RESOLUTION_PRESET],
                    default = defaults.resolutionPreset
                ),
                jpegQuality =
                rawQuality.coerceIn(
                    UserSettingsSnapshot.MIN_JPEG_QUALITY,
                    UserSettingsSnapshot.MAX_JPEG_QUALITY
                ),
                isFrontMirrorEnabled = prefs[KEY_FRONT_MIRROR_ENABLED] ?: defaults.isFrontMirrorEnabled,
                useHardwareAeCompatibility =
                prefs[KEY_HARDWARE_AE_COMPAT] ?: defaults.useHardwareAeCompatibility,
                stampFormat =
                parseEnum(
                    raw = prefs[KEY_STAMP_FORMAT],
                    default = defaults.stampFormat
                ),
                useEraYearOffset = prefs[KEY_USE_ERA_YEAR_OFFSET] ?: defaults.useEraYearOffset,
                stampColor =
                parseEnum(
                    raw = prefs[KEY_STAMP_COLOR],
                    default = defaults.stampColor
                ),
                stampFontStyle =
                parseEnum(
                    raw = prefs[KEY_STAMP_FONT_STYLE],
                    default = defaults.stampFontStyle
                ),
                stampPosition =
                parseEnum(
                    raw = prefs[KEY_STAMP_POSITION],
                    default = defaults.stampPosition
                ),
                stampGlow = (prefs[KEY_STAMP_GLOW] ?: defaults.stampGlow).coerceIn(0f, 1f),
                hapticsEnabled = prefs[KEY_HAPTICS_ENABLED] ?: defaults.hapticsEnabled,
                sfxEnabled = prefs[KEY_SFX_ENABLED] ?: defaults.sfxEnabled,
                gridEnabled = prefs[KEY_GRID_ENABLED] ?: defaults.gridEnabled,
                rollCapacity = (prefs[KEY_ROLL_CAPACITY] ?: defaults.rollCapacity).coerceAtLeast(1),
                selectedSkinByCamera = SkinMapJsonCodec.decode(prefs[KEY_SELECTED_SKIN_BY_CAMERA])
            )
        }

        private inline fun <reified T : Enum<T>> parseEnum(raw: String?, default: T): T {
            if (raw == null) return default
            return enumValues<T>().firstOrNull { it.name == raw } ?: default
        }

        val KEY_STORAGE_MODE = stringPreferencesKey("storage_mode")
        val KEY_DUAL_SAVE_ENABLED = booleanPreferencesKey("dual_save_enabled")
        val KEY_RESOLUTION_PRESET = stringPreferencesKey("resolution_preset")
        val KEY_JPEG_QUALITY = intPreferencesKey("jpeg_quality")
        val KEY_FRONT_MIRROR_ENABLED = booleanPreferencesKey("front_mirror_enabled")
        val KEY_HARDWARE_AE_COMPAT = booleanPreferencesKey("hardware_ae_compatibility")
        val KEY_STAMP_FORMAT = stringPreferencesKey("stamp_format")
        val KEY_USE_ERA_YEAR_OFFSET = booleanPreferencesKey("use_era_year_offset")
        val KEY_STAMP_COLOR = stringPreferencesKey("stamp_color")
        val KEY_STAMP_FONT_STYLE = stringPreferencesKey("stamp_font_style")
        val KEY_STAMP_POSITION = stringPreferencesKey("stamp_position")
        val KEY_STAMP_GLOW = floatPreferencesKey("stamp_glow")
        val KEY_HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val KEY_SFX_ENABLED = booleanPreferencesKey("sfx_enabled")
        val KEY_GRID_ENABLED = booleanPreferencesKey("grid_enabled")
        val KEY_ROLL_CAPACITY = intPreferencesKey("roll_capacity")
        val KEY_ACTIVE_CAMERA_ID = stringPreferencesKey("active_camera_id")
        val KEY_SELECTED_SKIN_BY_CAMERA = stringPreferencesKey("selected_skin_by_camera")
        val KEY_FAVORITE_CAMERA_IDS = stringSetPreferencesKey("favorite_camera_ids")
    }
}
