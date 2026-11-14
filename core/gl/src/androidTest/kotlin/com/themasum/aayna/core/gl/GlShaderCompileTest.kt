package com.themasum.aayna.core.gl

import android.graphics.Bitmap
import android.graphics.Color
import android.opengl.EGL14
import android.opengl.EGLConfig
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.themasum.aayna.core.model.CameraCatalog
import com.themasum.aayna.core.model.CaptureRecipe
import com.themasum.aayna.core.model.FrameBorderStyle
import com.themasum.aayna.core.model.LensAttachment
import com.themasum.aayna.core.model.OpticalSettings
import com.themasum.aayna.core.model.ShaderExposureFallback
import com.themasum.aayna.core.model.StampConfig
import com.themasum.aayna.core.model.StampFontStyle
import com.themasum.aayna.core.model.StampPosition
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GlShaderCompileTest {
    @Test
    fun allFourPassShadersCompileCleanlyUnderEglEs30() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        assertTrue("eglGetDisplay failed", display != EGL14.EGL_NO_DISPLAY)
        val version = IntArray(2)
        assertTrue("eglInitialize failed", EGL14.eglInitialize(display, version, 0, version, 1))

        val configAttribs =
            intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE,
                0x0040, // EGL_OPENGL_ES3_BIT
                EGL14.EGL_SURFACE_TYPE,
                EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE,
                8,
                EGL14.EGL_GREEN_SIZE,
                8,
                EGL14.EGL_BLUE_SIZE,
                8,
                EGL14.EGL_ALPHA_SIZE,
                8,
                EGL14.EGL_NONE
            )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        assertTrue(
            "eglChooseConfig failed",
            EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0) &&
                numConfigs[0] > 0
        )
        val config = requireNotNull(configs[0])

        val ctx =
            EGL14.eglCreateContext(
                display,
                config,
                EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE),
                0
            )
        assertTrue("eglCreateContext failed", ctx != EGL14.EGL_NO_CONTEXT)

        val surface =
            EGL14.eglCreatePbufferSurface(
                display,
                config,
                intArrayOf(EGL14.EGL_WIDTH, 64, EGL14.EGL_HEIGHT, 64, EGL14.EGL_NONE),
                0
            )
        assertTrue("eglCreatePbufferSurface failed", surface != EGL14.EGL_NO_SURFACE)

        val engine =
            AaynaFourPassEngine(
                assetManager = context.assets,
                useOesInput = true,
                compileBothPass1Variants = true
            )
        try {
            assertTrue("eglMakeCurrent failed", EGL14.eglMakeCurrent(display, surface, surface, ctx))
            engine.init()
            val statuses = engine.shaderStatuses
            assertEquals("Expected 8 compiled shader stages", 8, statuses.size)
            for (status in statuses) {
                assertTrue(
                    "Shader '${status.passName}' failed to compile: ${status.errorLog}",
                    status.compiled
                )
            }
        } finally {
            engine.release()
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, ctx)
            EGL14.eglTerminate(display)
        }
    }

    @Test
    fun offscreenExporter_renders2dLutCubeFallbackLensesAndPhotoBooth4Strip() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val exporter = OffscreenGlExporter(context.assets)
        val srcBitmap =
            Bitmap.createBitmap(128, 96, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.rgb(180, 130, 95))
            }
        try {
            val z30 = CameraCatalog.defaultCamera
            val recipe =
                CaptureRecipe(
                    recipeId = "android-test-1",
                    cameraId = z30.id,
                    capturedAtEpochMs = 1_700_000_000_000L,
                    randomSeed = 42L,
                    opticalSettings =
                    OpticalSettings(
                        warmthShift = 0.25f,
                        beautySoftness = 0.5f,
                        highlights = -0.2f,
                        shadows = 0.2f,
                        sharpen = 0.4f,
                        straightenDegrees = 5f,
                        lensAttachment = LensAttachment.PRISM,
                        frameStyle = FrameBorderStyle.DV_TIMECODE
                    ),
                    stampConfig =
                    StampConfig(
                        enabled = true,
                        fontStyle = StampFontStyle.VCR_OSD,
                        position = StampPosition.TOP_LEFT
                    )
                )

            val rendered =
                exporter.renderBitmap(
                    sourceBitmap = srcBitmap,
                    profile = z30,
                    recipe = recipe,
                    stampText = "'24 11 14",
                    fallbackGains = ShaderExposureFallback(0.25f, 0.1f, 0f),
                    maxEdgeOverride = 256
                )
            assertEquals(256, rendered.width)
            assertEquals(192, rendered.height)
            rendered.recycle()

            // Verify .cube fallback path works cleanly
            val cubeProfile =
                z30.copy(
                    lutAssetPath = z30.lutCubeAssetPath ?: "luts/dc_04_ccd.cube"
                )
            val cubeRendered =
                exporter.renderBitmap(
                    sourceBitmap = srcBitmap,
                    profile = cubeProfile,
                    recipe = recipe.copy(
                        opticalSettings = recipe.opticalSettings.copy(
                            lensAttachment = LensAttachment.KALEIDOSCOPE,
                            frameStyle = FrameBorderStyle.INSTANT_WIDE
                        )
                    ),
                    stampText = "",
                    fallbackGains = ShaderExposureFallback(0f, 0f, 0f),
                    maxEdgeOverride = 256,
                    skipStamp = true
                )
            assertNotNull(cubeRendered)
            cubeRendered.recycle()

            // Verify 4-panel photobooth strip rendering
            val frames =
                listOf(
                    Color.rgb(210, 90, 90),
                    Color.rgb(90, 190, 110),
                    Color.rgb(90, 120, 210),
                    Color.rgb(210, 180, 90)
                ).map { c ->
                    Bitmap.createBitmap(96, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(c) }
                }
            try {
                val strip =
                    exporter.renderPhotoBooth4Strip(
                        frames = frames,
                        profile = z30,
                        recipe = recipe,
                        stampText = "'24 11 14",
                        outputSizePx = 512
                    )
                assertEquals(512, strip.width)
                assertEquals(512, strip.height)
                strip.recycle()
            } finally {
                frames.forEach { it.recycle() }
            }
        } finally {
            srcBitmap.recycle()
            exporter.close()
        }
    }
}
