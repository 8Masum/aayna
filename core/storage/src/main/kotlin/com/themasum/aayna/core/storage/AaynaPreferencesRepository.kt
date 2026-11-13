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
import androidx.datastore.preferences.preferencesDataStore
import com.themasum.aayna.core.model.CameraCatalog
import com.themasum.aayna.core.model.CaptureStorageMode
import com.themasum.aayna.core.model.ResolutionPreset
import com.themasum.aayna.core.model.StampFormat
import com.themasum.aayna.core.model.StampStyle
import com.themasum.aayna.core.model.UserSettingsSnapshot
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.aaynaDataStore: DataStore<Preferences> by preferencesDataStore(name = "aayna_settings")

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
        safePreferences.map { prefs ->
            UserSettingsSnapshot(
                storageMode =
                parseEnum(
                    raw = prefs[KEY_STORAGE_MODE],
                    default = CaptureStorageMode.INSTANT_DIGICAM
                ),
                dualSaveEnabled = prefs[KEY_DUAL_SAVE_ENABLED] ?: false,
                resolutionPreset =
                parseEnum(
                    raw = prefs[KEY_RESOLUTION_PRESET],
                    default = ResolutionPreset.DIGICAM_5MP
                ),
                stampFormat =
                parseEnum(
                    raw = prefs[KEY_STAMP_FORMAT],
                    default = StampFormat.SHORT_YY_MM_DD
                ),
                useEraYearOffset = prefs[KEY_USE_ERA_YEAR_OFFSET] ?: false,
                stampColor =
                parseEnum(
                    raw = prefs[KEY_STAMP_COLOR],
                    default = StampStyle.DOT_MATRIX_AMBER
                ),
                stampGlow = (prefs[KEY_STAMP_GLOW] ?: 0.35f).coerceIn(0f, 1f),
                hapticsEnabled = prefs[KEY_HAPTICS_ENABLED] ?: true,
                sfxEnabled = prefs[KEY_SFX_ENABLED] ?: true,
                gridEnabled = prefs[KEY_GRID_ENABLED] ?: false,
                rollCapacity = (prefs[KEY_ROLL_CAPACITY] ?: 24).coerceAtLeast(1)
            )
        }

    val activeCameraIdFlow: Flow<String> =
        safePreferences.map { prefs ->
            prefs[KEY_ACTIVE_CAMERA_ID] ?: CameraCatalog.defaultCamera.id
        }

    suspend fun setStorageMode(mode: CaptureStorageMode) {
        dataStore.edit { it[KEY_STORAGE_MODE] = mode.name }
    }

    suspend fun setDualSaveEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_DUAL_SAVE_ENABLED] = enabled }
    }

    suspend fun setResolutionPreset(preset: ResolutionPreset) {
        dataStore.edit { it[KEY_RESOLUTION_PRESET] = preset.name }
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

    private inline fun <reified T : Enum<T>> parseEnum(raw: String?, default: T): T {
        if (raw == null) return default
        return enumValues<T>().firstOrNull { it.name == raw } ?: default
    }

    private companion object {
        val KEY_STORAGE_MODE = stringPreferencesKey("storage_mode")
        val KEY_DUAL_SAVE_ENABLED = booleanPreferencesKey("dual_save_enabled")
        val KEY_RESOLUTION_PRESET = stringPreferencesKey("resolution_preset")
        val KEY_STAMP_FORMAT = stringPreferencesKey("stamp_format")
        val KEY_USE_ERA_YEAR_OFFSET = booleanPreferencesKey("use_era_year_offset")
        val KEY_STAMP_COLOR = stringPreferencesKey("stamp_color")
        val KEY_STAMP_GLOW = floatPreferencesKey("stamp_glow")
        val KEY_HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val KEY_SFX_ENABLED = booleanPreferencesKey("sfx_enabled")
        val KEY_GRID_ENABLED = booleanPreferencesKey("grid_enabled")
        val KEY_ROLL_CAPACITY = intPreferencesKey("roll_capacity")
        val KEY_ACTIVE_CAMERA_ID = stringPreferencesKey("active_camera_id")
    }
}
