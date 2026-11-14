package com.themasum.aayna.core.gl

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES30
import com.themasum.aayna.core.model.CameraProfile
import com.themasum.aayna.core.model.CaptureRecipe
import com.themasum.aayna.core.model.FrameBorderStyle
import com.themasum.aayna.core.model.GlDiagnosticsInfo
import com.themasum.aayna.core.model.PassShaderStatus
import com.themasum.aayna.core.model.ResolutionPreset
import com.themasum.aayna.core.model.ShaderExposureFallback
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

class OffscreenGlExporter private constructor(
    private val assetManager: AssetManager,
    private val sharedGlEngine: AaynaPersistentGlEngine?
) : Closeable {
    constructor(assetManager: AssetManager) : this(assetManager = assetManager, sharedGlEngine = null)
    constructor(glEngine: AaynaPersistentGlEngine) : this(
        assetManager = glEngine.assetManager,
        sharedGlEngine = glEngine
    )

    private val ownExecutor =
        if (sharedGlEngine == null) {
            Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "AaynaOffscreenGL").apply { isDaemon = true }
            }
        } else {
            null
        }
    private val ownDispatcher: ExecutorCoroutineDispatcher? = ownExecutor?.asCoroutineDispatcher()
    private val sharedDispatcher: CoroutineDispatcher? = sharedGlEngine?.executor?.asCoroutineDispatcher()
    private val glDispatcher: CoroutineDispatcher =
        checkNotNull(sharedDispatcher ?: ownDispatcher)

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var ownEngine: AaynaFourPassEngine? = null
    private var initialized = false
    private val closed = AtomicBoolean(false)

    // Reusable persistent output FBOs (reallocated only when dimensions change)
    private var cachedOutputFboId = 0
    private var cachedOutputTexId = 0
    private var cachedOutputW = 0
    private var cachedOutputH = 0

    private var cachedCompositeFboId = 0
    private var cachedCompositeTexId = 0
    private var cachedCompositeW = 0
    private var cachedCompositeH = 0

    private var readbackRawBuffer: ByteBuffer? = null
    private var readbackFlippedBuffer: ByteBuffer? = null
    private var readbackRowBytes: ByteArray? = null
    private var readbackCapacityBytes = 0

    suspend fun renderBitmap(
        sourceBitmap: Bitmap,
        profile: CameraProfile,
        recipe: CaptureRecipe,
        stampText: String,
        fallbackGains: ShaderExposureFallback,
        maxEdgeOverride: Int? = null,
        splitCompareRatio: Float = 0f,
        isHoldOriginal: Boolean = false,
        skipStamp: Boolean = false
    ): Bitmap = withContext(glDispatcher) {
        renderBitmapBlocking(
            sourceBitmap = sourceBitmap,
            profile = profile,
            recipe = recipe,
            stampText = stampText,
            fallbackGains = fallbackGains,
            maxEdgeOverride = maxEdgeOverride,
            splitCompareRatio = splitCompareRatio,
            isHoldOriginal = isHoldOriginal,
            skipStamp = skipStamp
        )
    }

    /**
     * Renders up to 4 frames into the 4 panels of `photo_booth_4strip_frame` (`grunge_meta.json`),
     * composites the photobooth strip frame + optional date stamp, and returns the ARGB_8888 [Bitmap].
     */
    suspend fun renderPhotoBooth4Strip(
        frames: List<Bitmap>,
        profile: CameraProfile,
        recipe: CaptureRecipe,
        stampText: String = "",
        fallbackGains: ShaderExposureFallback = ShaderExposureFallback(0f, 0f, 0f),
        outputSizePx: Int = DEFAULT_PHOTOBOOTH_SIZE_PX,
        skipStamp: Boolean = false
    ): Bitmap = withContext(glDispatcher) {
        renderPhotoBooth4StripBlocking(
            frames = frames,
            profile = profile,
            recipe = recipe,
            stampText = stampText,
            fallbackGains = fallbackGains,
            outputSizePx = outputSizePx,
            skipStamp = skipStamp
        )
    }

    fun getDiagnostics(): Pair<GlDiagnosticsInfo?, List<PassShaderStatus>> = sharedGlEngine?.getDiagnostics()
        ?: ownEngine?.let { it.diagnosticsInfo to it.shaderStatuses }
        ?: (null to emptyList())

    private fun renderBitmapBlocking(
        sourceBitmap: Bitmap,
        profile: CameraProfile,
        recipe: CaptureRecipe,
        stampText: String,
        fallbackGains: ShaderExposureFallback,
        maxEdgeOverride: Int?,
        splitCompareRatio: Float,
        isHoldOriginal: Boolean,
        skipStamp: Boolean
    ): Bitmap {
        val fourPass = ensureWorkerAndEngine()

        val maxTex = fourPass.getMaxTextureSize()
        val (outWidth, outHeight) =
            GlMath.computeExportDimensions(
                opticalSettings = recipe.opticalSettings,
                resolutionPreset = recipe.resolutionPreset,
                sourceWidth = sourceBitmap.width,
                sourceHeight = sourceBitmap.height,
                maxTexSize = maxTex,
                maxEdgeOverride = maxEdgeOverride,
                isPreComposited = recipe.isPreComposited
            )
        val (rotW, rotH) =
            GlMath.computeRotatedDimensions(
                width = sourceBitmap.width,
                height = sourceBitmap.height,
                rotationDegrees = recipe.opticalSettings.rotationDegrees
            )
        val inputAspect = rotW.toFloat() / rotH.coerceAtLeast(1).toFloat()
        val texMatrix = GlMath.computeBitmapTexMatrix(recipe.opticalSettings.rotationDegrees)
        val effectiveGains =
            fallbackGains.copy(
                shaderNoiseBoost =
                GlMath.computeEffectiveNoiseBoost(
                    fallbackNoiseBoost = fallbackGains.shaderNoiseBoost,
                    grainBoost = recipe.opticalSettings.grainBoost
                )
            )

        val inputTexId = AssetMetadataLoader.uploadTexture2D(sourceBitmap, recycleBitmap = false)
        val (outputFboId, _) = ensureCachedOutputFbo(outWidth, outHeight)

        try {
            val params =
                GlRenderParams(
                    profile = profile,
                    opticalSettings = recipe.opticalSettings,
                    stampConfig = recipe.stampConfig,
                    stampText = stampText,
                    fallbackGains = effectiveGains,
                    isPreviewHoldOriginal = isHoldOriginal,
                    splitCompareRatio = splitCompareRatio,
                    randomSeed = recipe.randomSeed,
                    inputAspectRatio = inputAspect,
                    skipStamp = skipStamp || recipe.isPreComposited,
                    isPreComposited = recipe.isPreComposited
                )

            fourPass.render(
                inputTextureId = inputTexId,
                texMatrix = texMatrix,
                targetWidth = outWidth,
                targetHeight = outHeight,
                outputFboId = outputFboId,
                params = params,
                useOes = false
            )

            return readFlippedBitmap(outputFboId, outWidth, outHeight)
        } finally {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glDeleteTextures(1, intArrayOf(inputTexId), 0)
        }
    }

    private fun renderPhotoBooth4StripBlocking(
        frames: List<Bitmap>,
        profile: CameraProfile,
        recipe: CaptureRecipe,
        stampText: String,
        fallbackGains: ShaderExposureFallback,
        outputSizePx: Int,
        skipStamp: Boolean
    ): Bitmap {
        require(frames.isNotEmpty()) { "renderPhotoBooth4Strip requires at least 1 frame" }
        val fourPass = ensureWorkerAndEngine()
        val maxTex = fourPass.getMaxTextureSize()
        val frameMeta = fourPass.getGrungeMeta()?.frames?.get(PHOTO_BOOTH_FRAME_KEY)

        val baseFrameW = frameMeta?.widthPx ?: DEFAULT_PHOTOBOOTH_SIZE_PX
        val baseFrameH = frameMeta?.heightPx ?: DEFAULT_PHOTOBOOTH_SIZE_PX
        val targetLong = outputSizePx.coerceIn(128, minOf(maxTex, ResolutionPreset.MAX_FBO_EDGE_PX))
        val outWidth: Int
        val outHeight: Int
        if (baseFrameW >= baseFrameH) {
            outWidth = ((targetLong / 2) * 2).coerceAtLeast(2)
            outHeight = (((outWidth.toDouble() * baseFrameH / baseFrameW).roundToInt()) / 2 * 2).coerceAtLeast(2)
        } else {
            outHeight = ((targetLong / 2) * 2).coerceAtLeast(2)
            outWidth = (((outHeight.toDouble() * baseFrameW / baseFrameH).roundToInt()) / 2 * 2).coerceAtLeast(2)
        }

        val (compositeFboId, compositeTexId) = ensureCachedCompositeFbo(outWidth, outHeight)
        val (outputFboId, _) = ensureCachedOutputFbo(outWidth, outHeight)

        val panelRects =
            frameMeta?.panels?.takeIf { it.size >= 4 } ?: DEFAULT_PHOTOBOOTH_PANELS
        val uploadedByIdentity = IdentityHashMap<Bitmap, Int>()

        try {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, compositeFboId)
            GLES30.glViewport(0, 0, outWidth, outHeight)
            GLES30.glClearColor(0f, 0f, 0f, 1f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

            val panelOptical =
                recipe.opticalSettings.copy(
                    frameStyle = FrameBorderStyle.NONE,
                    cropAspectRatio = null
                )
            val panelTexMatrix = GlMath.computeBitmapTexMatrix(recipe.opticalSettings.rotationDegrees)
            val effectiveGains =
                fallbackGains.copy(
                    shaderNoiseBoost =
                    GlMath.computeEffectiveNoiseBoost(
                        fallbackNoiseBoost = fallbackGains.shaderNoiseBoost,
                        grainBoost = recipe.opticalSettings.grainBoost
                    )
                )

            for (i in 0 until 4) {
                val srcBitmap = frames[i % frames.size]
                val inputTexId =
                    uploadedByIdentity.getOrPut(srcBitmap) {
                        AssetMetadataLoader.uploadTexture2D(srcBitmap, recycleBitmap = false)
                    }
                val vp =
                    GlMath.computeFramePanelViewport(
                        panelPx = panelRects[i],
                        frameWidthPx = baseFrameW,
                        frameHeightPx = baseFrameH,
                        outputWidth = outWidth,
                        outputHeight = outHeight
                    )
                val (rotW, rotH) =
                    GlMath.computeRotatedDimensions(
                        width = srcBitmap.width,
                        height = srcBitmap.height,
                        rotationDegrees = recipe.opticalSettings.rotationDegrees
                    )
                val panelParams =
                    GlRenderParams(
                        profile = profile,
                        opticalSettings = panelOptical,
                        stampConfig = recipe.stampConfig,
                        stampText = "",
                        fallbackGains = effectiveGains,
                        randomSeed = recipe.randomSeed + i,
                        inputAspectRatio = rotW.toFloat() / rotH.coerceAtLeast(1).toFloat(),
                        skipStamp = true
                    )

                fourPass.render(
                    inputTextureId = inputTexId,
                    texMatrix = panelTexMatrix,
                    targetWidth = vp.width,
                    targetHeight = vp.height,
                    outputFboId = compositeFboId,
                    viewportX = vp.x,
                    viewportY = vp.y,
                    viewportWidth = vp.width,
                    viewportHeight = vp.height,
                    params = panelParams,
                    useOes = false
                )
            }

            val overlayParams =
                GlRenderParams(
                    profile = profile,
                    opticalSettings = recipe.opticalSettings,
                    stampConfig = recipe.stampConfig,
                    stampText = stampText,
                    fallbackGains = effectiveGains,
                    randomSeed = recipe.randomSeed,
                    skipStamp = skipStamp
                )
            fourPass.renderFrameOverlay(
                baseTextureId = compositeTexId,
                frameKey = PHOTO_BOOTH_FRAME_KEY,
                outputFboId = outputFboId,
                width = outWidth,
                height = outHeight,
                params = overlayParams
            )

            return readFlippedBitmap(outputFboId, outWidth, outHeight)
        } finally {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            if (uploadedByIdentity.isNotEmpty()) {
                val ids = uploadedByIdentity.values.toIntArray()
                GLES30.glDeleteTextures(ids.size, ids, 0)
            }
        }
    }

    private fun ensureWorkerAndEngine(): AaynaFourPassEngine {
        val shared = sharedGlEngine
        if (shared != null) {
            shared.ensureInitialized()
            shared.makeDefaultCurrent()
            return shared.fourPassEngine
        }
        ensureOwnEglInitialized()
        return checkNotNull(ownEngine) { "OffscreenGlExporter engine not initialized" }
    }

    private fun ensureOwnEglInitialized() {
        if (initialized) {
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
            return
        }

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

        ownEngine =
            AaynaFourPassEngine(
                assetManager = assetManager,
                useOesInput = false
            ).apply {
                init()
            }
        initialized = true
    }

    private fun ensureCachedOutputFbo(width: Int, height: Int): Pair<Int, Int> {
        if (cachedOutputFboId != 0 && cachedOutputW == width && cachedOutputH == height) {
            return cachedOutputFboId to cachedOutputTexId
        }
        releaseCachedOutputFbo()
        val (fboId, texId) = createRgba8Fbo(width, height)
        cachedOutputFboId = fboId
        cachedOutputTexId = texId
        cachedOutputW = width
        cachedOutputH = height
        return fboId to texId
    }

    private fun ensureCachedCompositeFbo(width: Int, height: Int): Pair<Int, Int> {
        if (cachedCompositeFboId != 0 && cachedCompositeW == width && cachedCompositeH == height) {
            return cachedCompositeFboId to cachedCompositeTexId
        }
        releaseCachedCompositeFbo()
        val (fboId, texId) = createRgba8Fbo(width, height)
        cachedCompositeFboId = fboId
        cachedCompositeTexId = texId
        cachedCompositeW = width
        cachedCompositeH = height
        return fboId to texId
    }

    private fun createRgba8Fbo(width: Int, height: Int): Pair<Int, Int> {
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
        val totalBytes = rowStride * height
        if (readbackCapacityBytes < totalBytes) {
            readbackRawBuffer = ByteBuffer.allocateDirect(totalBytes).order(ByteOrder.nativeOrder())
            readbackFlippedBuffer = ByteBuffer.allocateDirect(totalBytes).order(ByteOrder.nativeOrder())
            readbackCapacityBytes = totalBytes
        }
        val rowBytes =
            readbackRowBytes?.takeIf { it.size >= rowStride }
                ?: ByteArray(rowStride).also { readbackRowBytes = it }

        val rawBuffer = checkNotNull(readbackRawBuffer).apply { clear() }
        val flippedBuffer = checkNotNull(readbackFlippedBuffer).apply { clear() }

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
        GLES30.glPixelStorei(GLES30.GL_PACK_ALIGNMENT, 4)
        GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, rawBuffer)

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

    private fun releaseCachedOutputFbo() {
        if (cachedOutputFboId != 0) {
            GLES30.glDeleteFramebuffers(1, intArrayOf(cachedOutputFboId), 0)
            cachedOutputFboId = 0
        }
        if (cachedOutputTexId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(cachedOutputTexId), 0)
            cachedOutputTexId = 0
        }
        cachedOutputW = 0
        cachedOutputH = 0
    }

    private fun releaseCachedCompositeFbo() {
        if (cachedCompositeFboId != 0) {
            GLES30.glDeleteFramebuffers(1, intArrayOf(cachedCompositeFboId), 0)
            cachedCompositeFboId = 0
        }
        if (cachedCompositeTexId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(cachedCompositeTexId), 0)
            cachedCompositeTexId = 0
        }
        cachedCompositeW = 0
        cachedCompositeH = 0
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val shared = sharedGlEngine
        if (shared != null) {
            if (!shared.isReleased()) {
                runCatching {
                    shared.runBlocking {
                        shared.makeDefaultCurrent()
                        releaseCachedOutputFbo()
                        releaseCachedCompositeFbo()
                    }
                }
            }
        } else {
            ownExecutor?.execute {
                if (initialized && eglDisplay != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
                }
                releaseCachedOutputFbo()
                releaseCachedCompositeFbo()
                ownEngine?.release()
                ownEngine = null
                if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(
                        eglDisplay,
                        EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_CONTEXT
                    )
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
            ownDispatcher?.close()
            ownExecutor?.shutdown()
        }
        readbackRawBuffer = null
        readbackFlippedBuffer = null
        readbackRowBytes = null
        readbackCapacityBytes = 0
    }

    private class IdentityHashMap<K : Any, V> : java.util.IdentityHashMap<K, V>()

    companion object {
        private const val EGL_OPENGL_ES3_BIT = 0x0040
        private const val DEFAULT_PHOTOBOOTH_SIZE_PX = 1024
        private const val PHOTO_BOOTH_FRAME_KEY = "photo_booth_4strip_frame"
        private val DEFAULT_PHOTOBOOTH_PANELS =
            listOf(
                listOf(252, 44, 772, 244),
                listOf(252, 268, 772, 468),
                listOf(252, 492, 772, 692),
                listOf(252, 716, 772, 916)
            )
    }
}
