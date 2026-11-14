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
        val profile = CameraCatalog.z30
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
        assertEquals("AAYNA_Z30_20240615_143045_007.jpg", standard)

        val cleanDualSave =
            ExportMetadataFormatter.formatFileName(
                profile = profile,
                recipe = recipe,
                suffix = "clean",
                zoneId = ZoneOffset.UTC
            )
        assertEquals("AAYNA_Z30_20240615_143045_007_clean.jpg", cleanDualSave)

        val d3dGif =
            ExportMetadataFormatter.formatFileName(
                profile = profile,
                recipe = recipe,
                suffix = "d3d",
                zoneId = ZoneOffset.UTC,
                extension = "gif"
            )
        assertEquals("AAYNA_Z30_20240615_143045_007_d3d.gif", d3dGif)
    }

    @Test
    fun `formatVideoFileName and formatMakerNote produce clean-room identifiers`() {
        val profile = CameraCatalog.z30
        val recipe =
            CaptureRecipe(
                recipeId = "rec-2",
                cameraId = profile.id,
                capturedAtEpochMs = 1_718_461_845_007L,
                randomSeed = 99L,
                skinId = "skin_charcoal_metal"
            )

        val videoName = ExportMetadataFormatter.formatVideoFileName(profile, recipe.capturedAtEpochMs, ZoneOffset.UTC)
        assertEquals("AAYNA_Z30_20240615_143045_007.mp4", videoName)

        val videoNameWithSuffix =
            ExportMetadataFormatter.formatVideoFileName(
                profile = profile,
                capturedAtMs = recipe.capturedAtEpochMs,
                zoneId = ZoneOffset.UTC,
                suffix = "video"
            )
        assertEquals("AAYNA_Z30_20240615_143045_007_video.mp4", videoNameWithSuffix)

        val makerNote = ExportMetadataFormatter.formatMakerNote(profile, recipe)
        assertEquals("AAYNA;cam=${profile.id};media=STILL;skin=skin_charcoal_metal", makerNote)
    }

    @Test
    fun `EXIF_SOFTWARE reflects Aayna v2_5`() {
        assertEquals("Aayna v2.5", MediaStoreExporter.EXIF_SOFTWARE)
    }
}
