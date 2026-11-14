package com.themasum.aayna.core.gl

import android.content.res.AssetManager
import android.graphics.SurfaceTexture
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.themasum.aayna.core.model.GlDiagnosticsInfo
import com.themasum.aayna.core.model.PassShaderStatus
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.roundToInt

class AaynaViewfinderGlRenderer(
    private val assetManager: AssetManager,
    private val onSurfaceTextureReady: (SurfaceTexture) -> Unit,
    private val onFrameAvailable: () -> Unit
) : GLSurfaceView.Renderer {
    private val paramsRef = AtomicReference<GlRenderParams?>(null)
    private val framePending = AtomicBoolean(false)
    private val diagnosticsRef = AtomicReference<Pair<GlDiagnosticsInfo?, List<PassShaderStatus>>>(null to emptyList())

    private var engine: AaynaFourPassEngine? = null
    private var oesTextureId = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var surfaceWidth = 0
    private var surfaceHeight = 0

    private val texMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }

    fun updateParams(params: GlRenderParams) {
        paramsRef.set(params)
    }

    fun getDiagnostics(): Pair<GlDiagnosticsInfo?, List<PassShaderStatus>> = diagnosticsRef.get()

    fun currentSurfaceTexture(): SurfaceTexture? = surfaceTexture

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Release any previous SurfaceTexture / OES / engine cleanly before recreating
        release()
        framePending.set(false)

        GLES30.glClearColor(0f, 0f, 0f, 1f)

        val texIds = IntArray(1)
        GLES30.glGenTextures(1, texIds, 0)
        oesTextureId = texIds[0]
        GLES30.glBindTexture(GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES30.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glBindTexture(GL_TEXTURE_EXTERNAL_OES, 0)

        val st =
            SurfaceTexture(oesTextureId).apply {
                setOnFrameAvailableListener {
                    framePending.set(true)
                    onFrameAvailable()
                }
            }
        surfaceTexture = st
        onSurfaceTextureReady(st)

        val fourPass =
            AaynaFourPassEngine(
                assetManager = assetManager,
                useOesInput = true
            ).apply {
                init()
            }
        engine = fourPass
        diagnosticsRef.set(fourPass.diagnosticsInfo to fourPass.shaderStatuses)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width.coerceAtLeast(2)
        surfaceHeight = height.coerceAtLeast(2)
        GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        val st = surfaceTexture ?: return
        if (framePending.compareAndSet(true, false)) {
            st.updateTexImage()
        }
        st.getTransformMatrix(texMatrix)

        val params = paramsRef.get() ?: return
        val fourPass = engine ?: return
        if (surfaceWidth <= 0 || surfaceHeight <= 0) return

        val isPortraitSurface = surfaceHeight > surfaceWidth
        val targetRatio = GlMath.computeEffectiveAspectRatio(params.opticalSettings, isPortraitSurface)
        val surfaceRatio = surfaceWidth.toFloat() / surfaceHeight.toFloat()

        val (vpW, vpH) =
            if (surfaceRatio > targetRatio) {
                (surfaceHeight * targetRatio).roundToInt().coerceAtLeast(2) to surfaceHeight
            } else {
                surfaceWidth to (surfaceWidth / targetRatio).roundToInt().coerceAtLeast(2)
            }
        val vpX = (surfaceWidth - vpW) / 2
        val vpY = (surfaceHeight - vpH) / 2

        fourPass.render(
            inputTextureId = oesTextureId,
            texMatrix = texMatrix,
            targetWidth = vpW,
            targetHeight = vpH,
            outputFboId = 0,
            viewportX = vpX,
            viewportY = vpY,
            viewportWidth = vpW,
            viewportHeight = vpH,
            params = params
        )
        diagnosticsRef.set(fourPass.diagnosticsInfo to fourPass.shaderStatuses)
    }

    fun release() {
        surfaceTexture?.setOnFrameAvailableListener(null)
        surfaceTexture?.release()
        surfaceTexture = null
        if (oesTextureId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(oesTextureId), 0)
            oesTextureId = 0
        }
        engine?.release()
        engine = null
    }

    companion object {
        private const val GL_TEXTURE_EXTERNAL_OES = 0x8D65
    }
}
