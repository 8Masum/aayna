package com.themasum.aayna.feature.gallery

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.themasum.aayna.core.gl.OffscreenGlExporter
import com.themasum.aayna.core.model.CameraCatalog
import com.themasum.aayna.core.model.FrameBorderStyle
import com.themasum.aayna.core.model.LensAttachment
import com.themasum.aayna.core.model.StampFontStyle
import com.themasum.aayna.core.model.StampPosition
import com.themasum.aayna.core.model.UserSettingsSnapshot
import com.themasum.aayna.core.storage.GifWigglegramEncoder
import com.themasum.aayna.core.storage.ThumbnailMemoryCache
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GalleryStudioInstrumentedTest {
    @Test
    fun thumbnailCacheLoadsAsyncAndInvalidatesCleanly() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cache = ThumbnailMemoryCache(maxMemoryBytes = 8 * 1024 * 1024)
        val tempFile = File(context.cacheDir, "gallery_thumb_test.jpg")
        val source =
            Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.rgb(210, 140, 80))
            }
        try {
            FileOutputStream(tempFile).use { out ->
                assertTrue(source.compress(Bitmap.CompressFormat.JPEG, 90, out))
            }
            val first = GalleryImageDecoder.loadThumbnailCached(tempFile.absolutePath, cache)
            assertNotNull(first)
            assertEquals(1, cache.entryCount())

            val second = GalleryImageDecoder.loadThumbnailCached(tempFile.absolutePath, cache)
            assertSame(first, second)

            cache.invalidatePath(tempFile.absolutePath)
            assertEquals(0, cache.entryCount())
        } finally {
            source.recycle()
            tempFile.delete()
            cache.evictAll()
        }
    }

    @Test
    fun studioReducerRendersThroughOffscreenExporterAndD3dGifEncoder() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val exporter = OffscreenGlExporter(context.assets)
        val source =
            Bitmap.createBitmap(160, 120, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.rgb(150, 120, 95))
            }
        try {
            val recipe =
                GalleryRecipeReducer.createImportedRecipe(
                    cameraId = CameraCatalog.z30.id,
                    settings = UserSettingsSnapshot(),
                    capturedAtEpochMs = 1_700_000_000_000L,
                    randomSeed = 77L,
                    recipeId = "inst_test_1"
                ).let {
                    GalleryRecipeReducer.updateLensAttachment(it, LensAttachment.SOFT_GLOW)
                }.let {
                    GalleryRecipeReducer.updateFrameStyle(it, FrameBorderStyle.POLAROID_SQUARE)
                }.let {
                    GalleryRecipeReducer.updateStampFontStyle(it, StampFontStyle.PIXEL)
                }.let {
                    GalleryRecipeReducer.updateStampPosition(it, StampPosition.TOP_RIGHT)
                }.let {
                    GalleryRecipeReducer.updateStraightenDegrees(it, 6.5f)
                }

            val params =
                GalleryRecipeReducer.buildGlRenderParams(
                    recipe = recipe,
                    profile = CameraCatalog.z30,
                    stampText = "'24 11 14",
                    grainBoost = 0.25f,
                    isHoldOriginal = false
                )

            val rendered =
                exporter.renderBitmap(
                    sourceBitmap = source,
                    profile = CameraCatalog.z30,
                    recipe = recipe,
                    stampText = params.stampText,
                    fallbackGains = params.fallbackGains,
                    maxEdgeOverride = 256,
                    skipStamp = params.skipStamp
                )
            assertEquals(256, rendered.width)
            rendered.recycle()

            val frames = GalleryImageDecoder.synthesizeParallaxFrames(source, count = 4)
            try {
                val strip =
                    exporter.renderPhotoBooth4Strip(
                        frames = frames,
                        profile = CameraCatalog.booth,
                        recipe = recipe,
                        stampText = params.stampText,
                        outputSizePx = 512
                    )
                assertEquals(512, strip.width)
                strip.recycle()

                val gifBytes =
                    GifWigglegramEncoder.encodeBitmaps(
                        bitmaps = frames,
                        frameDelayMs = GalleryD3dTimingMath.DEFAULT_FRAME_DELAY_MS,
                        pingPong = true
                    )
                assertTrue(gifBytes.size > 64)
                assertEquals('G'.code.toByte(), gifBytes[0])
                assertEquals('I'.code.toByte(), gifBytes[1])
                assertEquals('F'.code.toByte(), gifBytes[2])
            } finally {
                frames.forEach { it.recycle() }
            }
        } finally {
            source.recycle()
            exporter.close()
        }
    }
}
