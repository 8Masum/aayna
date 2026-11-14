package com.themasum.aayna.core.gl

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.opengl.Matrix
import android.view.Surface
import com.themasum.aayna.core.model.OpticalSettings
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class VideoTranscodePlan(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val sourceRotationDegrees: Int,
    val totalRotationDegrees: Int,
    val displayWidth: Int,
    val displayHeight: Int,
    val outputWidth: Int,
    val outputHeight: Int,
    val trimStartMs: Long,
    val trimEndMs: Long,
    val trimStartUs: Long,
    val trimEndUs: Long,
    val durationMs: Long,
    val bitrate: Int,
    val frameRate: Int
)

object VideoTranscodeConfig {
    const val MIME_TYPE_AVC = "video/avc"
    const val DEFAULT_MAX_EDGE_PX = 1920
    const val DEFAULT_FRAME_RATE = 30
    const val MIN_TRIM_DURATION_MS = 100L
    const val I_FRAME_INTERVAL_SEC = 1

    fun buildPlan(
        sourceWidth: Int,
        sourceHeight: Int,
        sourceRotationDegrees: Int,
        sourceDurationMs: Long,
        trimStartMs: Long,
        trimEndMs: Long,
        opticalSettings: OpticalSettings,
        maxEdgePx: Int = DEFAULT_MAX_EDGE_PX,
        frameRate: Int = DEFAULT_FRAME_RATE,
        isPreComposited: Boolean = false
    ): VideoTranscodePlan {
        require(sourceWidth > 0 && sourceHeight > 0) {
            "Source video dimensions must be positive: ${sourceWidth}x$sourceHeight"
        }
        val normSourceRotation = OpticalSettings.normalizeRotationDegrees(sourceRotationDegrees)
        val totalRotation =
            OpticalSettings.normalizeRotationDegrees(normSourceRotation + opticalSettings.rotationDegrees)
        val (displayW, displayH) =
            GlMath.computeRotatedDimensions(
                width = sourceWidth,
                height = sourceHeight,
                rotationDegrees = totalRotation
            )

        val displayAspect = displayW.toDouble() / displayH.toDouble()
        val targetAspect =
            if (isPreComposited && opticalSettings.cropAspectRatio == null) {
                displayAspect
            } else {
                GlMath.computeEffectiveAspectRatio(
                    opticalSettings = opticalSettings,
                    isPortraitSurface = displayH > displayW
                ).toDouble()
            }

        var rawW: Double
        var rawH: Double
        if (displayAspect > targetAspect) {
            rawH = displayH.toDouble()
            rawW = rawH * targetAspect
        } else {
            rawW = displayW.toDouble()
            rawH = rawW / targetAspect
        }

        val edgeCap = maxEdgePx.coerceAtLeast(16).toDouble()
        val currentLong = maxOf(rawW, rawH)
        if (currentLong > edgeCap) {
            val scale = edgeCap / currentLong
            rawW *= scale
            rawH *= scale
        }

        val outW = ((rawW.roundToInt() / 2) * 2).coerceAtLeast(16)
        val outH = ((rawH.roundToInt() / 2) * 2).coerceAtLeast(16)

        val safeSourceDuration = sourceDurationMs.coerceAtLeast(0L)
        val effectiveStart = trimStartMs.coerceAtLeast(0L)
        val rawEnd =
            if (trimEndMs == Long.MAX_VALUE || trimEndMs <= 0L) {
                safeSourceDuration
            } else {
                trimEndMs
            }
        val effectiveEnd =
            if (safeSourceDuration > 0L) {
                rawEnd.coerceAtMost(safeSourceDuration)
            } else {
                rawEnd
            }

        require(effectiveEnd > effectiveStart) {
            "Trim end ($effectiveEnd ms) must be greater than trim start ($effectiveStart ms)"
        }
        val durationMs = effectiveEnd - effectiveStart
        require(durationMs >= MIN_TRIM_DURATION_MS) {
            "Trimmed clip duration ($durationMs ms) must be at least $MIN_TRIM_DURATION_MS ms"
        }

        val clampedFps = frameRate.coerceIn(15, 60)
        val bitrate = (outW.toLong() * outH.toLong() * 6L).coerceIn(2_000_000L, 14_000_000L).toInt()

        return VideoTranscodePlan(
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            sourceRotationDegrees = normSourceRotation,
            totalRotationDegrees = totalRotation,
            displayWidth = displayW,
            displayHeight = displayH,
            outputWidth = outW,
            outputHeight = outH,
            trimStartMs = effectiveStart,
            trimEndMs = effectiveEnd,
            trimStartUs = effectiveStart * 1000L,
            trimEndUs = effectiveEnd * 1000L,
            durationMs = durationMs,
            bitrate = bitrate,
            frameRate = clampedFps
        )
    }
}

/**
 * Hardware-accelerated GL video transcoder using [MediaExtractor] + [MediaCodec] decoder ->
 * OES [SurfaceTexture] -> [AaynaFourPassEngine] -> [MediaCodec] H.264 encoder + audio passthrough
 * cropped to `[trimStartMs, trimEndMs]` -> [MediaMuxer].
 *
 * All video rotation (`KEY_ROTATION` / `METADATA_KEY_VIDEO_ROTATION` + `opticalSettings.rotationDegrees`)
 * is baked upright in GL so frames, aspect crops, and date stamps render right-side-up with `orientationHint = 0`.
 */
class VideoGlTranscoder(private val context: Context, private val glEngine: AaynaPersistentGlEngine) {
    private data class SourceMetadata(
        val videoTrackIndex: Int,
        val videoFormat: MediaFormat,
        val videoMime: String,
        val audioTrackIndex: Int,
        val audioFormat: MediaFormat?,
        val width: Int,
        val height: Int,
        val rotationDegrees: Int,
        val durationMs: Long,
        val frameRate: Int
    )

    suspend fun transcode(
        sourceUri: Uri,
        outputFile: File,
        params: GlRenderParams,
        trimStartMs: Long = 0L,
        trimEndMs: Long = Long.MAX_VALUE,
        maxEdgePx: Int = VideoTranscodeConfig.DEFAULT_MAX_EDGE_PX,
        onProgress: ((Float) -> Unit)? = null
    ): VideoTranscodePlan = withContext(Dispatchers.IO) {
        check(!glEngine.isReleased()) { "AaynaPersistentGlEngine is released" }
        outputFile.parentFile?.mkdirs()

        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var encoderInputSurface: Surface? = null
        var encoderEglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
        var decoderSurface: Surface? = null
        var decoderSurfaceTexture: SurfaceTexture? = null
        var oesTexId = 0
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var completed = false

        try {
            extractor.setDataSource(context, sourceUri, null)
            val metadata = extractSourceMetadata(sourceUri, extractor)
            val plan =
                VideoTranscodeConfig.buildPlan(
                    sourceWidth = metadata.width,
                    sourceHeight = metadata.height,
                    sourceRotationDegrees = metadata.rotationDegrees,
                    sourceDurationMs = metadata.durationMs,
                    trimStartMs = trimStartMs,
                    trimEndMs = trimEndMs,
                    opticalSettings = params.opticalSettings,
                    maxEdgePx = maxEdgePx,
                    frameRate = metadata.frameRate,
                    isPreComposited = params.isPreComposited
                )

            val encoderFormat =
                MediaFormat.createVideoFormat(
                    VideoTranscodeConfig.MIME_TYPE_AVC,
                    plan.outputWidth,
                    plan.outputHeight
                ).apply {
                    setInteger(
                        MediaFormat.KEY_COLOR_FORMAT,
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                    )
                    setInteger(MediaFormat.KEY_BIT_RATE, plan.bitrate)
                    setInteger(MediaFormat.KEY_FRAME_RATE, plan.frameRate)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, VideoTranscodeConfig.I_FRAME_INTERVAL_SEC)
                }

            val videoEncoder = MediaCodec.createEncoderByType(VideoTranscodeConfig.MIME_TYPE_AVC)
            encoder = videoEncoder
            videoEncoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val encInputSurface = videoEncoder.createInputSurface()
            encoderInputSurface = encInputSurface
            videoEncoder.start()

            val frameLock = ReentrantLock()
            val frameCondition = frameLock.newCondition()
            var frameAvailable = false

            glEngine.runBlocking {
                glEngine.ensureInitialized()
                glEngine.makeDefaultCurrent()

                val texIds = IntArray(1)
                GLES30.glGenTextures(1, texIds, 0)
                oesTexId = texIds[0]
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
                        setDefaultBufferSize(plan.sourceWidth, plan.sourceHeight)
                        setOnFrameAvailableListener(
                            {
                                frameLock.withLock {
                                    frameAvailable = true
                                    frameCondition.signalAll()
                                }
                            },
                            glEngine.handler
                        )
                    }
                decoderSurfaceTexture = st
                decoderSurface = Surface(st)
                encoderEglSurface = glEngine.createWindowSurface(encInputSurface)
            }

            val videoDecoder = MediaCodec.createDecoderByType(metadata.videoMime)
            decoder = videoDecoder
            videoDecoder.configure(metadata.videoFormat, decoderSurface, null, 0)
            videoDecoder.start()

            val activeMuxer =
                MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).apply {
                    setOrientationHint(0)
                }
            muxer = activeMuxer

            extractor.selectTrack(metadata.videoTrackIndex)
            if (plan.trimStartUs > 0L) {
                extractor.seekTo(plan.trimStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            }

            val effectiveParams =
                params.copy(
                    inputAspectRatio = plan.displayWidth.toFloat() / plan.displayHeight.coerceAtLeast(1).toFloat()
                )
            val stMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }

            val decoderBufferInfo = MediaCodec.BufferInfo()
            val encoderBufferInfo = MediaCodec.BufferInfo()

            var muxerVideoTrack = -1
            var muxerAudioTrack = -1
            var extractorDone = false
            var decoderDone = false
            var encoderDone = false
            var stalledLoops = 0

            while (!encoderDone) {
                currentCoroutineContext().ensureActive()
                var progressed = false

                if (!extractorDone && !decoderDone) {
                    val inIndex = videoDecoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inIndex >= 0) {
                        progressed = true
                        val inBuf =
                            videoDecoder.getInputBuffer(inIndex)
                                ?: error("decoder.getInputBuffer($inIndex) returned null")
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        val sampleTimeUs = extractor.sampleTime
                        if (sampleSize < 0 || sampleTimeUs > plan.trimEndUs) {
                            videoDecoder.queueInputBuffer(
                                inIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            extractorDone = true
                        } else {
                            videoDecoder.queueInputBuffer(
                                inIndex,
                                0,
                                sampleSize,
                                sampleTimeUs,
                                extractor.sampleFlags
                            )
                            extractor.advance()
                        }
                    }
                }

                if (!decoderDone) {
                    val outIndex = videoDecoder.dequeueOutputBuffer(decoderBufferInfo, CODEC_TIMEOUT_US)
                    when {
                        outIndex >= 0 -> {
                            progressed = true
                            val ptsUs = decoderBufferInfo.presentationTimeUs
                            val isEos = (decoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                            val shouldRender =
                                decoderBufferInfo.size > 0 &&
                                    ptsUs in plan.trimStartUs..plan.trimEndUs

                            if (shouldRender) {
                                frameLock.withLock {
                                    frameAvailable = false
                                }
                                videoDecoder.releaseOutputBuffer(outIndex, true)
                                awaitFrameAvailable(frameLock, frameCondition) { frameAvailable }

                                val outPtsUs = (ptsUs - plan.trimStartUs).coerceAtLeast(0L)
                                glEngine.runBlocking {
                                    val st = checkNotNull(decoderSurfaceTexture)
                                    glEngine.makeDefaultCurrent()
                                    st.updateTexImage()
                                    st.getTransformMatrix(stMatrix)
                                    val combinedMatrix =
                                        GlMath.computeOesTexMatrix(stMatrix, plan.totalRotationDegrees)
                                    check(glEngine.makeCurrent(encoderEglSurface)) {
                                        "Failed to make VideoGlTranscoder encoder surface current"
                                    }
                                    glEngine.fourPassEngine.render(
                                        inputTextureId = oesTexId,
                                        texMatrix = combinedMatrix,
                                        targetWidth = plan.outputWidth,
                                        targetHeight = plan.outputHeight,
                                        outputFboId = 0,
                                        params = effectiveParams,
                                        useOes = true
                                    )
                                    glEngine.setPresentationTime(encoderEglSurface, outPtsUs * 1000L)
                                    glEngine.swapBuffers(encoderEglSurface)
                                    glEngine.makeDefaultCurrent()
                                }
                                onProgress?.invoke(
                                    (outPtsUs.toFloat() / (plan.durationMs * 1000f)).coerceIn(0f, 0.90f)
                                )
                            } else {
                                videoDecoder.releaseOutputBuffer(outIndex, false)
                            }

                            if (isEos || ptsUs > plan.trimEndUs) {
                                decoderDone = true
                                videoEncoder.signalEndOfInputStream()
                            }
                        }
                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> progressed = true
                    }
                }

                // Drain available encoded video packets
                while (!encoderDone) {
                    val encOutIndex = videoEncoder.dequeueOutputBuffer(encoderBufferInfo, CODEC_TIMEOUT_US)
                    when {
                        encOutIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> break
                        encOutIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            progressed = true
                            check(!muxerStarted) { "Video encoder output format changed twice" }
                            muxerVideoTrack = activeMuxer.addTrack(videoEncoder.outputFormat)
                            val audioFmt = metadata.audioFormat
                            if (audioFmt != null) {
                                muxerAudioTrack =
                                    runCatching { activeMuxer.addTrack(audioFmt) }.getOrDefault(-1)
                            }
                            activeMuxer.start()
                            muxerStarted = true
                        }
                        encOutIndex >= 0 -> {
                            progressed = true
                            val encBuf =
                                videoEncoder.getOutputBuffer(encOutIndex)
                                    ?: error("encoder.getOutputBuffer($encOutIndex) returned null")

                            if ((encoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                encoderBufferInfo.size = 0
                            }
                            if (encoderBufferInfo.size > 0 && muxerStarted) {
                                encBuf.position(encoderBufferInfo.offset)
                                encBuf.limit(encoderBufferInfo.offset + encoderBufferInfo.size)
                                activeMuxer.writeSampleData(muxerVideoTrack, encBuf, encoderBufferInfo)
                            }
                            videoEncoder.releaseOutputBuffer(encOutIndex, false)
                            if ((encoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                encoderDone = true
                                break
                            }
                        }
                    }
                }

                if (progressed) {
                    stalledLoops = 0
                } else {
                    stalledLoops++
                    if (stalledLoops >= MAX_STALLED_LOOPS) {
                        error("Video transcode timed out waiting for MediaCodec buffers")
                    }
                }
            }

            if (muxerStarted && muxerAudioTrack >= 0 && metadata.audioTrackIndex >= 0) {
                copyAudioPassthrough(
                    extractor = extractor,
                    videoTrackIndex = metadata.videoTrackIndex,
                    audioTrackIndex = metadata.audioTrackIndex,
                    audioFormat = metadata.audioFormat,
                    muxer = activeMuxer,
                    muxerAudioTrack = muxerAudioTrack,
                    trimStartUs = plan.trimStartUs,
                    trimEndUs = plan.trimEndUs
                )
            }

            onProgress?.invoke(1f)
            completed = true
            plan
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { encoder?.stop() }
            runCatching { encoder?.release() }
            runCatching { decoderSurface?.release() }
            runCatching { encoderInputSurface?.release() }
            if (!glEngine.isReleased()) {
                runCatching {
                    glEngine.runBlocking {
                        glEngine.makeDefaultCurrent()
                        decoderSurfaceTexture?.setOnFrameAvailableListener(null)
                        decoderSurfaceTexture?.release()
                        if (oesTexId != 0) {
                            GLES30.glDeleteTextures(1, intArrayOf(oesTexId), 0)
                        }
                        if (encoderEglSurface != EGL14.EGL_NO_SURFACE) {
                            glEngine.destroySurface(encoderEglSurface)
                        }
                    }
                }
            }
            runCatching { extractor.release() }
            if (muxerStarted) {
                runCatching { muxer?.stop() }
            }
            runCatching { muxer?.release() }
            if (!completed) {
                runCatching { outputFile.delete() }
            }
        }
    }

    private fun extractSourceMetadata(sourceUri: Uri, extractor: MediaExtractor): SourceMetadata {
        var videoTrackIndex = -1
        var videoFormat: MediaFormat? = null
        var videoMime = ""
        var audioTrackIndex = -1
        var audioFormat: MediaFormat? = null

        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
            if (videoTrackIndex < 0 && mime.startsWith("video/")) {
                videoTrackIndex = i
                videoFormat = fmt
                videoMime = mime
            } else if (audioTrackIndex < 0 && mime.startsWith("audio/")) {
                audioTrackIndex = i
                audioFormat = fmt
            }
        }

        val resolvedVideoFormat =
            requireNotNull(videoFormat) { "No video track found in $sourceUri" }
        val width = resolvedVideoFormat.getInteger(MediaFormat.KEY_WIDTH)
        val height = resolvedVideoFormat.getInteger(MediaFormat.KEY_HEIGHT)

        val formatRotation =
            if (resolvedVideoFormat.containsKey(MediaFormat.KEY_ROTATION)) {
                resolvedVideoFormat.getInteger(MediaFormat.KEY_ROTATION)
            } else {
                null
            }
        val formatDurationMs =
            if (resolvedVideoFormat.containsKey(MediaFormat.KEY_DURATION)) {
                resolvedVideoFormat.getLong(MediaFormat.KEY_DURATION) / 1000L
            } else {
                0L
            }
        val frameRate =
            if (resolvedVideoFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                resolvedVideoFormat.getInteger(MediaFormat.KEY_FRAME_RATE)
                    .coerceIn(15, 60)
            } else {
                VideoTranscodeConfig.DEFAULT_FRAME_RATE
            }

        var retrieverRotation = 0
        var retrieverDurationMs = 0L
        if (formatRotation == null || formatDurationMs <= 0L) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, sourceUri)
                retrieverRotation =
                    retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                        ?.toIntOrNull()
                        ?: 0
                retrieverDurationMs =
                    retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()
                        ?: 0L
            } finally {
                runCatching { retriever.release() }
            }
        }

        return SourceMetadata(
            videoTrackIndex = videoTrackIndex,
            videoFormat = resolvedVideoFormat,
            videoMime = videoMime,
            audioTrackIndex = audioTrackIndex,
            audioFormat = audioFormat,
            width = width,
            height = height,
            rotationDegrees = formatRotation ?: retrieverRotation,
            durationMs = if (formatDurationMs > 0L) formatDurationMs else retrieverDurationMs,
            frameRate = frameRate
        )
    }

    private suspend fun copyAudioPassthrough(
        extractor: MediaExtractor,
        videoTrackIndex: Int,
        audioTrackIndex: Int,
        audioFormat: MediaFormat?,
        muxer: MediaMuxer,
        muxerAudioTrack: Int,
        trimStartUs: Long,
        trimEndUs: Long
    ) {
        extractor.unselectTrack(videoTrackIndex)
        extractor.selectTrack(audioTrackIndex)
        if (trimStartUs > 0L) {
            extractor.seekTo(trimStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        }

        val maxInputSize =
            if (audioFormat != null && audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(DEFAULT_AUDIO_BUFFER_BYTES)
            } else {
                DEFAULT_AUDIO_BUFFER_BYTES
            }
        val buffer = ByteBuffer.allocateDirect(maxInputSize)
        val info = MediaCodec.BufferInfo()

        while (true) {
            currentCoroutineContext().ensureActive()
            val sampleSize = extractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break
            val sampleTimeUs = extractor.sampleTime
            if (sampleTimeUs > trimEndUs) break
            if (sampleTimeUs < trimStartUs) {
                extractor.advance()
                continue
            }

            var codecFlags = 0
            val sampleFlags = extractor.sampleFlags
            if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_KEY_FRAME
            }
            if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0) {
                codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
            }
            info.offset = 0
            info.size = sampleSize
            info.presentationTimeUs = (sampleTimeUs - trimStartUs).coerceAtLeast(0L)
            info.flags = codecFlags
            buffer.position(0)
            buffer.limit(sampleSize)
            muxer.writeSampleData(muxerAudioTrack, buffer, info)
            extractor.advance()
        }
    }

    private fun awaitFrameAvailable(frameLock: ReentrantLock, frameCondition: Condition, isReady: () -> Boolean) {
        val deadlineNs = System.nanoTime() + FRAME_AVAILABLE_TIMEOUT_MS * 1_000_000L
        frameLock.withLock {
            while (!isReady()) {
                val remainingNs = deadlineNs - System.nanoTime()
                if (remainingNs <= 0L) {
                    error("Timed out waiting for MediaCodec decoder frame on SurfaceTexture")
                }
                frameCondition.await(remainingNs, TimeUnit.NANOSECONDS)
            }
        }
    }

    companion object {
        private const val GL_TEXTURE_EXTERNAL_OES = 0x8D65
        private const val CODEC_TIMEOUT_US = 10_000L
        private const val FRAME_AVAILABLE_TIMEOUT_MS = 2_500L
        private const val MAX_STALLED_LOOPS = 1_000
        private const val DEFAULT_AUDIO_BUFFER_BYTES = 256 * 1024
    }
}
