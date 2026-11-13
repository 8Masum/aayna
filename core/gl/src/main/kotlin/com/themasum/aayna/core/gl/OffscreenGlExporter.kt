package com.themasum.aayna.core.gl

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.opengl.Matrix
import com.themasum.aayna.core.model.CameraProfile
import com.themasum.aayna.core.model.CaptureRecipe
import com.themasum.aayna.core.model.GlDiagnosticsInfo
import com.themasum.aayna.core.model.PassShaderStatus
import com.themasum.aayna.core.model.ResolutionPreset
import com.themasum.aayna.core.model.ShaderExposureFallback
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

class OffscreenGlExporter(private val assetManager: AssetManager) : Closeable {
    private val executor =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "AaynaOffscreenGL").apply { isDaemon = true }
        }
    private val glDispatcher: ExecutorCoroutineDispatcher = executor.asCoroutineDispatcher()

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var engine: AaynaFourPassEngine? = null
    private var initialized = false

    private val bitmapTexMatrix =
        FloatArray(16).apply {
            Matrix.setIdentityM(this, 0)
            Matrix.translateM(this, 0, 0f, 1f, 0f)
            Matrix.scaleM(this, 0, 1f, -1f, 1f)
        }

    suspend fun renderBitmap(
        sourceBitmap: Bitmap,
        profile: CameraProfile,
        recipe: CaptureRecipe,
        stampText: String,
        fallbackGains: ShaderExposureFallback,
        maxEdgeOverride: Int? = null,
        splitCompareRatio: Float = 0f,
        isHoldOriginal: Boolean = false
    ): Bitmap = withContext(glDispatcher) {
        renderBitmapBlocking(
            sourceBitmap = sourceBitmap,
            profile = profile,
            recipe = recipe,
            stampText = stampText,
            fallbackGains = fallbackGains,
            maxEdgeOverride = maxEdgeOverride,
            splitCompareRatio = splitCompareRatio,
            isHoldOriginal = isHoldOriginal
        )
    }

    fun getDiagnostics(): Pair<GlDiagnosticsInfo?, List<PassShaderStatus>> =
        engine?.let { it.diagnosticsInfo to it.shaderStatuses } ?: (null to emptyList())

    private fun renderBitmapBlocking(
        sourceBitmap: Bitmap,
        profile: CameraProfile,
        recipe: CaptureRecipe,
        stampText: String,
        fallbackGains: ShaderExposureFallback,
        maxEdgeOverride: Int?,
        splitCompareRatio: Float,
        isHoldOriginal: Boolean
    ): Bitmap {
        ensureEglInitialized()
        val fourPass = checkNotNull(engine) { "OffscreenGlExporter engine not initialized" }

        val aspect = recipe.opticalSettings.aspectRatio
        val maxTex = fourPass.getMaxTextureSize()
        val (longEdge, shortEdge) =
            if (maxEdgeOverride != null) {
                val safeLong = ((maxEdgeOverride.coerceIn(64, minOf(maxTex, ResolutionPreset.MAX_FBO_EDGE_PX)) / 2) * 2)
                val rawShort = (safeLong.toDouble() * aspect.heightRatio / aspect.widthRatio).roundToInt()
                val safeShort = ((rawShort / 2) * 2).coerceIn(2, safeLong)
                safeLong to safeShort
            } else {
                recipe.resolutionPreset.computeTargetDimensions(aspect, maxTex)
            }

        val isPortrait = sourceBitmap.height > sourceBitmap.width
        val outWidth = if (isPortrait) shortEdge else longEdge
        val outHeight = if (isPortrait) longEdge else shortEdge

        val inputTexId = AssetMetadataLoader.uploadTexture2D(sourceBitmap, recycleBitmap = false)
        val (outputFboId, outputTexId) = createOutputFbo(outWidth, outHeight)

        try {
            val params =
                GlRenderParams(
                    profile = profile,
                    opticalSettings = recipe.opticalSettings,
                    stampConfig = recipe.stampConfig,
                    stampText = stampText,
                    fallbackGains = fallbackGains,
                    isPreviewHoldOriginal = isHoldOriginal,
                    splitCompareRatio = splitCompareRatio,
                    randomSeed = recipe.randomSeed,
                    inputAspectRatio = sourceBitmap.width.toFloat() / sourceBitmap.height.coerceAtLeast(1).toFloat()
                )

            fourPass.render(
                inputTextureId = inputTexId,
                texMatrix = bitmapTexMatrix,
                targetWidth = outWidth,
                targetHeight = outHeight,
                outputFboId = outputFboId,
                params = params
            )

            return readFlippedBitmap(outputFboId, outWidth, outHeight)
        } finally {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glDeleteTextures(2, intArrayOf(inputTexId, outputTexId), 0)
            GLES30.glDeleteFramebuffers(1, intArrayOf(outputFboId), 0)
        }
    }

    private fun ensureEglInitialized() {
        if (initialized) return

        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(eglDisplay != EGL14.EGL_NO_DISPLAY) { "eglGetDisplay failed" }

        val version = IntArray(2)
        check(EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) { "eglInitialize failed" }

        val configAttribs =
            intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE,
                EGL_OPENGL_ES3_BIT,
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
        check(EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0) && numConfigs[0] > 0) {
            "eglChooseConfig failed for ES 3.0"
        }
        val eglConfig = checkNotNull(configs[0])

        val contextAttribs =
            intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION,
                3,
                EGL14.EGL_NONE
            )
        eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        check(eglContext != EGL14.EGL_NO_CONTEXT) { "eglCreateContext ES 3.0 failed" }

        val pbufferAttribs =
            intArrayOf(
                EGL14.EGL_WIDTH,
                1,
                EGL14.EGL_HEIGHT,
                1,
                EGL14.EGL_NONE
            )
        eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig, pbufferAttribs, 0)
        check(eglSurface != EGL14.EGL_NO_SURFACE) { "eglCreatePbufferSurface failed" }

        check(EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            "eglMakeCurrent failed"
        }

        engine =
            AaynaFourPassEngine(
                assetManager = assetManager,
                useOesInput = false
            ).apply {
                init()
            }
        initialized = true
    }

    private fun createOutputFbo(width: Int, height: Int): Pair<Int, Int> {
        val texIds = IntArray(1)
        GLES30.glGenTextures(1, texIds, 0)
        val texId = texIds[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D,
            0,
            GLES30.GL_RGBA8,
            width,
            height,
            0,
            GLES30.GL_RGBA,
            GLES30.GL_UNSIGNED_BYTE,
            null
        )

        val fboIds = IntArray(1)
        GLES30.glGenFramebuffers(1, fboIds, 0)
        val fboId = fboIds[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER,
            GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D,
            texId,
            0
        )
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        check(status == GLES30.GL_FRAMEBUFFER_COMPLETE) {
            "Offscreen output FBO incomplete: 0x${status.toString(16)}"
        }
        return fboId to texId
    }

    private fun readFlippedBitmap(fboId: Int, width: Int, height: Int): Bitmap {
        val rowStride = width * 4
        val rawBuffer =
            ByteBuffer
                .allocateDirect(rowStride * height)
                .order(ByteOrder.nativeOrder())
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
        GLES30.glPixelStorei(GLES30.GL_PACK_ALIGNMENT, 4)
        GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, rawBuffer)

        val flippedBuffer =
            ByteBuffer
                .allocateDirect(rowStride * height)
                .order(ByteOrder.nativeOrder())
        val rowBytes = ByteArray(rowStride)
        for (y in 0 until height) {
            val srcOffset = (height - 1 - y) * rowStride
            rawBuffer.position(srcOffset)
            rawBuffer.get(rowBytes, 0, rowStride)
            flippedBuffer.put(rowBytes, 0, rowStride)
        }
        flippedBuffer.position(0)

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            copyPixelsFromBuffer(flippedBuffer)
        }
    }

    override fun close() {
        executor.execute {
            engine?.release()
            engine = null
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (eglSurface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(eglDisplay, eglSurface)
                    eglSurface = EGL14.EGL_NO_SURFACE
                }
                if (eglContext != EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroyContext(eglDisplay, eglContext)
                    eglContext = EGL14.EGL_NO_CONTEXT
                }
                EGL14.eglTerminate(eglDisplay)
                eglDisplay = EGL14.EGL_NO_DISPLAY
            }
            initialized = false
        }
        glDispatcher.close()
        executor.shutdown()
    }

    companion object {
        private const val EGL_OPENGL_ES3_BIT = 0x0040
    }
}
