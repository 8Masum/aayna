package com.themasum.aayna.ui

import com.themasum.aayna.core.model.CaptureStorageMode
import com.themasum.aayna.core.model.ResolutionPreset
import com.themasum.aayna.core.model.StampFontStyle
import com.themasum.aayna.core.model.StampFormat
import com.themasum.aayna.core.model.StampPosition
import com.themasum.aayna.core.model.StampStyle
import com.themasum.aayna.core.model.UserSettingsSnapshot

object SettingsLabelFormatter {
    fun storageModeTitle(mode: CaptureStorageMode): String = when (mode) {
        CaptureStorageMode.INSTANT_DIGICAM -> "Instant Digicam Save"
        CaptureStorageMode.NON_DESTRUCTIVE_ROLL -> "Non-Destructive Roll / Negatives"
        CaptureStorageMode.ANALOG_DEVELOPING_RITUAL -> "Analog Developing Ritual"
    }

    fun storageModeSubtitle(mode: CaptureStorageMode): String = when (mode) {
        CaptureStorageMode.INSTANT_DIGICAM ->
            "Exports rendered JPEG straight to Pictures/Aayna."
        CaptureStorageMode.NON_DESTRUCTIVE_ROLL ->
            "Keeps clean digital negative for re-grading in Studio."
        CaptureStorageMode.ANALOG_DEVELOPING_RITUAL ->
            "Queues shots on a film roll until you develop the roll."
    }

    fun resolutionPresetLabel(preset: ResolutionPreset): String = when (preset) {
        ResolutionPreset.CCD_3_2MP -> "3.2MP CCD"
        ResolutionPreset.DIGICAM_5MP -> "5MP Digicam"
        ResolutionPreset.VINTAGE_8MP -> "8MP Crisp"
    }

    fun stampFormatLabel(format: StampFormat): String = when (format) {
        StampFormat.SHORT_YY_MM_DD -> "'26 11 14"
        StampFormat.DOT_YYYY_MM_DD -> "2026.11.14"
        StampFormat.EURO_DD_MM_YY -> "14.11.26"
        StampFormat.CUSTOM -> "Custom"
    }

    fun stampColorLabel(style: StampStyle): String = when (style) {
        StampStyle.DOT_MATRIX_AMBER -> "Amber LED"
        StampStyle.CLASSIC_RED -> "Classic Red"
        StampStyle.SEVEN_SEGMENT_EMERALD -> "Emerald LCD"
        StampStyle.CLEAN_WHITE -> "Clean White"
        StampStyle.QUARTZ_ORANGE -> "Quartz Orange"
    }

    fun stampFontLabel(fontStyle: StampFontStyle): String = when (fontStyle) {
        StampFontStyle.DOT_MATRIX -> "Dot Matrix"
        StampFontStyle.SEVEN_SEGMENT -> "7-Segment"
        StampFontStyle.VCR_OSD -> "VCR OSD"
        StampFontStyle.PIXEL -> "Pixel LCD"
        StampFontStyle.CLEAN_SANS -> "Hardware Sans"
    }

    fun stampPositionLabel(position: StampPosition): String = when (position) {
        StampPosition.BOTTOM_RIGHT -> "Bottom Right"
        StampPosition.BOTTOM_LEFT -> "Bottom Left"
        StampPosition.TOP_RIGHT -> "Top Right"
        StampPosition.TOP_LEFT -> "Top Left"
    }

    fun jpegQualityLabel(quality: Int): String = "${
        quality.coerceIn(
            UserSettingsSnapshot.MIN_JPEG_QUALITY,
            UserSettingsSnapshot.MAX_JPEG_QUALITY
        )
    }%"
}
