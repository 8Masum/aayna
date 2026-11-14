package com.themasum.aayna

import com.themasum.aayna.core.model.CaptureStorageMode
import com.themasum.aayna.core.model.ResolutionPreset
import com.themasum.aayna.core.model.StampFontStyle
import com.themasum.aayna.core.model.StampFormat
import com.themasum.aayna.core.model.StampPosition
import com.themasum.aayna.core.model.StampStyle
import com.themasum.aayna.ui.SettingsLabelFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        CaptureStorageMode.entries.forEach { mode ->
            assertTrue(SettingsLabelFormatter.storageModeSubtitle(mode).isNotBlank())
        }
    }

    @Test
    fun `resolution preset labels match expected hardware presets`() {
        assertEquals("3.2MP CCD", SettingsLabelFormatter.resolutionPresetLabel(ResolutionPreset.CCD_3_2MP))
        assertEquals("5MP Digicam", SettingsLabelFormatter.resolutionPresetLabel(ResolutionPreset.DIGICAM_5MP))
        assertEquals("8MP Crisp", SettingsLabelFormatter.resolutionPresetLabel(ResolutionPreset.VINTAGE_8MP))
    }

    @Test
    fun `all four stamp formats and five stamp colors are formatted cleanly`() {
        assertEquals("'26 11 14", SettingsLabelFormatter.stampFormatLabel(StampFormat.SHORT_YY_MM_DD))
        assertEquals("2026.11.14", SettingsLabelFormatter.stampFormatLabel(StampFormat.DOT_YYYY_MM_DD))
        assertEquals("14.11.26", SettingsLabelFormatter.stampFormatLabel(StampFormat.EURO_DD_MM_YY))
        assertEquals("Custom", SettingsLabelFormatter.stampFormatLabel(StampFormat.CUSTOM))

        assertEquals("Amber LED", SettingsLabelFormatter.stampColorLabel(StampStyle.DOT_MATRIX_AMBER))
        assertEquals("Classic Red", SettingsLabelFormatter.stampColorLabel(StampStyle.CLASSIC_RED))
        assertEquals("Emerald LCD", SettingsLabelFormatter.stampColorLabel(StampStyle.SEVEN_SEGMENT_EMERALD))
        assertEquals("Clean White", SettingsLabelFormatter.stampColorLabel(StampStyle.CLEAN_WHITE))
        assertEquals("Quartz Orange", SettingsLabelFormatter.stampColorLabel(StampStyle.QUARTZ_ORANGE))
    }

    @Test
    fun `all five stamp font styles and four stamp positions are formatted cleanly`() {
        assertEquals("Dot Matrix", SettingsLabelFormatter.stampFontLabel(StampFontStyle.DOT_MATRIX))
        assertEquals("7-Segment", SettingsLabelFormatter.stampFontLabel(StampFontStyle.SEVEN_SEGMENT))
        assertEquals("VCR OSD", SettingsLabelFormatter.stampFontLabel(StampFontStyle.VCR_OSD))
        assertEquals("Pixel LCD", SettingsLabelFormatter.stampFontLabel(StampFontStyle.PIXEL))
        assertEquals("Hardware Sans", SettingsLabelFormatter.stampFontLabel(StampFontStyle.CLEAN_SANS))

        assertEquals("Bottom Right", SettingsLabelFormatter.stampPositionLabel(StampPosition.BOTTOM_RIGHT))
        assertEquals("Bottom Left", SettingsLabelFormatter.stampPositionLabel(StampPosition.BOTTOM_LEFT))
        assertEquals("Top Right", SettingsLabelFormatter.stampPositionLabel(StampPosition.TOP_RIGHT))
        assertEquals("Top Left", SettingsLabelFormatter.stampPositionLabel(StampPosition.TOP_LEFT))
    }

    @Test
    fun `jpeg quality label clamps into supported 60 to 100 range`() {
        assertEquals("60%", SettingsLabelFormatter.jpegQualityLabel(25))
        assertEquals("92%", SettingsLabelFormatter.jpegQualityLabel(92))
        assertEquals("100%", SettingsLabelFormatter.jpegQualityLabel(120))
    }
}
