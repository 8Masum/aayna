package com.themasum.aayna.core.gl

import android.content.res.AssetManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.os.Handler
import android.view.Surface
import com.themasum.aayna.core.model.GlDiagnosticsInfo
import com.themasum.aayna.core.model.PassShaderStatus
import java.io.Closeable
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Persistent app-scoped EGL14 ES 3.0 engine running on a single dedicated [GLHandlerThread]
 * ("AaynaGLThread") configured with `EGL_RECORDABLE_ANDROID`.
 *
 * Keeps the `EGLContext`, compiled 4-pass shader programs, LUT cache, preloaded grunge textures,
 * and stamp atlases resident across pause/navigation without context teardown.
 */
class AaynaPersistentGlEngine(internal val assetManager: AssetManager) : Closeable {
    private val glThread = GLHandlerThread(GLHandlerThread.DEFAULT_THREAD_NAME)
    private val released = AtomicBoolean(false)
    private val diagnosticsRef =
        AtomicReference<Pair<GlDiagnosticsInfo?, List<PassShaderStatus>>>(null to emptyList())

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglConfig: EGLConfig? = null
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var defaultPbufferSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var engineInternal: AaynaFourPassEngine? = null
    private var initialized = false

    val handler: Handler
        get() = glThread.handler

    val executor: Executor
        get() = glThread.executor

    val fourPassEngine: AaynaFourPassEngine
        get() {
            check(glThread.isOnGlThread()) { "fourPassEngine must be accessed on AaynaGLThread" }
            ensureInitialized()
            return checkNotNull(engineInternal)
        }

    fun isOnGlThread(): Boolean = glThread.isOnGlThread()

    fun isReleased(): Boolean = released.get()

    fun post(block: () -> Unit): Boolean {
        if (released.get()) return false
        return glThread.post(block)
    }

    fun <T> runBlocking(block: () -> T): T {
        check(!released.get()) { "AaynaPersistentGlEngine has been released" }
        return glThread.runBlocking(block)
    }

    fun ensureInitialized() {
        check(glThread.isOnGlThread()) { "ensureInitialized() must be called on AaynaGLThread" }
        if (initialized || released.get()) return

        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(eglDisplay != EGL14.EGL_NO_DISPLAY) { "eglGetDisplay failed" }

        val version = IntArray(2)
        check(EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) { "eglInitialize failed" }

        val selectedConfig = chooseEglConfig(eglDisplay)
        eglConfig = selectedConfig

        val contextAttribs =
            intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION,
                3,
                EGL14.EGL_NONE
            )
        eglContext = EGL14.eglCreateContext(eglDisplay, selectedConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        check(eglContext != EGL14.EGL_NO_CONTEXT) { "eglCreateContext ES 3.0 failed" }

        val pbufferAttribs =
            intArrayOf(
                EGL14.EGL_WIDTH,
                1,
                EGL14.EGL_HEIGHT,
                1,
                EGL14.EGL_NONE
            )
        defaultPbufferSurface = EGL14.eglCreatePbufferSurface(eglDisplay, selectedConfig, pbufferAttribs, 0)
        check(defaultPbufferSurface != EGL14.EGL_NO_SURFACE) { "eglCreatePbufferSurface failed" }

        check(EGL14.eglMakeCurrent(eglDisplay, defaultPbufferSurface, defaultPbufferSurface, eglContext)) {
            "eglMakeCurrent failed for persistent Pbuffer"
        }

        val engine =
            AaynaFourPassEngine(
                assetManager = assetManager,
                useOesInput = true,
                compileBothPass1Variants = true
            ).apply {
                init()
            }
        engineInternal = engine
        diagnosticsRef.set(engine.diagnosticsInfo to engine.shaderStatuses)
        initialized = true
    }

    fun createWindowSurface(surface: Surface): EGLSurface {
        check(glThread.isOnGlThread()) { "createWindowSurface must be called on AaynaGLThread" }
        ensureInitialized()
        val config = checkNotNull(eglConfig) { "EGLConfig not initialized" }
        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        val windowSurface = EGL14.eglCreateWindowSurface(eglDisplay, config, surface, surfaceAttribs, 0)
        check(windowSurface != EGL14.EGL_NO_SURFACE) {
            "eglCreateWindowSurface failed: 0x${EGL14.eglGetError().toString(16)}"
        }
        return windowSurface
    }

    fun makeCurrent(surface: EGLSurface): Boolean {
        check(glThread.isOnGlThread()) { "makeCurrent must be called on AaynaGLThread" }
        if (!initialized || eglDisplay == EGL14.EGL_NO_DISPLAY || eglContext == EGL14.EGL_NO_CONTEXT) {
            return false
        }
        return EGL14.eglMakeCurrent(eglDisplay, surface, surface, eglContext)
    }

    fun makeDefaultCurrent(): Boolean {
        check(glThread.isOnGlThread()) { "makeDefaultCurrent must be called on AaynaGLThread" }
        if (!initialized || defaultPbufferSurface == EGL14.EGL_NO_SURFACE) return false
        return EGL14.eglMakeCurrent(eglDisplay, defaultPbufferSurface, defaultPbufferSurface, eglContext)
    }

    fun setPresentationTime(surface: EGLSurface, timestampNs: Long) {
        check(glThread.isOnGlThread()) { "setPresentationTime must be called on AaynaGLThread" }
        if (initialized && eglDisplay != EGL14.EGL_NO_DISPLAY && surface != EGL14.EGL_NO_SURFACE) {
            EGLExt.eglPresentationTimeANDROID(eglDisplay, surface, timestampNs)
        }
    }

    fun swapBuffers(surface: EGLSurface): Boolean {
        check(glThread.isOnGlThread()) { "swapBuffers must be called on AaynaGLThread" }
        if (!initialized || eglDisplay == EGL14.EGL_NO_DISPLAY || surface == EGL14.EGL_NO_SURFACE) {
            return false
        }
        return EGL14.eglSwapBuffers(eglDisplay, surface)
    }

    fun destroySurface(surface: EGLSurface) {
        check(glThread.isOnGlThread()) { "destroySurface must be called on AaynaGLThread" }
        if (eglDisplay != EGL14.EGL_NO_DISPLAY && surface != EGL14.EGL_NO_SURFACE) {
            if (EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW) == surface ||
                EGL14.eglGetCurrentSurface(EGL14.EGL_READ) == surface
            ) {
                makeDefaultCurrent()
            }
            EGL14.eglDestroySurface(eglDisplay, surface)
        }
    }

    fun refreshDiagnostics() {
        engineInternal?.let { engine ->
            diagnosticsRef.set(engine.diagnosticsInfo to engine.shaderStatuses)
        }
    }

    fun getDiagnostics(): Pair<GlDiagnosticsInfo?, List<PassShaderStatus>> = diagnosticsRef.get()

    fun release() {
        if (!released.compareAndSet(false, true)) return
        if (glThread.isAlive) {
            runCatching {
                glThread.runBlocking {
                    releaseGlResourcesOnThread()
                }
            }
            glThread.shutdownSafely()
        }
    }

    override fun close() {
        release()
    }

    private fun releaseGlResourcesOnThread() {
        if (initialized && eglDisplay != EGL14.EGL_NO_DISPLAY) {
            makeDefaultCurrent()
        }
        engineInternal?.release()
        engineInternal = null

        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(
                eglDisplay,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT
            )
            if (defaultPbufferSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(eglDisplay, defaultPbufferSurface)
                defaultPbufferSurface = EGL14.EGL_NO_SURFACE
            }
            if (eglContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(eglDisplay, eglContext)
                eglContext = EGL14.EGL_NO_CONTEXT
            }
            EGL14.eglTerminate(eglDisplay)
            eglDisplay = EGL14.EGL_NO_DISPLAY
        }
        eglConfig = null
        initialized = false
    }

    private fun chooseEglConfig(display: EGLDisplay): EGLConfig {
        val recordableAttribs =
            intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE,
                EGL_OPENGL_ES3_BIT,
                EGL14.EGL_SURFACE_TYPE,
                EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE,
                8,
                EGL14.EGL_GREEN_SIZE,
                8,
                EGL14.EGL_BLUE_SIZE,
                8,
                EGL14.EGL_ALPHA_SIZE,
                8,
                EGL_RECORDABLE_ANDROID,
                1,
                EGL14.EGL_NONE
            )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (EGL14.eglChooseConfig(display, recordableAttribs, 0, configs, 0, 1, numConfigs, 0) &&
            numConfigs[0] > 0 &&
            configs[0] != null
        ) {
            return checkNotNull(configs[0])
        }

        // Fallback for emulators or drivers without EGL_RECORDABLE_ANDROID
        val fallbackAttribs =
            intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE,
                EGL_OPENGL_ES3_BIT,
                EGL14.EGL_SURFACE_TYPE,
                EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,
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
        check(
            EGL14.eglChooseConfig(display, fallbackAttribs, 0, configs, 0, 1, numConfigs, 0) &&
                numConfigs[0] > 0 &&
                configs[0] != null
        ) {
            "eglChooseConfig failed for ES 3.0 window/pbuffer"
        }
        return checkNotNull(configs[0])
    }

    companion object {
        private const val EGL_OPENGL_ES3_BIT = 0x0040
        private const val EGL_RECORDABLE_ANDROID = 0x3142
    }
}
