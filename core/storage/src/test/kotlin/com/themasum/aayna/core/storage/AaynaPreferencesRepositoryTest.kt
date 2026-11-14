package com.themasum.aayna.core.storage

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.themasum.aayna.core.model.StampFontStyle
import com.themasum.aayna.core.model.StampPosition
import com.themasum.aayna.core.model.UserSettingsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AaynaPreferencesRepositoryTest {
    @Test
    fun `empty preferences map to canonical UserSettingsSnapshot defaults`() {
        val snapshot = AaynaPreferencesRepository.mapPreferencesToSnapshot(emptyPreferences())
        val expected = UserSettingsSnapshot()
        assertEquals(expected, snapshot)
        assertTrue(snapshot.isFrontMirrorEnabled)
        assertEquals(UserSettingsSnapshot.DEFAULT_JPEG_QUALITY, snapshot.jpegQuality)
        assertFalse(snapshot.useHardwareAeCompatibility)
        assertEquals(StampFontStyle.DOT_MATRIX, snapshot.stampFontStyle)
        assertEquals(StampPosition.BOTTOM_RIGHT, snapshot.stampPosition)
        assertTrue(snapshot.selectedSkinByCamera.isEmpty())
    }

    @Test
    fun `mapPreferencesToSnapshot reads expanded fields and clamps bounds`() {
        val prefs =
            mutablePreferencesOf(
                AaynaPreferencesRepository.KEY_JPEG_QUALITY to 120,
                AaynaPreferencesRepository.KEY_FRONT_MIRROR_ENABLED to false,
                AaynaPreferencesRepository.KEY_HARDWARE_AE_COMPAT to true,
                AaynaPreferencesRepository.KEY_STAMP_FONT_STYLE to StampFontStyle.VCR_OSD.name,
                AaynaPreferencesRepository.KEY_STAMP_POSITION to StampPosition.TOP_LEFT.name,
                AaynaPreferencesRepository.KEY_STAMP_GLOW to 2.5f,
                AaynaPreferencesRepository.KEY_ROLL_CAPACITY to 0,
                AaynaPreferencesRepository.KEY_SELECTED_SKIN_BY_CAMERA to
                    """{"z30":"skin_y2k_cyan","inst":"skin_inst_blush"}"""
            )

        val snapshot = AaynaPreferencesRepository.mapPreferencesToSnapshot(prefs)
        assertEquals(UserSettingsSnapshot.MAX_JPEG_QUALITY, snapshot.jpegQuality)
        assertFalse(snapshot.isFrontMirrorEnabled)
        assertTrue(snapshot.useHardwareAeCompatibility)
        assertEquals(StampFontStyle.VCR_OSD, snapshot.stampFontStyle)
        assertEquals(StampPosition.TOP_LEFT, snapshot.stampPosition)
        assertEquals(1f, snapshot.stampGlow, 0.0001f)
        assertEquals(1, snapshot.rollCapacity)
        assertEquals(
            mapOf("z30" to "skin_y2k_cyan", "inst" to "skin_inst_blush"),
            snapshot.selectedSkinByCamera
        )
    }

    @Test
    fun `SkinMapJsonCodec round-trips and handles malformed payloads safely`() {
        val input = mapOf("z30" to "skin_charcoal_metal", "dv" to "skin_dv_cobalt_metallic")
        val encoded = SkinMapJsonCodec.encode(input)
        val decoded = SkinMapJsonCodec.decode(encoded)
        assertEquals(input, decoded)

        assertTrue(SkinMapJsonCodec.decode(null).isEmpty())
        assertTrue(SkinMapJsonCodec.decode("").isEmpty())
        assertTrue(SkinMapJsonCodec.decode("{invalid").isEmpty())
        assertTrue(SkinMapJsonCodec.decode("""{"a":123}""").isEmpty())
    }
}
