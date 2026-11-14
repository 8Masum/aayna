package com.themasum.aayna.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.themasum.aayna.R
import com.themasum.aayna.core.model.StampFontStyle

object AaynaColors {
    val CharcoalBg = Color(0xFF191818)
    val SplashBg = Color(0xFF191818)
    val CharcoalSurface = Color(0xFF242222)
    val CharcoalElevated = Color(0xFF302D2D)
    val CharcoalRecessed = Color(0xFF121111)
    val MetallicBorder = Color(0xFF3E3A3A)
    val SilverText = Color(0xFFF2EDE4)
    val MutedText = Color(0xFFB7B0A5)
    val AmberLed = Color(0xFFFFB347)
    val CyanLed = Color(0xFF4CD4E8)
    val EmeraldLcd = Color(0xFF39FFAE)
    val RedLed = Color(0xFFFF453A)
    val CleanWhite = Color(0xFFF2EDE4)
}

object AaynaFonts {
    val LedSevenSegment: FontFamily = FontFamily(Font(R.font.aayna_led_seven_segment))
    val DotMatrix: FontFamily = FontFamily(Font(R.font.aayna_dot_matrix))
    val VcrOsd: FontFamily = FontFamily(Font(R.font.aayna_vcr_osd))
    val PixelLcd: FontFamily = FontFamily(Font(R.font.aayna_pixel_lcd))
    val HardwareCondensed: FontFamily = FontFamily(Font(R.font.aayna_hardware_condensed))

    fun forStampFontStyle(style: StampFontStyle): FontFamily = when (style) {
        StampFontStyle.DOT_MATRIX -> DotMatrix
        StampFontStyle.SEVEN_SEGMENT -> LedSevenSegment
        StampFontStyle.VCR_OSD -> VcrOsd
        StampFontStyle.PIXEL -> PixelLcd
        StampFontStyle.CLEAN_SANS -> HardwareCondensed
    }
}

private val AaynaDarkColorScheme =
    darkColorScheme(
        primary = AaynaColors.AmberLed,
        onPrimary = AaynaColors.CharcoalBg,
        secondary = AaynaColors.CyanLed,
        onSecondary = AaynaColors.CharcoalBg,
        tertiary = AaynaColors.EmeraldLcd,
        onTertiary = AaynaColors.CharcoalBg,
        background = AaynaColors.CharcoalBg,
        onBackground = AaynaColors.SilverText,
        surface = AaynaColors.CharcoalSurface,
        onSurface = AaynaColors.SilverText,
        surfaceVariant = AaynaColors.CharcoalElevated,
        onSurfaceVariant = AaynaColors.MutedText,
        outline = AaynaColors.MetallicBorder,
        error = AaynaColors.RedLed,
        onError = AaynaColors.CleanWhite
    )

private val AaynaTypography =
    Typography(
        titleLarge =
        TextStyle(
            fontFamily = AaynaFonts.HardwareCondensed,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            letterSpacing = 0.8.sp
        ),
        titleMedium =
        TextStyle(
            fontFamily = AaynaFonts.HardwareCondensed,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            letterSpacing = 0.4.sp
        ),
        labelMedium =
        TextStyle(
            fontFamily = AaynaFonts.HardwareCondensed,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            letterSpacing = 1.1.sp
        ),
        bodyMedium =
        TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp
        ),
        bodySmall =
        TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp
        )
    )

@Composable
fun AaynaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AaynaDarkColorScheme,
        typography = AaynaTypography,
        content = content
    )
}
