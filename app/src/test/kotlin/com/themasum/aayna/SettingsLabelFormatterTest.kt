package com.themasum.aayna

import com.themasum.aayna.core.model.CaptureStorageMode
import com.themasum.aayna.core.model.ResolutionPreset
import com.themasum.aayna.core.model.StampFormat
import com.themasum.aayna.core.model.StampStyle
import com.themasum.aayna.ui.SettingsLabelFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsLabelFormatterTest {
    @Test
    fun `storage mode labels match ProCCD console copy`() {
        assertEquals(
            "Instant Digicam Save",
            SettingsLabelFormatter.storageModeTitle(CaptureStorageMode.INSTANT_DIGICAM)
        )
        assertEquals(
            "Non-Destructive Roll / Negatives",
            SettingsLabelFormatter.storageModeTitle(CaptureStorageMode.NON_DESTRUCTIVE_ROLL)
        )
        assertEquals(
            "Analog Developing Ritual",
            SettingsLabelFormatter.storageModeTitle(CaptureStorageMode.ANALOG_DEVELOPING_RITUAL)
        )
    }

    @Test
    fun `resolution preset labels match expected hardware presets`() {
        assertEquals("3.2MP CCD", SettingsLabelFormatter.resolutionPresetLabel(ResolutionPreset.CCD_3_2MP))
        assertEquals("5MP Digicam", SettingsLabelFormatter.resolutionPresetLabel(ResolutionPreset.DIGICAM_5MP))
        assertEquals("8MP Crisp", SettingsLabelFormatter.resolutionPresetLabel(ResolutionPreset.VINTAGE_8MP))
    }

    @Test
    fun `stamp format and color labels are formatted cleanly`() {
        assertEquals("'26 11 14", SettingsLabelFormatter.stampFormatLabel(StampFormat.SHORT_YY_MM_DD))
        assertEquals("2026.11.14", SettingsLabelFormatter.stampFormatLabel(StampFormat.DOT_YYYY_MM_DD))
        assertEquals("14.11.26", SettingsLabelFormatter.stampFormatLabel(StampFormat.EURO_DD_MM_YY))

        assertEquals("Amber LED", SettingsLabelFormatter.stampColorLabel(StampStyle.DOT_MATRIX_AMBER))
        assertEquals("Classic Red", SettingsLabelFormatter.stampColorLabel(StampStyle.CLASSIC_RED))
        assertEquals("Emerald LCD", SettingsLabelFormatter.stampColorLabel(StampStyle.SEVEN_SEGMENT_EMERALD))
        assertEquals("Clean White", SettingsLabelFormatter.stampColorLabel(StampStyle.CLEAN_WHITE))
    }
}
