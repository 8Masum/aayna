package com.themasum.aayna.core.gl

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.view.Surface
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.IdentityHashMap
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class D3dMp4Plan(
    val width: Int,
    val height: Int,
    val frameDelayMs: Int,
    val fps: Int,
    val bitrate: Int,
    val cycleIndices: List<Int>,
    val repeatCount: Int,
    val frameSequence: List<Int>,
    val presentationTimesUs: List<Long>,
    val totalDurationMs: Int
)

object D3dMp4Config {
    const val MIME_TYPE_AVC = "video/avc"
    const val DEFAULT_FRAME_DELAY_MS = 140
    const val MIN_FRAME_DELAY_MS = 60
    const val MAX_FRAME_DELAY_MS = 500
    const val DEFAULT_TARGET_DURATION_MS = 2800
    const val MIN_TARGET_DURATION_MS = 2400
    const val MAX_TARGET_DURATION_MS = 3200
    const val DEFAULT_MAX_EDGE_PX = 1080
    const val I_FRAME_INTERVAL_SEC = 1

    fun buildCycleIndices(frameCount: Int, pingPong: Boolean = true): List<Int> {
        require(frameCount >= 1) { "At least 1 frame is required, got $frameCount" }
        if (!pingPong || frameCount <= 2) {
            return (0 until frameCount).toList()
        }
        val forward = (0 until frameCount).toList()
        val reverseMiddle = (frameCount - 2 downTo 1).toList()
        return forward + reverseMiddle
    }

    fun computeDimensions(sourceWidth: Int, sourceHeight: Int, maxEdgePx: Int = DEFAULT_MAX_EDGE_PX): Pair<Int, Int> {
        require(sourceWidth > 0 && sourceHeight > 0) {
            "Frame dimensions must be positive: ${sourceWidth}x$sourceHeight"
        }
        val cap = maxEdgePx.coerceAtLeast(16)
        val srcLong = maxOf(sourceWidth, sourceHeight)
        val scale = if (srcLong > cap) cap.toDouble() / srcLong.toDouble() else 1.0
        val outW = (((sourceWidth * scale).roundToInt() / 2) * 2).coerceAtLeast(16)
        val outH = (((sourceHeight * scale).roundToInt() / 2) * 2).coerceAtLeast(16)
        return outW to outH
    }

    fun computeBitrate(width: Int, height: Int): Int {
        val pixels = width.coerceAtLeast(16).toLong() * height.coerceAtLeast(16).toLong()
        return (pixels * 6L).coerceIn(1_500_000L, 10_000_000L).toInt()
    }

    fun buildPlan(
        sourceWidth: Int,
        sourceHeight: Int,
        frameCount: Int,
        frameDelayMs: Int = DEFAULT_FRAME_DELAY_MS,
        pingPong: Boolean = true,
        maxEdgePx: Int = DEFAULT_MAX_EDGE_PX,
        targetDurationMs: Int = DEFAULT_TARGET_DURATION_MS
    ): D3dMp4Plan {
        val (outW, outH) = computeDimensions(sourceWidth, sourceHeight, maxEdgePx)
        val clampedDelay = frameDelayMs.coerceIn(MIN_FRAME_DELAY_MS, MAX_FRAME_DELAY_MS)
        val clampedTarget = targetDurationMs.coerceIn(MIN_TARGET_DURATION_MS, MAX_TARGET_DURATION_MS)
        val cycle = buildCycleIndices(frameCount, pingPong)
        val cycleDurationMs = cycle.size * clampedDelay
        var repeatCount = (clampedTarget.toDouble() / cycleDurationMs.toDouble()).roundToInt().coerceAtLeast(1)
        if (repeatCount * cycleDurationMs < MIN_TARGET_DURATION_MS &&
            (repeatCount + 1) * cycleDurationMs <= MAX_TARGET_DURATION_MS
        ) {
            repeatCount += 1
        }
        val totalFrames = repeatCount * cycle.size
        val sequence = List(totalFrames) { idx -> cycle[idx % cycle.size] }
        val ptsUs = List(totalFrames) { idx -> idx.toLong() * clampedDelay.toLong() * 1000L }
        val fps = (1000.0 / clampedDelay.toDouble()).roundToInt().coerceIn(2, 30)
        return D3dMp4Plan(
            width = outW,
            height = outH,
            frameDelayMs = clampedDelay,
            fps = fps,
            bitrate = computeBitrate(outW, outH),
            cycleIndices = cycle,
            repeatCount = repeatCount,
            frameSequence = sequence,
            presentationTimesUs = ptsUs,
            totalDurationMs = totalFrames * clampedDelay
        )
    }
}

/**
 * Hardware-accelerated H.264 `.mp4` encoder for D3D wigglegram bursts (`List<Bitmap>`).
 *
 * Repeats the frame sequence (ping-pong or forward) cleanly to ~2.4s–3.2s, rendering each frame
 * onto a `MediaCodec` (`video/avc`) `Surface` via an EGL14 (`EGL_RECORDABLE_ANDROID`) window
 * surface with exact presentation timestamps (`eglPresentationTimeANDROID`) and muxing into MP4.
 */
class D3dMp4Encoder(private val glEngine: AaynaPersistentGlEngine? = null) {
    suspend fun encode(
        frames: List<Bitmap>,
        outputFile: File,
        frameDelayMs: Int = D3dMp4Config.DEFAULT_FRAME_DELAY_MS,
        pingPong: Boolean = true,
        maxEdgePx: Int = D3dMp4Config.DEFAULT_MAX_EDGE_PX,
        targetDurationMs: Int = D3dMp4Config.DEFAULT_TARGET_DURATION_MS
    ): D3dMp4Plan = withContext(Dispatchers.IO) {
        require(frames.isNotEmpty()) { "At least one Bitmap frame is required to encode D3D MP4" }
        val first = frames.first()
        val plan =
            D3dMp4Config.buildPlan(
                sourceWidth = first.width,
                sourceHeight = first.height,
                frameCount = frames.size,
                frameDelayMs = frameDelayMs,
                pingPong = pingPong,
                maxEdgePx = maxEdgePx,
                targetDurationMs = targetDurationMs
            )

        outputFile.parentFile?.mkdirs()

        var codec: MediaCodec? = null
        var inputSurface: Surface? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var muxerTrackIndex = -1
        var eglHost: EglBlitHost? = null
        var completed = false

        try {
            val format =
                MediaFormat.createVideoFormat(D3dMp4Config.MIME_TYPE_AVC, plan.width, plan.height).apply {
                    setInteger(
                        MediaFormat.KEY_COLOR_FORMAT,
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                    )
                    setInteger(MediaFormat.KEY_BIT_RATE, plan.bitrate)
                    setInteger(MediaFormat.KEY_FRAME_RATE, plan.fps)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, D3dMp4Config.I_FRAME_INTERVAL_SEC)
                }

            val encoder = MediaCodec.createEncoderByType(D3dMp4Config.MIME_TYPE_AVC)
            codec = encoder
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = encoder.createInputSurface()
            inputSurface = surface
            encoder.start()

            val activeMuxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = activeMuxer

            val host =
                if (glEngine != null && !glEngine.isReleased()) {
                    SharedEngineBlitHost(glEngine, surface, frames, plan.width, plan.height)
                } else {
                    StandaloneEglBlitHost(surface, frames, plan.width, plan.height)
                }
            eglHost = host
            host.prepare()

            val bufferInfo = MediaCodec.BufferInfo()
            for (stepIdx in plan.frameSequence.indices) {
                currentCoroutineContext().ensureActive()
                val frameIdx = plan.frameSequence[stepIdx]
                val ptsNs = plan.presentationTimesUs[stepIdx] * 1000L
                host.drawAndSwap(frameIdx, ptsNs)

                val drained =
                    drainEncoder(
                        encoder = encoder,
                        muxer = activeMuxer,
                        bufferInfo = bufferInfo,
                        trackIndex = muxerTrackIndex,
                        muxerStarted = muxerStarted,
                        endOfStream = false
                    )
                muxerTrackIndex = drained.first
                muxerStarted = drained.second
            }

            encoder.signalEndOfInputStream()
            val finalDrain =
                drainEncoder(
                    encoder = encoder,
                    muxer = activeMuxer,
                    bufferInfo = bufferInfo,
                    trackIndex = muxerTrackIndex,
                    muxerStarted = muxerStarted,
                    endOfStream = true
                )
            muxerTrackIndex = finalDrain.first
            muxerStarted = finalDrain.second
            completed = true
            plan
        } finally {
            runCatching { eglHost?.release() }
            runCatching { inputSurface?.release() }
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            if (muxerStarted) {
                runCatching { muxer?.stop() }
            }
            runCatching { muxer?.release() }
            if (!completed) {
                runCatching { outputFile.delete() }
            }
        }
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        trackIndex: Int,
        muxerStarted: Boolean,
        endOfStream: Boolean
    ): Pair<Int, Boolean> {
        var currentTrack = trackIndex
        var started = muxerStarted
        var eosIdleLoops = 0

        while (true) {
            val outIndex = encoder.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
            when {
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) break
                    eosIdleLoops++
                    if (eosIdleLoops >= MAX_EOS_IDLE_LOOPS) {
                        error("D3D MP4 encoder timed out waiting for EOS")
                    }
                }
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    check(!started) { "MediaCodec output format changed twice" }
                    currentTrack = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    started = true
                }
                outIndex >= 0 -> {
                    eosIdleLoops = 0
                    val encodedData =
                        encoder.getOutputBuffer(outIndex)
                            ?: error("encoder.getOutputBuffer($outIndex) returned null")

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size > 0 && started) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(currentTrack, encodedData, bufferInfo)
                    }

                    encoder.releaseOutputBuffer(outIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break
                    }
                }
            }
        }
        return currentTrack to started
    }

    private interface EglBlitHost {
        fun prepare()

        fun drawAndSwap(frameIndex: Int, ptsNs: Long)

        fun release()
    }

    private class SharedEngineBlitHost(
        private val glEngine: AaynaPersistentGlEngine,
        private val surface: Surface,
        private val frames: List<Bitmap>,
        private val width: Int,
        private val height: Int
    ) : EglBlitHost {
        private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
        private val blitter = BitmapQuadBlitter()

        override fun prepare() {
            glEngine.runBlocking {
                glEngine.ensureInitialized()
                glEngine.makeDefaultCurrent()
                eglSurface = glEngine.createWindowSurface(surface)
                check(glEngine.makeCurrent(eglSurface)) { "Failed to make D3D encoder surface current" }
                blitter.initAndUpload(frames)
            }
        }

        override fun drawAndSwap(frameIndex: Int, ptsNs: Long) {
            glEngine.runBlocking {
                check(glEngine.makeCurrent(eglSurface)) { "Failed to make D3D encoder surface current" }
                blitter.drawFrame(frameIndex, width, height)
                glEngine.setPresentationTime(eglSurface, ptsNs)
                glEngine.swapBuffers(eglSurface)
                glEngine.makeDefaultCurrent()
            }
        }

        override fun release() {
            if (glEngine.isReleased()) return
            glEngine.runBlocking {
                glEngine.makeDefaultCurrent()
                blitter.release()
                if (eglSurface != EGL14.EGL_NO_SURFACE) {
                    glEngine.destroySurface(eglSurface)
                    eglSurface = EGL14.EGL_NO_SURFACE
                }
            }
        }
    }

    private class StandaloneEglBlitHost(
        private val surface: Surface,
        private val frames: List<Bitmap>,
        private val width: Int,
        private val height: Int
    ) : EglBlitHost {
        private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
        private val blitter = BitmapQuadBlitter()

        override fun prepare() {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(eglDisplay != EGL14.EGL_NO_DISPLAY) { "eglGetDisplay failed" }
            val version = IntArray(2)
            check(EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) { "eglInitialize failed" }

            val config = chooseRecordableConfig(eglDisplay)
            eglContext =
                EGL14.eglCreateContext(
                    eglDisplay,
                    config,
                    EGL14.EGL_NO_CONTEXT,
                    intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE),
                    0
                )
            check(eglContext != EGL14.EGL_NO_CONTEXT) { "eglCreateContext ES 3.0 failed" }

            eglSurface =
                EGL14.eglCreateWindowSurface(
                    eglDisplay,
                    config,
                    surface,
                    intArrayOf(EGL14.EGL_NONE),
                    0
                )
            check(eglSurface != EGL14.EGL_NO_SURFACE) { "eglCreateWindowSurface failed" }
            check(EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                "eglMakeCurrent failed"
            }
            blitter.initAndUpload(frames)
        }

        override fun drawAndSwap(frameIndex: Int, ptsNs: Long) {
            blitter.drawFrame(frameIndex, width, height)
            EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, ptsNs)
            EGL14.eglSwapBuffers(eglDisplay, eglSurface)
        }

        override fun release() {
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                if (eglSurface != EGL14.EGL_NO_SURFACE && eglContext != EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
                }
                blitter.release()
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
        }

        private fun chooseRecordableConfig(display: EGLDisplay): EGLConfig {
            val attribs =
                intArrayOf(
                    EGL14.EGL_RENDERABLE_TYPE,
                    EGL_OPENGL_ES3_BIT,
                    EGL14.EGL_SURFACE_TYPE,
                    EGL14.EGL_WINDOW_BIT,
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
            if (EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, numConfigs, 0) &&
                numConfigs[0] > 0 &&
                configs[0] != null
            ) {
                return checkNotNull(configs[0])
            }
            val fallbackAttribs =
                intArrayOf(
                    EGL14.EGL_RENDERABLE_TYPE,
                    EGL_OPENGL_ES3_BIT,
                    EGL14.EGL_SURFACE_TYPE,
                    EGL14.EGL_WINDOW_BIT,
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
                "eglChooseConfig failed for D3dMp4Encoder"
            }
            return checkNotNull(configs[0])
        }
    }

    private class BitmapQuadBlitter {
        private var programId = 0
        private var samplerLoc = -1
        private val frameTexIds = mutableListOf<Int>()
        private val uniqueTexIds = mutableListOf<Int>()

        private val posBuffer: FloatBuffer =
            ByteBuffer
                .allocateDirect(QUAD_POS.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(QUAD_POS)
                    position(0)
                }

        private val uvBuffer: FloatBuffer =
            ByteBuffer
                .allocateDirect(QUAD_UV_FLIPPED.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(QUAD_UV_FLIPPED)
                    position(0)
                }

        fun initAndUpload(frames: List<Bitmap>) {
            val vs = compileShader(GLES30.GL_VERTEX_SHADER, BLIT_VERTEX_SHADER)
            val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, BLIT_FRAGMENT_SHADER)
            val prog = GLES30.glCreateProgram()
            GLES30.glAttachShader(prog, vs)
            GLES30.glAttachShader(prog, fs)
            GLES30.glLinkProgram(prog)
            GLES30.glDeleteShader(vs)
            GLES30.glDeleteShader(fs)

            val linkStatus = IntArray(1)
            GLES30.glGetProgramiv(prog, GLES30.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) {
                val log = GLES30.glGetProgramInfoLog(prog)
                GLES30.glDeleteProgram(prog)
                error("Failed to link D3D blit shader: $log")
            }
            programId = prog
            samplerLoc = GLES30.glGetUniformLocation(prog, "uTex")

            val byIdentity = IdentityHashMap<Bitmap, Int>()
            for (bmp in frames) {
                val texId =
                    byIdentity.getOrPut(bmp) {
                        val uploaded = AssetMetadataLoader.uploadTexture2D(bmp, recycleBitmap = false)
                        uniqueTexIds += uploaded
                        uploaded
                    }
                frameTexIds += texId
            }
        }

        fun drawFrame(frameIndex: Int, width: Int, height: Int) {
            val texId = frameTexIds[frameIndex]
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glViewport(0, 0, width, height)
            GLES30.glUseProgram(programId)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
            GLES30.glUniform1i(samplerLoc, 0)

            posBuffer.position(0)
            uvBuffer.position(0)
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, posBuffer)
            GLES30.glEnableVertexAttribArray(1)
            GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 0, uvBuffer)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
            GLES30.glDisableVertexAttribArray(0)
            GLES30.glDisableVertexAttribArray(1)
        }

        fun release() {
            if (uniqueTexIds.isNotEmpty()) {
                val ids = uniqueTexIds.toIntArray()
                GLES30.glDeleteTextures(ids.size, ids, 0)
                uniqueTexIds.clear()
            }
            frameTexIds.clear()
            if (programId != 0) {
                GLES30.glDeleteProgram(programId)
                programId = 0
            }
        }

        private fun compileShader(type: Int, source: String): Int {
            val shader = GLES30.glCreateShader(type)
            GLES30.glShaderSource(shader, source)
            GLES30.glCompileShader(shader)
            val status = IntArray(1)
            GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
            if (status[0] == 0) {
                val log = GLES30.glGetShaderInfoLog(shader)
                GLES30.glDeleteShader(shader)
                error("D3D blit shader compile failed: $log")
            }
            return shader
        }
    }

    companion object {
        private const val EGL_OPENGL_ES3_BIT = 0x0040
        private const val EGL_RECORDABLE_ANDROID = 0x3142
        private const val CODEC_TIMEOUT_US = 10_000L
        private const val MAX_EOS_IDLE_LOOPS = 500

        private val QUAD_POS =
            floatArrayOf(
                -1f,
                -1f,
                1f,
                -1f,
                -1f,
                1f,
                1f,
                1f
            )

        private val QUAD_UV_FLIPPED =
            floatArrayOf(
                0f,
                1f,
                1f,
                1f,
                0f,
                0f,
                1f,
                0f
            )

        private const val BLIT_VERTEX_SHADER = """#version 300 es
layout(location = 0) in vec2 aPosition;
layout(location = 1) in vec2 aTexCoord;
out vec2 vTexCoord;
void main() {
    gl_Position = vec4(aPosition, 0.0, 1.0);
    vTexCoord = aTexCoord;
}
"""

        private const val BLIT_FRAGMENT_SHADER = """#version 300 es
precision mediump float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uTex;
void main() {
    fragColor = vec4(texture(uTex, vTexCoord).rgb, 1.0);
}
"""
    }
}
