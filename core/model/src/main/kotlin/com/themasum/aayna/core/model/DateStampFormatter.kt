package com.themasum.aayna.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

object DateStampFormatter {
    const val ATLAS_ALLOWED_CHARS: String = "0123456789'.:-/ ABCDEFGHIJKLMNOPQRSTUVWXYZ"

    private val allowedCharSet: Set<Char> = ATLAS_ALLOWED_CHARS.toSet()

    fun format(
        date: LocalDate,
        format: StampFormat,
        useEraYearOffset: Boolean = false,
        eraYear: Int = date.year,
        customText: String = ""
    ): String {
        val displayYear = if (useEraYearOffset) eraYear.coerceIn(1900, 2999) else date.year
        val yy = (displayYear % 100).toString().padStart(2, '0')
        val yyyy = displayYear.toString().padStart(4, '0')
        val mm = date.monthValue.toString().padStart(2, '0')
        val dd = date.dayOfMonth.toString().padStart(2, '0')

        val raw =
            when (format) {
                StampFormat.SHORT_YY_MM_DD -> "'$yy $mm $dd"
                StampFormat.DOT_YYYY_MM_DD -> "$yyyy.$mm.$dd"
                StampFormat.EURO_DD_MM_YY -> "$dd.$mm.$yy"
                StampFormat.CUSTOM -> {
                    val sanitized = sanitizeForAtlas(customText)
                    sanitized.ifBlank { "'$yy $mm $dd" }
                }
            }
        return sanitizeForAtlas(raw)
    }

    fun format(
        epochMillis: Long,
        format: StampFormat,
        useEraYearOffset: Boolean = false,
        eraYear: Int = 2004,
        customText: String = "",
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val localDate = Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()
        return format(
            date = localDate,
            format = format,
            useEraYearOffset = useEraYearOffset,
            eraYear = eraYear,
            customText = customText
        )
    }

    fun sanitizeForAtlas(input: String): String {
        val upper = input.uppercase(Locale.US)
        val builder = StringBuilder(upper.length)
        for (ch in upper) {
            if (ch in allowedCharSet) {
                builder.append(ch)
            }
        }
        return builder.toString().trim()
    }

    fun formatTimecode(durationMs: Long): String {
        val totalSeconds = (durationMs.coerceAtLeast(0L) / 1000L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }
}
