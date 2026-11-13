package com.themasum.aayna.core.storage

import com.themasum.aayna.core.model.CameraCatalog
import com.themasum.aayna.core.model.CaptureRecipe
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class ExportMetadataFormatterTest {
    @Test
    fun `formatExifDateTime formats standard EXIF timestamp in given ZoneId`() {
        // 2024-06-15 14:30:45 UTC
        val epochMs = 1_718_461_845_123L
        val formatted = ExportMetadataFormatter.formatExifDateTime(epochMs, ZoneOffset.UTC)
        assertEquals("2024:06:15 14:30:45", formatted)
    }

    @Test
    fun `formatFileName produces clean JPEG name with and without suffix`() {
        val profile = CameraCatalog.dc04SilverPulse
        val recipe =
            CaptureRecipe(
                recipeId = "rec-1",
                cameraId = profile.id,
                capturedAtEpochMs = 1_718_461_845_007L,
                randomSeed = 42L
            )

        val standard =
            ExportMetadataFormatter.formatFileName(
                profile = profile,
                recipe = recipe,
                zoneId = ZoneOffset.UTC
            )
        assertEquals("AAYNA_DC04_20240615_143045_007.jpg", standard)

        val cleanDualSave =
            ExportMetadataFormatter.formatFileName(
                profile = profile,
                recipe = recipe,
                suffix = "clean",
                zoneId = ZoneOffset.UTC
            )
        assertEquals("AAYNA_DC04_20240615_143045_007_clean.jpg", cleanDualSave)
    }
}
