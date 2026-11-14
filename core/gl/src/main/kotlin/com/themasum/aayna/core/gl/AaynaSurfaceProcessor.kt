package com.themasum.aayna.core.gl

import android.content.res.AssetManager
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.opengl.Matrix
import android.view.Surface
import androidx.camera.core.CameraEffect
import androidx.camera.core.SurfaceOutput
import androidx.camera.core.SurfaceProcessor
import androidx.camera.core.SurfaceRequest
import androidx.core.util.Consumer
import com.themasum.aayna.core.model.GlDiagnosticsInfo
import com.themasum.aayna.core.model.PassShaderStatus
import java.io.Closeable
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Concrete [CameraEffect] wrapper exposing CameraX's protected `SurfaceProcessor` constructor
 * for `CameraEffect.PREVIEW or CameraEffect.VIDEO_CAPTURE`.
 */
class AaynaCameraEffect(
    targets: Int = PREVIEW or VIDEO_CAPTURE,
    executor: Executor,
    surfaceProcessor: SurfaceProcessor,
    errorListener: Consumer<Throwable> = Consumer { }
) : CameraEffect(targets, executor, surfaceProcessor, errorListener)

/**
 * CameraX [SurfaceProcessor] backed by [AaynaPersistentGlEngine] on `AaynaGLThread`.
 *
 * Supports `CameraEffect.PREVIEW or CameraEffect.VIDEO_CAPTURE` with `EGL_RECORDABLE_ANDROID`
 * and presentation timestamp propagation (`EGLExt.eglPresentationTimeANDROID`) without tearing
 * down the `EGLContext` across pause/navigation.
 */
class AaynaSurfaceProcessor private constructor(
    private val glEngine: AaynaPersistentGlEngine,
    private val ownsEngine: Boolean
) : SurfaceProcessor,
    Closeable {
    constructor(glEngine: AaynaPersistentGlEngine) : this(glEngine = glEngine, ownsEngine = false)
    constructor(assetManager: AssetManager) : this(
        glEngine = AaynaPersistentGlEngine(assetManager),
        ownsEngine = true
    )

    private data class InputEntry(val oesTexId: Int, val surfaceTexture: SurfaceTexture, val surface: Surface)

    private data class OutputEntry(val surfaceOutput: SurfaceOutput, val surface: Surface, val eglSurface: EGLSurface)

    private val paramsRef = AtomicReference<GlRenderParams?>(null)
    private val released = AtomicBoolean(false)

    private val activeInputs = mutableListOf<InputEntry>()
    private val activeOutputs = mutableMapOf<SurfaceOutput, OutputEntry>()
    private val textureTransform = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }
    private val outputTransform = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }

    val glExecutor: Executor
        get() = glEngine.executor

    fun updateParams(params: GlRenderParams) {
        paramsRef.set(params)
    }

    fun getDiagnostics(): Pair<GlDiagnosticsInfo?, List<PassShaderStatus>> = glEngine.getDiagnostics()

    fun createCameraEffect(
        targets: Int = CameraEffect.PREVIEW or CameraEffect.VIDEO_CAPTURE,
        onError: (Throwable) -> Unit = {}
    ): CameraEffect = AaynaCameraEffect(
        targets = targets,
        executor = glEngine.executor,
        surfaceProcessor = this,
        errorListener = Consumer { err -> onError(err) }
    )

    override fun onInputSurface(request: SurfaceRequest) {
        if (released.get() || glEngine.isReleased()) {
            request.willNotProvideSurface()
            return
        }
        val posted =
            glEngine.post {
                if (released.get() || glEngine.isReleased()) {
                    request.willNotProvideSurface()
                    return@post
                }
                glEngine.ensureInitialized()
                glEngine.makeDefaultCurrent()

                val texIds = IntArray(1)
                GLES30.glGenTextures(1, texIds, 0)
                val oesTexId = texIds[0]
                GLES30.glBindTexture(GL_TEXTURE_EXTERNAL_OES, oesTexId)
                GLES30.glTexParameteri(
                    GL_TEXTURE_EXTERNAL_OES,
                    GLES30.GL_TEXTURE_MIN_FILTER,
                    GLES30.GL_LINEAR
                )
                GLES30.glTexParameteri(
                    GL_TEXTURE_EXTERNAL_OES,
                    GLES30.GL_TEXTURE_MAG_FILTER,
                    GLES30.GL_LINEAR
                )
                GLES30.glTexParameteri(
                    GL_TEXTURE_EXTERNAL_OES,
                    GLES30.GL_TEXTURE_WRAP_S,
                    GLES30.GL_CLAMP_TO_EDGE
                )
                GLES30.glTexParameteri(
                    GL_TEXTURE_EXTERNAL_OES,
                    GLES30.GL_TEXTURE_WRAP_T,
                    GLES30.GL_CLAMP_TO_EDGE
                )
                GLES30.glBindTexture(GL_TEXTURE_EXTERNAL_OES, 0)

                val st =
                    SurfaceTexture(oesTexId).apply {
                        setDefaultBufferSize(request.resolution.width, request.resolution.height)
                    }
                val inputSurface = Surface(st)
                val entry = InputEntry(oesTexId = oesTexId, surfaceTexture = st, surface = inputSurface)
                activeInputs += entry

                st.setOnFrameAvailableListener(
                    { readySt -> onFrameAvailableOnGlThread(readySt, entry) },
                    glEngine.handler
                )

                request.provideSurface(inputSurface, glEngine.executor) {
                    releaseInputEntryOnGlThread(entry)
                }
            }
        if (!posted) {
            request.willNotProvideSurface()
        }
    }

    override fun onOutputSurface(surfaceOutput: SurfaceOutput) {
        if (released.get() || glEngine.isReleased()) {
            surfaceOutput.close()
            return
        }
        val posted =
            glEngine.post {
                if (released.get() || glEngine.isReleased()) {
                    surfaceOutput.close()
                    return@post
                }
                glEngine.ensureInitialized()
                glEngine.makeDefaultCurrent()

                val outputSurface =
                    surfaceOutput.getSurface(glEngine.executor) { event ->
                        if (event.eventCode == SurfaceOutput.Event.EVENT_REQUEST_CLOSE) {
                            removeOutputEntryOnGlThread(surfaceOutput)
                        }
                    }

                val eglSurface =
                    runCatching { glEngine.createWindowSurface(outputSurface) }
                        .getOrNull()
                if (eglSurface == null || eglSurface == EGL14.EGL_NO_SURFACE) {
                    surfaceOutput.close()
                    return@post
                }

                activeOutputs[surfaceOutput] =
                    OutputEntry(
                        surfaceOutput = surfaceOutput,
                        surface = outputSurface,
                        eglSurface = eglSurface
                    )
            }
        if (!posted) {
            surfaceOutput.close()
        }
    }

    /**
     * Releases active input/output surfaces on `AaynaGLThread`.
     * Preserves the shared [AaynaPersistentGlEngine] `EGLContext` unless this processor owns it.
     */
    fun release() {
        if (!released.compareAndSet(false, true)) return
        if (!glEngine.isReleased()) {
            runCatching {
                glEngine.runBlocking {
                    releaseSurfacesOnGlThread()
                }
            }
        }
        if (ownsEngine) {
            glEngine.release()
        }
    }

    /**
     * Releases only attached input/output surfaces while keeping the processor reusable
     * across camera rebinds or navigation without tearing down `EGLContext`.
     */
    fun releaseSurfacesOnly() {
        if (released.get() || glEngine.isReleased()) return
        glEngine.post {
            releaseSurfacesOnGlThread()
        }
    }

    override fun close() {
        release()
    }

    private fun onFrameAvailableOnGlThread(st: SurfaceTexture, entry: InputEntry) {
        if (released.get() || glEngine.isReleased()) return
        if (!activeInputs.contains(entry)) return

        glEngine.makeDefaultCurrent()
        st.updateTexImage()
        st.getTransformMatrix(textureTransform)
        val timestampNs = st.timestamp

        val baseParams = paramsRef.get() ?: return
        if (activeOutputs.isEmpty()) return

        val fourPass = glEngine.fourPassEngine
        for (output in activeOutputs.values) {
            output.surfaceOutput.updateTransformMatrix(outputTransform, textureTransform)
            if (!glEngine.makeCurrent(output.eglSurface)) continue

            val size = output.surfaceOutput.size
            val outW = size.width.coerceAtLeast(2)
            val outH = size.height.coerceAtLeast(2)
            val surfaceAspect = outW.toFloat() / outH.toFloat()
            val cropTargetAspect =
                GlMath.computeEffectiveAspectRatio(
                    baseParams.opticalSettings,
                    isPortraitSurface = outH > outW
                )

            val frameParams =
                baseParams.copy(
                    inputAspectRatio = surfaceAspect
                )
            val renderW: Int
            val renderH: Int
            val vpX: Int
            val vpY: Int
            if (abs(cropTargetAspect - surfaceAspect) > 0.005f) {
                if (surfaceAspect > cropTargetAspect) {
                    renderH = outH
                    renderW = (outH * cropTargetAspect).roundToInt().coerceIn(2, outW)
                } else {
                    renderW = outW
                    renderH = (outW / cropTargetAspect).roundToInt().coerceIn(2, outH)
                }
                vpX = (outW - renderW) / 2
                vpY = (outH - renderH) / 2
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
                GLES30.glViewport(0, 0, outW, outH)
                GLES30.glClearColor(0f, 0f, 0f, 1f)
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            } else {
                renderW = outW
                renderH = outH
                vpX = 0
                vpY = 0
            }

            fourPass.render(
                inputTextureId = entry.oesTexId,
                texMatrix = outputTransform,
                targetWidth = renderW,
                targetHeight = renderH,
                outputFboId = 0,
                viewportX = vpX,
                viewportY = vpY,
                viewportWidth = renderW,
                viewportHeight = renderH,
                params = frameParams,
                useOes = true
            )
            glEngine.setPresentationTime(output.eglSurface, timestampNs)
            glEngine.swapBuffers(output.eglSurface)
        }

        glEngine.refreshDiagnostics()
        glEngine.makeDefaultCurrent()
    }

    private fun releaseInputEntryOnGlThread(entry: InputEntry) {
        if (!activeInputs.remove(entry)) return
        glEngine.makeDefaultCurrent()
        entry.surfaceTexture.setOnFrameAvailableListener(null)
        entry.surfaceTexture.release()
        entry.surface.release()
        if (entry.oesTexId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(entry.oesTexId), 0)
        }
    }

    private fun removeOutputEntryOnGlThread(surfaceOutput: SurfaceOutput) {
        val removed = activeOutputs.remove(surfaceOutput) ?: return
        glEngine.destroySurface(removed.eglSurface)
        surfaceOutput.close()
    }

    private fun releaseSurfacesOnGlThread() {
        glEngine.makeDefaultCurrent()
        val inputsCopy = activeInputs.toList()
        inputsCopy.forEach { releaseInputEntryOnGlThread(it) }
        val outputsCopy = activeOutputs.keys.toList()
        outputsCopy.forEach { removeOutputEntryOnGlThread(it) }
    }

    companion object {
        private const val GL_TEXTURE_EXTERNAL_OES = 0x8D65
    }
}
