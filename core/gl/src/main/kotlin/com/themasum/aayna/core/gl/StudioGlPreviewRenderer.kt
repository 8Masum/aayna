package com.themasum.aayna.core.gl

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.view.Surface
import com.themasum.aayna.core.model.GlDiagnosticsInfo
import com.themasum.aayna.core.model.PassShaderStatus
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

/**
 * Persistent Window-Surface Photo Studio Controller running on [AaynaPersistentGlEngine]'s
 * `AaynaGLThread`.
 *
 * Keeps the decoded source [Bitmap] resident in VRAM as a `GL_TEXTURE_2D` and renders directly
 * into an attached window [EGLSurface] (`SurfaceView` or `TextureView`), coalescing rapid slider
 * scrubs so UI adjustments redraw immediately without recreating Pbuffer contexts or leaking bitmaps.
 */
class StudioGlPreviewRenderer private constructor(
    private val glEngine: AaynaPersistentGlEngine,
    private val ownsEngine: Boolean
) : Closeable {
    constructor(glEngine: AaynaPersistentGlEngine) : this(glEngine = glEngine, ownsEngine = false)
    constructor(assetManager: AssetManager) : this(
        glEngine = AaynaPersistentGlEngine(assetManager),
        ownsEngine = true
    )

    private val pendingParamsRef = AtomicReference<GlRenderParams?>(null)
    private val lastParamsRef = AtomicReference<GlRenderParams?>(null)
    private val renderScheduled = AtomicBoolean(false)
    private val released = AtomicBoolean(false)

    private var windowSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var surfaceWidth = 0
    private var surfaceHeight = 0

    private var sourceTexId = 0
    private var sourceWidth = 0
    private var sourceHeight = 0

    fun attachSurface(surface: Surface, width: Int, height: Int) {
        if (released.get() || glEngine.isReleased()) return
        glEngine.post {
            if (released.get() || glEngine.isReleased()) return@post
            glEngine.ensureInitialized()
            destroyWindowSurfaceOnGlThread()
            surfaceWidth = width.coerceAtLeast(2)
            surfaceHeight = height.coerceAtLeast(2)
            windowSurface = glEngine.createWindowSurface(surface)
            lastParamsRef.get()?.let { renderImmediateOnGlThread(it) }
        }
    }

    fun resizeSurface(width: Int, height: Int) {
        if (released.get() || glEngine.isReleased()) return
        glEngine.post {
            if (released.get() || glEngine.isReleased()) return@post
            surfaceWidth = width.coerceAtLeast(2)
            surfaceHeight = height.coerceAtLeast(2)
            lastParamsRef.get()?.let { renderImmediateOnGlThread(it) }
        }
    }

    fun detachSurface() {
        if (glEngine.isReleased()) return
        runCatching {
            glEngine.runBlocking {
                destroyWindowSurfaceOnGlThread()
            }
        }
    }

    fun setSourceBitmap(bitmap: Bitmap, recycleUploadedCopy: Boolean = false) {
        if (released.get() || glEngine.isReleased()) return
        glEngine.post {
            if (released.get() || glEngine.isReleased()) return@post
            glEngine.ensureInitialized()
            glEngine.makeDefaultCurrent()
            deleteSourceTextureOnGlThread()

            sourceWidth = bitmap.width.coerceAtLeast(1)
            sourceHeight = bitmap.height.coerceAtLeast(1)
            sourceTexId = AssetMetadataLoader.uploadTexture2D(bitmap, recycleBitmap = recycleUploadedCopy)
            lastParamsRef.get()?.let { renderImmediateOnGlThread(it) }
        }
    }

    fun clearSourceBitmap() {
        if (glEngine.isReleased()) return
        glEngine.post {
            glEngine.makeDefaultCurrent()
            deleteSourceTextureOnGlThread()
        }
    }

    /**
     * Requests a coalesced preview redraw for [params]. Rapid slider scrub updates replace
     * any pending params before execution so only the latest state renders.
     */
    fun requestRender(params: GlRenderParams) {
        if (released.get() || glEngine.isReleased()) return
        lastParamsRef.set(params)
        pendingParamsRef.set(params)
        if (renderScheduled.compareAndSet(false, true)) {
            val posted =
                glEngine.post {
                    renderScheduled.set(false)
                    val nextParams = pendingParamsRef.getAndSet(null) ?: return@post
                    renderImmediateOnGlThread(nextParams)
                }
            if (!posted) {
                renderScheduled.set(false)
            }
        }
    }

    fun getDiagnostics(): Pair<GlDiagnosticsInfo?, List<PassShaderStatus>> = glEngine.getDiagnostics()

    fun release() {
        if (!released.compareAndSet(false, true)) return
        pendingParamsRef.set(null)
        lastParamsRef.set(null)
        if (!glEngine.isReleased()) {
            runCatching {
                glEngine.runBlocking {
                    glEngine.makeDefaultCurrent()
                    deleteSourceTextureOnGlThread()
                    destroyWindowSurfaceOnGlThread()
                }
            }
        }
        if (ownsEngine) {
            glEngine.release()
        }
    }

    override fun close() {
        release()
    }

    private fun renderImmediateOnGlThread(params: GlRenderParams) {
        if (released.get() || glEngine.isReleased()) return
        if (windowSurface == EGL14.EGL_NO_SURFACE || sourceTexId == 0) return
        if (surfaceWidth <= 0 || surfaceHeight <= 0) return
        if (!glEngine.makeCurrent(windowSurface)) return

        val (rotW, rotH) =
            GlMath.computeRotatedDimensions(
                width = sourceWidth,
                height = sourceHeight,
                rotationDegrees = params.opticalSettings.rotationDegrees
            )
        val isPortrait = rotH > rotW
        val inputAspect = rotW.toFloat() / rotH.coerceAtLeast(1).toFloat()
        val targetRatio =
            if (params.isPreComposited && params.opticalSettings.cropAspectRatio == null) {
                inputAspect
            } else {
                GlMath.computeEffectiveAspectRatio(params.opticalSettings, isPortrait)
            }
        val surfaceRatio = surfaceWidth.toFloat() / surfaceHeight.toFloat()

        val (vpW, vpH) =
            if (surfaceRatio > targetRatio) {
                (surfaceHeight * targetRatio).roundToInt().coerceIn(2, surfaceWidth) to surfaceHeight
            } else {
                surfaceWidth to (surfaceWidth / targetRatio).roundToInt().coerceIn(2, surfaceHeight)
            }
        val vpX = (surfaceWidth - vpW) / 2
        val vpY = (surfaceHeight - vpH) / 2

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        val effectiveParams = params.copy(inputAspectRatio = inputAspect)
        val texMatrix = GlMath.computeBitmapTexMatrix(params.opticalSettings.rotationDegrees)

        glEngine.fourPassEngine.render(
            inputTextureId = sourceTexId,
            texMatrix = texMatrix,
            targetWidth = vpW,
            targetHeight = vpH,
            outputFboId = 0,
            viewportX = vpX,
            viewportY = vpY,
            viewportWidth = vpW,
            viewportHeight = vpH,
            params = effectiveParams,
            useOes = false
        )
        glEngine.swapBuffers(windowSurface)
        glEngine.refreshDiagnostics()
    }

    private fun deleteSourceTextureOnGlThread() {
        if (sourceTexId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(sourceTexId), 0)
            sourceTexId = 0
        }
        sourceWidth = 0
        sourceHeight = 0
    }

    private fun destroyWindowSurfaceOnGlThread() {
        if (windowSurface != EGL14.EGL_NO_SURFACE) {
            glEngine.destroySurface(windowSurface)
            windowSurface = EGL14.EGL_NO_SURFACE
        }
    }
}
