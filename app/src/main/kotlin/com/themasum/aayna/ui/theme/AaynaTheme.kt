package com.themasum.aayna.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object AaynaColors {
    val CharcoalBg = Color(0xFF121316)
    val SplashBg = Color(0xFF141518)
    val CharcoalSurface = Color(0xFF1C1E24)
    val CharcoalElevated = Color(0xFF23262E)
    val MetallicBorder = Color(0xFF2A2E38)
    val SilverText = Color(0xFFD8DCE3)
    val MutedText = Color(0xFF8E95A5)
    val AmberLed = Color(0xFFE09F3E)
    val CyanLed = Color(0xFF39D0D8)
    val EmeraldLcd = Color(0xFF39FFAE)
    val RedLed = Color(0xFFFF453A)
    val CleanWhite = Color(0xFFF5F5F0)
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
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            letterSpacing = 0.8.sp
        ),
        titleMedium =
        TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            letterSpacing = 0.4.sp
        ),
        labelMedium =
        TextStyle(
            fontFamily = FontFamily.Monospace,
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
