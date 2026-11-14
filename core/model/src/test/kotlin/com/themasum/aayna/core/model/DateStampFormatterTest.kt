package com.themasum.aayna.core.model

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DateStampFormatterTest {
    private val sampleDate = LocalDate.of(2026, 11, 14)

    @Test
    fun `formats real date by default`() {
        assertEquals(
            "'26 11 14",
            DateStampFormatter.format(sampleDate, StampFormat.SHORT_YY_MM_DD)
        )
        assertEquals(
            "2026.11.14",
            DateStampFormatter.format(sampleDate, StampFormat.DOT_YYYY_MM_DD)
        )
        assertEquals(
            "14.11.26",
            DateStampFormatter.format(sampleDate, StampFormat.EURO_DD_MM_YY)
        )
    }

    @Test
    fun `applies era year offset when enabled`() {
        assertEquals(
            "'04 11 14",
            DateStampFormatter.format(
                date = sampleDate,
                format = StampFormat.SHORT_YY_MM_DD,
                useEraYearOffset = true,
                eraYear = 2004
            )
        )
        assertEquals(
            "1998.11.14",
            DateStampFormatter.format(
                date = sampleDate,
                format = StampFormat.DOT_YYYY_MM_DD,
                useEraYearOffset = true,
                eraYear = 1998
            )
        )
        assertEquals(
            "14.11.77",
            DateStampFormatter.format(
                date = sampleDate,
                format = StampFormat.EURO_DD_MM_YY,
                useEraYearOffset = true,
                eraYear = 1977
            )
        )
    }

    @Test
    fun `custom format sanitizes to atlas allowed chars`() {
        val formatted =
            DateStampFormatter.format(
                date = sampleDate,
                format = StampFormat.CUSTOM,
                customText = "dhaka '04!@#"
            )
        assertEquals("DHAKA '04", formatted)
        val allowed = DateStampFormatter.ATLAS_ALLOWED_CHARS.toSet()
        assertTrue(formatted.all { it in allowed })
    }

    @Test
    fun `epochMillis overload formats consistently in UTC`() {
        val epochMs = sampleDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(
            "'26 11 14",
            DateStampFormatter.format(
                epochMillis = epochMs,
                format = StampFormat.SHORT_YY_MM_DD,
                zoneId = ZoneOffset.UTC
            )
        )
    }

    @Test
    fun `formatTimecode formats camcorder durations cleanly`() {
        assertEquals("00:00:00", DateStampFormatter.formatTimecode(-500L))
        assertEquals("00:01:05", DateStampFormatter.formatTimecode(65_000L))
        assertEquals("01:02:03", DateStampFormatter.formatTimecode(3_723_400L))
    }
}
