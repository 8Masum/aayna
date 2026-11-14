package com.themasum.aayna

import androidx.compose.ui.graphics.Color
import com.themasum.aayna.core.model.StampFontStyle
import com.themasum.aayna.core.model.StampPosition
import com.themasum.aayna.core.model.UserSettingsSnapshot
import com.themasum.aayna.ui.theme.AaynaColors
import com.themasum.aayna.ui.theme.AaynaFonts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppShellContractTest {
    @Test
    fun `user settings defaults preserve real date and v2 camera pipeline defaults`() {
        val defaults = UserSettingsSnapshot()

        assertFalse(defaults.useEraYearOffset)
        assertTrue(defaults.isFrontMirrorEnabled)
        assertFalse(defaults.useHardwareAeCompatibility)
        assertEquals(92, defaults.jpegQuality)
        assertEquals(StampFontStyle.DOT_MATRIX, defaults.stampFontStyle)
        assertEquals(StampPosition.BOTTOM_RIGHT, defaults.stampPosition)
    }

    @Test
    fun `proccd charcoal darkroom theme palette uses canonical hex values`() {
        assertEquals(Color(0xFF191818), AaynaColors.CharcoalBg)
        assertEquals(Color(0xFF242222), AaynaColors.CharcoalSurface)
        assertEquals(Color(0xFFF2EDE4), AaynaColors.SilverText)
        assertEquals(Color(0xFFFFB347), AaynaColors.AmberLed)
    }

    @Test
    fun `font mapper resolves distinct FontFamily for each StampFontStyle`() {
        val mapped = StampFontStyle.entries.map { AaynaFonts.forStampFontStyle(it) }
        assertEquals(5, mapped.size)
        assertEquals(5, mapped.distinct().size)
    }
}
