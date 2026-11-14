package com.themasum.aayna.core.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ExifInterface
import android.os.Build
import android.os.Looper
import android.util.Rational
import android.view.Display
import android.view.Surface
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraEffect
import androidx.camera.core.CameraSelector
import androidx.camera.core.DisplayOrientedMeteringPointFactory
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.SurfaceProcessor
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.core.ZoomState
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileDescriptorOutputOptions
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.OutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.themasum.aayna.core.model.AspectRatioPreset
import com.themasum.aayna.core.model.CameraCapabilityInfo
import com.themasum.aayna.core.model.DeviceFingerprint
import com.themasum.aayna.core.model.DeviceQuirkFlags
import com.themasum.aayna.core.model.DeviceQuirkRegistry
import com.themasum.aayna.core.model.FlashMode
import com.themasum.aayna.core.model.OpticalSettings
import com.themasum.aayna.core.model.ResolvedDeviceQuirks
import com.themasum.aayna.core.model.ShaderExposureFallback
import com.themasum.aayna.core.model.WhiteBalancePreset
import java.io.ByteArrayInputStream
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

data class PreviewSurfaceConfig(
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val isFrontCamera: Boolean,
    val isMirroring: Boolean,
    val sensorOrientationDegrees: Int
)

data class CaptureMetadata(
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val isFrontCamera: Boolean,
    val capturedAtEpochMs: Long,
    val cameraId: String
)

data class CapturedFrameResult(val bitmap: Bitmap, val metadata: CaptureMetadata)

internal class AaynaCameraEffect(
    targets: Int,
    executor: Executor,
    surfaceProcessor: SurfaceProcessor,
    errorConsumer: Consumer<Throwable>
) : CameraEffect(targets, executor, surfaceProcessor, errorConsumer)

@OptIn(ExperimentalCamera2Interop::class)
class AaynaCameraController(context: Context) {
    private val appContext: Context = context.applicationContext
    private val mainExecutor: Executor = ContextCompat.getMainExecutor(appContext)
    private val captureExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val cameraManager: CameraManager =
        appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private val _activeCapabilityInfo = MutableStateFlow<CameraCapabilityInfo?>(null)
    val activeCapabilityInfo: StateFlow<CameraCapabilityInfo?> = _activeCapabilityInfo.asStateFlow()

    private val _resolvedDeviceQuirks =
        MutableStateFlow<ResolvedDeviceQuirks?>(resolveDeviceQuirks(userAeCompatibilityEnabled = false))
    val resolvedDeviceQuirks: StateFlow<ResolvedDeviceQuirks?> = _resolvedDeviceQuirks.asStateFlow()

    private val _isFrontCamera = MutableStateFlow(false)
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera.asStateFlow()

    private val _isTorchActive = MutableStateFlow(false)
    val isTorchActive: StateFlow<Boolean> = _isTorchActive.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _zoomUiState = MutableStateFlow(ZoomUiState())
    val zoomUiState: StateFlow<ZoomUiState> = _zoomUiState.asStateFlow()

    private val _isCameraEffectActive = MutableStateFlow(false)
    val isCameraEffectActive: StateFlow<Boolean> = _isCameraEffectActive.asStateFlow()

    private val _cameraError = MutableStateFlow<String?>(null)
    val cameraError: StateFlow<String?> = _cameraError.asStateFlow()

    private val _shaderExposureFallback =
        MutableStateFlow(
            ShaderExposureFallback(
                evGainStops = 0f,
                shaderNoiseBoost = 0f,
                digitalBoostStops = 0f
            )
        )
    val shaderExposureFallback: StateFlow<ShaderExposureFallback> =
        _shaderExposureFallback.asStateFlow()

    private var cameraProvider: ProcessCameraProvider? = null
    private var boundCamera: Camera? = null
    private var boundPreviewView: PreviewView? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var activeBounds: CameraHardwareBounds? = null
    private var useHardwareAeCompatibility: Boolean = false
    private var lastOpticalSettings: OpticalSettings? = null

    private var observedZoomLiveData: LiveData<ZoomState>? = null
    private var zoomObserver: Observer<ZoomState>? = null

    fun setHardwareAeCompatibility(enabled: Boolean) {
        if (useHardwareAeCompatibility == enabled && _resolvedDeviceQuirks.value != null) {
            return
        }
        useHardwareAeCompatibility = enabled
        _resolvedDeviceQuirks.value = resolveDeviceQuirks(enabled)
        lastOpticalSettings?.let { settings ->
            applyOpticalSettings(settings, enabled)
        }
    }

    fun resolveDeviceQuirks(userAeCompatibilityEnabled: Boolean = useHardwareAeCompatibility): ResolvedDeviceQuirks =
        DeviceQuirkRegistry.resolve(
            fingerprint = currentDeviceFingerprint(),
            userAeCompatibilityEnabled = userAeCompatibilityEnabled
        )

    /**
     * Binds CameraX [Preview], [ImageCapture], and optional [VideoCapture] via a synchronized
     * [UseCaseGroup] attached to [previewView] (`PreviewView.ImplementationMode.COMPATIBLE` by
     * default, with automatic fallback if OEM HALs reject [CameraEffect] or multi-stream video).
     */
    suspend fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        useFrontCamera: Boolean = _isFrontCamera.value,
        aspectRatio: AspectRatioPreset = AspectRatioPreset.RATIO_4_3,
        surfaceProcessor: SurfaceProcessor? = null,
        surfaceProcessorExecutor: Executor = mainExecutor,
        enableVideoCapture: Boolean = false,
        videoQualitySelector: QualitySelector = DEFAULT_VIDEO_QUALITY_SELECTOR,
        preferCompatPreview: Boolean = true,
        useHardwareAeCompatibility: Boolean = this.useHardwareAeCompatibility,
        preferVideoMode: Boolean = false,
        onEffectError: (Throwable) -> Unit = {}
    ) {
        _cameraError.value = null
        this.useHardwareAeCompatibility = useHardwareAeCompatibility
        val quirks = resolveDeviceQuirks(useHardwareAeCompatibility)
        _resolvedDeviceQuirks.value = quirks

        val provider = getOrAwaitCameraProvider()
        cameraProvider = provider

        val selector = buildCameraSelector(useFrontCamera)
        val probeResult = probeSelectedCamera(provider, selector, quirks.flags)
        val bounds = probeResult?.bounds
        activeBounds = bounds
        _activeCapabilityInfo.value = probeResult?.capabilityInfo
        _isFrontCamera.value = useFrontCamera
        _isTorchActive.value = false
        boundPreviewView = previewView

        val bindingPolicy =
            CameraPolicyMath.resolvePreviewBindingPolicy(
                preferCompatPreview = preferCompatPreview,
                hasSurfaceProcessor = surfaceProcessor != null,
                quirkFlags = quirks.flags
            )
        previewView.implementationMode =
            if (bindingPolicy.useCompatPreview) {
                PreviewView.ImplementationMode.COMPATIBLE
            } else {
                PreviewView.ImplementationMode.PERFORMANCE
            }

        val targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
        val cameraXAspectRatio = CameraPolicyMath.toCameraXAspectRatio(aspectRatio)
        val resolutionSelector = buildResolutionSelector(cameraXAspectRatio)

        val preview =
            Preview.Builder()
                .setResolutionSelector(resolutionSelector)
                .setTargetRotation(targetRotation)
                .apply { applyIspNeutralization(Camera2Interop.Extender(this), bounds, quirks.flags) }
                .build()
                .apply {
                    surfaceProvider = previewView.surfaceProvider
                }

        val captureUseCase =
            ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setResolutionSelector(resolutionSelector)
                .setTargetRotation(targetRotation)
                .apply { applyIspNeutralization(Camera2Interop.Extender(this), bounds, quirks.flags) }
                .build()

        val videoUseCase =
            if (enableVideoCapture) {
                val recorder =
                    Recorder.Builder()
                        .setQualitySelector(videoQualitySelector)
                        .setAspectRatio(cameraXAspectRatio)
                        .build()
                VideoCapture.Builder(recorder)
                    .setTargetRotation(targetRotation)
                    .build()
            } else {
                null
            }

        val viewPort =
            previewView.viewPort ?: previewView.getViewPort(targetRotation)
                ?: buildFallbackViewPort(aspectRatio, targetRotation)

        val effect =
            if (bindingPolicy.enableCameraEffect && surfaceProcessor != null) {
                AaynaCameraEffect(
                    targets = CameraEffect.PREVIEW or CameraEffect.VIDEO_CAPTURE,
                    executor = surfaceProcessorExecutor,
                    surfaceProcessor = surfaceProcessor,
                    errorConsumer = { error ->
                        _cameraError.value = error.message ?: "CameraEffect surface processing failed"
                        onEffectError(error)
                    }
                )
            } else {
                null
            }

        stopVideoRecording()

        val bound =
            bindUseCaseGroupWithFallback(
                provider = provider,
                lifecycleOwner = lifecycleOwner,
                selector = selector,
                viewPort = viewPort,
                preview = preview,
                captureUseCase = captureUseCase,
                videoUseCase = videoUseCase,
                effect = effect,
                preferVideoMode = preferVideoMode
            )
        boundCamera = bound.camera
        imageCapture = bound.imageCapture
        videoCapture = bound.videoCapture
        _isCameraEffectActive.value = bound.effectActive
        attachZoomObserver(bound.camera)
    }

    /**
     * Legacy [SurfaceTexture] binding preserved for callers not yet migrated to [PreviewView].
     */
    suspend fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        useFrontCamera: Boolean = _isFrontCamera.value,
        surfaceTextureProvider: (width: Int, height: Int) -> SurfaceTexture?,
        onTransformationInfo: (PreviewSurfaceConfig) -> Unit = {}
    ) {
        _cameraError.value = null
        val quirks = resolveDeviceQuirks(useHardwareAeCompatibility)
        _resolvedDeviceQuirks.value = quirks

        val provider = getOrAwaitCameraProvider()
        cameraProvider = provider

        val selector = buildCameraSelector(useFrontCamera)
        val probeResult = probeSelectedCamera(provider, selector, quirks.flags)
        val bounds = probeResult?.bounds
        activeBounds = bounds
        _activeCapabilityInfo.value = probeResult?.capabilityInfo
        _isFrontCamera.value = useFrontCamera
        _isTorchActive.value = false
        boundPreviewView = null
        videoCapture = null
        _isCameraEffectActive.value = false

        val previewBuilder = Preview.Builder()
        applyIspNeutralization(Camera2Interop.Extender(previewBuilder), bounds, quirks.flags)
        val preview = previewBuilder.build()

        val sensorOrientation = bounds?.sensorOrientationDegrees ?: 0
        preview.setSurfaceProvider(mainExecutor) { request: SurfaceRequest ->
            val width = request.resolution.width
            val height = request.resolution.height
            request.setTransformationInfoListener(mainExecutor) { info ->
                onTransformationInfo(
                    PreviewSurfaceConfig(
                        width = width,
                        height = height,
                        rotationDegrees = info.rotationDegrees,
                        isFrontCamera = useFrontCamera,
                        isMirroring = info.isMirroring,
                        sensorOrientationDegrees = sensorOrientation
                    )
                )
            }
            val surfaceTexture = surfaceTextureProvider(width, height)
            if (surfaceTexture == null) {
                request.willNotProvideSurface()
                return@setSurfaceProvider
            }
            surfaceTexture.setDefaultBufferSize(width, height)
            val surface = Surface(surfaceTexture)
            request.provideSurface(surface, mainExecutor) {
                surface.release()
            }
        }

        val captureBuilder =
            ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        applyIspNeutralization(Camera2Interop.Extender(captureBuilder), bounds, quirks.flags)
        val captureUseCase = captureBuilder.build()

        try {
            provider.unbindAll()
            val camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, captureUseCase)
            boundCamera = camera
            imageCapture = captureUseCase
            attachZoomObserver(camera)
        } catch (t: Throwable) {
            _cameraError.value = t.message ?: "Failed to bind camera use cases"
            throw t
        }
    }

    fun applyOpticalSettings(
        settings: OpticalSettings,
        useHardwareAeCompatibility: Boolean = this.useHardwareAeCompatibility
    ) {
        lastOpticalSettings = settings
        if (this.useHardwareAeCompatibility != useHardwareAeCompatibility || _resolvedDeviceQuirks.value == null) {
            this.useHardwareAeCompatibility = useHardwareAeCompatibility
            _resolvedDeviceQuirks.value = resolveDeviceQuirks(useHardwareAeCompatibility)
        }

        val quirks = _resolvedDeviceQuirks.value ?: resolveDeviceQuirks(useHardwareAeCompatibility)
        val bounds = activeBounds
        val camera = boundCamera
        val aeDecision =
            CameraPolicyMath.resolveAeDecision(
                settings = settings,
                hasManualSensor = bounds?.hasManualSensor == true,
                minIso = bounds?.minIso ?: DEFAULT_MIN_ISO,
                maxIso = bounds?.maxIso ?: DEFAULT_MAX_ISO,
                minExposureTimeNs = bounds?.minExposureTimeNs ?: DEFAULT_MIN_EXPOSURE_NS,
                maxExposureTimeNs = bounds?.maxExposureTimeNs ?: DEFAULT_MAX_EXPOSURE_NS,
                aeCompensationMinIndex = bounds?.aeCompensationMinIndex ?: 0,
                aeCompensationMaxIndex = bounds?.aeCompensationMaxIndex ?: 0,
                aeCompensationStepNumerator = bounds?.aeCompensationStepNumerator ?: 1,
                aeCompensationStepDenominator = bounds?.aeCompensationStepDenominator ?: 1,
                quirkFlags = quirks.flags,
                useHardwareAeCompatibility = useHardwareAeCompatibility,
                hasBoundCamera = camera != null
            )
        _shaderExposureFallback.value = aeDecision.shaderFallback

        if (camera == null) return
        val capture = imageCapture

        capture?.flashMode =
            when (settings.flashMode) {
                FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
                FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                FlashMode.HARDWARE_ON -> ImageCapture.FLASH_MODE_ON
                FlashMode.OPTICAL_FLASH_SIM -> ImageCapture.FLASH_MODE_OFF
            }

        val camera2Control = Camera2CameraControl.from(camera.cameraControl)
        val optionsBuilder = CaptureRequestOptions.Builder()

        val awbMode = resolveAwbMode(settings.whiteBalance, bounds?.supportedAwbModes ?: emptySet())
        optionsBuilder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, awbMode)

        if (aeDecision.useManualAeOff) {
            optionsBuilder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_OFF
            )
            aeDecision.clampedIso?.let { clampedIso ->
                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.SENSOR_SENSITIVITY,
                    clampedIso
                )
            }
            aeDecision.clampedExposureTimeNs?.let { clampedNs ->
                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.SENSOR_EXPOSURE_TIME,
                    clampedNs
                )
            }
        } else {
            optionsBuilder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON
            )
            if (aeDecision.hardwareEvApplied) {
                camera.cameraControl.setExposureCompensationIndex(aeDecision.evCompensationIndex)
            }
        }

        camera2Control.setCaptureRequestOptions(optionsBuilder.build())
    }

    fun tapToFocus(xPx: Float, yPx: Float) {
        val previewView = boundPreviewView ?: return
        tapToFocus(previewView = previewView, xPx = xPx, yPx = yPx)
    }

    fun tapToFocus(previewView: PreviewView, xPx: Float, yPx: Float) {
        val camera = boundCamera ?: return
        if (previewView.width <= 0 || previewView.height <= 0) return
        val clampedX = xPx.coerceIn(0f, previewView.width.toFloat())
        val clampedY = yPx.coerceIn(0f, previewView.height.toFloat())
        val point = previewView.meteringPointFactory.createPoint(clampedX, clampedY)
        startFocusAndMetering(camera, point)
    }

    fun tapToFocus(normalizedX: Float, normalizedY: Float, viewWidth: Int, viewHeight: Int) {
        val camera = boundCamera ?: return
        if (viewWidth <= 0 || viewHeight <= 0) return

        val previewView = boundPreviewView
        val factory: MeteringPointFactory
        val pointX: Float
        val pointY: Float

        if (previewView != null && previewView.width > 0 && previewView.height > 0) {
            factory = previewView.meteringPointFactory
            pointX = normalizedX.coerceIn(0f, 1f) * previewView.width.toFloat()
            pointY = normalizedY.coerceIn(0f, 1f) * previewView.height.toFloat()
        } else {
            val display: Display? = previewView?.display
            factory =
                if (display != null) {
                    DisplayOrientedMeteringPointFactory(
                        display,
                        camera.cameraInfo,
                        viewWidth.toFloat(),
                        viewHeight.toFloat()
                    )
                } else {
                    SurfaceOrientedMeteringPointFactory(viewWidth.toFloat(), viewHeight.toFloat())
                }
            pointX = normalizedX.coerceIn(0f, 1f) * viewWidth.toFloat()
            pointY = normalizedY.coerceIn(0f, 1f) * viewHeight.toFloat()
        }

        val point = factory.createPoint(pointX, pointY)
        startFocusAndMetering(camera, point)
    }

    fun setZoomRatio(ratio: Float) {
        val camera = boundCamera ?: return
        val clamped = _zoomUiState.value.bounds.clampRatio(ratio)
        camera.cameraControl.setZoomRatio(clamped)
    }

    fun setLinearZoom(linearZoom: Float) {
        val camera = boundCamera ?: return
        camera.cameraControl.setLinearZoom(linearZoom.coerceIn(0f, 1f))
    }

    fun setTorchEnabled(enabled: Boolean) {
        val camera = boundCamera ?: return
        if (activeBounds?.hasFlashUnit != true) {
            _isTorchActive.value = false
            return
        }
        camera.cameraControl.enableTorch(enabled)
        _isTorchActive.value = enabled
    }

    suspend fun captureCleanFrame(
        maxEdgePx: Int = BitmapDecodeMath.DEFAULT_MAX_LONG_EDGE,
        isFrontMirrorEnabled: Boolean = true
    ): CapturedFrameResult {
        val capture =
            imageCapture ?: throw IllegalStateException("ImageCapture is not bound")
        val frontCamera = _isFrontCamera.value
        val cameraId = activeBounds?.cameraId ?: "unknown"
        val swapFrontMirror = _resolvedDeviceQuirks.value?.flags?.swapFrontMirror == true

        return suspendCancellableCoroutine { continuation ->
            capture.takePicture(
                captureExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val bytes =
                            try {
                                extractJpegBytes(image)
                            } finally {
                                image.close()
                            }
                        try {
                            val result =
                                decodeJpegBytes(
                                    bytes = bytes,
                                    maxEdgePx = maxEdgePx,
                                    frontCamera = frontCamera,
                                    mirrorFrontCamera = isFrontMirrorEnabled,
                                    swapFrontMirror = swapFrontMirror,
                                    cameraId = cameraId
                                )
                            if (continuation.isActive) {
                                continuation.resume(result)
                            } else if (!result.bitmap.isRecycled) {
                                result.bitmap.recycle()
                            }
                        } catch (t: Throwable) {
                            _cameraError.value = t.message ?: "Failed to decode captured frame"
                            if (continuation.isActive) {
                                continuation.resumeWithException(t)
                            }
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        _cameraError.value = exception.message ?: "Image capture failed"
                        if (continuation.isActive) {
                            continuation.resumeWithException(exception)
                        }
                    }
                }
            )
        }
    }

    suspend fun captureBurstFrames(
        count: Int = DEFAULT_D3D_BURST_COUNT,
        maxEdgePx: Int = BitmapDecodeMath.DEFAULT_MAX_LONG_EDGE,
        frameIntervalMs: Long = DEFAULT_BURST_INTERVAL_MS,
        isFrontMirrorEnabled: Boolean = true
    ): List<CapturedFrameResult> {
        val targetCount = count.coerceIn(1, MAX_BURST_COUNT)
        val frames = ArrayList<CapturedFrameResult>(targetCount)
        try {
            for (index in 0 until targetCount) {
                frames += captureCleanFrame(
                    maxEdgePx = maxEdgePx,
                    isFrontMirrorEnabled = isFrontMirrorEnabled
                )
                if (index < targetCount - 1 && frameIntervalMs > 0L) {
                    delay(frameIntervalMs)
                }
            }
            return frames
        } catch (t: Throwable) {
            frames.forEach { frame ->
                if (!frame.bitmap.isRecycled) {
                    frame.bitmap.recycle()
                }
            }
            throw t
        }
    }

    @SuppressLint("MissingPermission")
    fun startVideoRecording(
        outputOptions: OutputOptions,
        audioEnabled: Boolean = true,
        onRecordEvent: (VideoRecordEvent) -> Unit = {}
    ): Recording? {
        val capture = videoCapture
        if (capture == null) {
            _cameraError.value = "VideoCapture is not bound"
            return null
        }

        activeRecording?.stop()
        activeRecording = null

        val recorder = capture.output
        val pendingRecording =
            when (outputOptions) {
                is MediaStoreOutputOptions -> recorder.prepareRecording(appContext, outputOptions)
                is FileOutputOptions -> recorder.prepareRecording(appContext, outputOptions)
                is FileDescriptorOutputOptions -> recorder.prepareRecording(appContext, outputOptions)
                else -> {
                    _cameraError.value = "Unsupported OutputOptions: ${outputOptions::class.java.simpleName}"
                    return null
                }
            }

        val hasAudioPermission =
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (audioEnabled && hasAudioPermission) {
            pendingRecording.withAudioEnabled()
        }

        val recording =
            pendingRecording.start(mainExecutor) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> _isRecording.value = true
                    is VideoRecordEvent.Finalize -> {
                        _isRecording.value = false
                        activeRecording = null
                        if (event.hasError()) {
                            _cameraError.value =
                                event.cause?.message ?: "Video recording failed (error=${event.error})"
                        }
                    }
                }
                onRecordEvent(event)
            }
        activeRecording = recording
        return recording
    }

    fun stopVideoRecording() {
        activeRecording?.stop()
        activeRecording = null
        _isRecording.value = false
    }

    fun release() {
        stopVideoRecording()
        detachZoomObserver()
        cameraProvider?.unbindAll()
        boundCamera = null
        boundPreviewView = null
        imageCapture = null
        videoCapture = null
        _isCameraEffectActive.value = false
        captureExecutor.shutdown()
    }

    private data class BoundGroupResult(
        val camera: Camera,
        val imageCapture: ImageCapture?,
        val videoCapture: VideoCapture<Recorder>?,
        val effectActive: Boolean
    )

    private fun bindUseCaseGroupWithFallback(
        provider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        selector: CameraSelector,
        viewPort: ViewPort,
        preview: Preview,
        captureUseCase: ImageCapture,
        videoUseCase: VideoCapture<Recorder>?,
        effect: CameraEffect?,
        preferVideoMode: Boolean
    ): BoundGroupResult {
        val attempts =
            CameraPolicyMath.buildBindAttemptPlan(
                hasVideoUseCase = videoUseCase != null,
                hasEffect = effect != null,
                preferVideoMode = preferVideoMode
            )

        var lastError: Throwable? = null
        for (attempt in attempts) {
            val attemptImage = if (attempt.includeImageCapture) captureUseCase else null
            val attemptVideo = if (attempt.includeVideoCapture) videoUseCase else null
            val attemptEffect = if (attempt.includeEffect) effect else null
            try {
                val groupBuilder =
                    UseCaseGroup.Builder()
                        .setViewPort(viewPort)
                        .addUseCase(preview)
                attemptImage?.let { groupBuilder.addUseCase(it) }
                attemptVideo?.let { groupBuilder.addUseCase(it) }
                attemptEffect?.let { groupBuilder.addEffect(it) }

                provider.unbindAll()
                val camera = provider.bindToLifecycle(lifecycleOwner, selector, groupBuilder.build())
                val fallbackError =
                    CameraPolicyMath.resolveBindFallbackError(
                        requestedVideoUseCase = videoUseCase != null,
                        boundVideoUseCase = attemptVideo != null,
                        requestedEffect = effect != null,
                        boundEffect = attemptEffect != null,
                        preferVideoMode = preferVideoMode
                    )
                if (fallbackError != null) {
                    _cameraError.value = fallbackError
                }
                return BoundGroupResult(
                    camera = camera,
                    imageCapture = attemptImage,
                    videoCapture = attemptVideo,
                    effectActive = attemptEffect != null
                )
            } catch (t: Throwable) {
                lastError = t
            }
        }

        val failure = lastError ?: IllegalStateException("Failed to bind camera use cases")
        _cameraError.value = failure.message ?: "Failed to bind camera use cases"
        throw failure
    }

    private fun startFocusAndMetering(camera: Camera, point: androidx.camera.core.MeteringPoint) {
        val action =
            FocusMeteringAction.Builder(
                point,
                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
            )
                .setAutoCancelDuration(FOCUS_AUTO_CANCEL_SECONDS, TimeUnit.SECONDS)
                .build()
        camera.cameraControl.startFocusAndMetering(action)
    }

    private fun attachZoomObserver(camera: Camera) {
        val zoomLiveData = camera.cameraInfo.zoomState
        zoomLiveData.value?.let(::updateZoomState)
        val attachTask = Runnable {
            detachZoomObserverImmediate()
            val observer = Observer<ZoomState> { state -> updateZoomState(state) }
            observedZoomLiveData = zoomLiveData
            zoomObserver = observer
            zoomLiveData.observeForever(observer)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            attachTask.run()
        } else {
            mainExecutor.execute(attachTask)
        }
    }

    private fun detachZoomObserver() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            detachZoomObserverImmediate()
        } else {
            mainExecutor.execute(::detachZoomObserverImmediate)
        }
    }

    private fun detachZoomObserverImmediate() {
        val liveData = observedZoomLiveData
        val observer = zoomObserver
        if (liveData != null && observer != null) {
            liveData.removeObserver(observer)
        }
        observedZoomLiveData = null
        zoomObserver = null
    }

    private fun updateZoomState(state: ZoomState) {
        _zoomUiState.value =
            ZoomUiState(
                zoomRatio = state.zoomRatio,
                linearZoom = state.linearZoom,
                bounds =
                ZoomBounds(
                    minZoomRatio = state.minZoomRatio,
                    maxZoomRatio = state.maxZoomRatio
                )
            )
    }

    private fun extractJpegBytes(image: ImageProxy): ByteArray {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return bytes
    }

    private fun decodeJpegBytes(
        bytes: ByteArray,
        maxEdgePx: Int,
        frontCamera: Boolean,
        mirrorFrontCamera: Boolean,
        swapFrontMirror: Boolean,
        cameraId: String
    ): CapturedFrameResult {
        val exifOrientation =
            try {
                ByteArrayInputStream(bytes).use { stream ->
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                }
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }

        val boundsOptions =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)

        val sampleSize =
            BitmapDecodeMath.computeInSampleSize(
                rawWidth = boundsOptions.outWidth,
                rawHeight = boundsOptions.outHeight,
                maxLongEdge = maxEdgePx
            )

        val decodeOptions =
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        val decoded =
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
                ?: throw IllegalStateException("BitmapFactory failed to decode JPEG frame")

        val transform =
            BitmapDecodeMath.computeExifTransform(
                exifOrientation = exifOrientation,
                isFrontCamera = frontCamera,
                mirrorFrontCamera = mirrorFrontCamera,
                swapFrontMirror = swapFrontMirror
            )
        val finalBitmap = applyBitmapTransform(decoded, transform)
        if (finalBitmap !== decoded) {
            decoded.recycle()
        }

        return CapturedFrameResult(
            bitmap = finalBitmap,
            metadata =
            CaptureMetadata(
                width = finalBitmap.width,
                height = finalBitmap.height,
                rotationDegrees = transform.rotationDegrees,
                isFrontCamera = frontCamera,
                capturedAtEpochMs = System.currentTimeMillis(),
                cameraId = cameraId
            )
        )
    }

    private fun applyBitmapTransform(source: Bitmap, transform: ExifTransform): Bitmap {
        if (transform.rotationDegrees == 0 && !transform.mirrorHorizontal) {
            return source
        }
        val matrix = Matrix()
        if (transform.rotationDegrees != 0) {
            matrix.postRotate(transform.rotationDegrees.toFloat())
        }
        if (transform.mirrorHorizontal) {
            matrix.postScale(-1f, 1f)
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun <T> applyIspNeutralization(
        extender: Camera2Interop.Extender<T>,
        bounds: CameraHardwareBounds?,
        quirkFlags: DeviceQuirkFlags
    ) {
        if (!quirkFlags.disableEdgeOffOverride) {
            bounds?.preferredEdgeMode?.let { mode ->
                extender.setCaptureRequestOption(CaptureRequest.EDGE_MODE, mode)
            }
        }
        if (!quirkFlags.disableNoiseReductionOffOverride) {
            bounds?.preferredNoiseReductionMode?.let { mode ->
                extender.setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, mode)
            }
        }
    }

    private fun buildCameraSelector(useFrontCamera: Boolean): CameraSelector {
        val lensFacing =
            if (useFrontCamera) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
        return CameraSelector.Builder().requireLensFacing(lensFacing).build()
    }

    private fun probeSelectedCamera(
        provider: ProcessCameraProvider,
        selector: CameraSelector,
        quirkFlags: DeviceQuirkFlags
    ): CameraProbeResult? {
        val selectedCameraId = findCameraIdForSelector(provider, selector) ?: return null
        return Camera2CapabilityProbe.probe(cameraManager, selectedCameraId, quirkFlags)
    }

    private fun buildResolutionSelector(cameraXAspectRatio: Int): ResolutionSelector {
        val strategy =
            if (cameraXAspectRatio == androidx.camera.core.AspectRatio.RATIO_16_9) {
                AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
            } else {
                AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
            }
        return ResolutionSelector.Builder()
            .setAspectRatioStrategy(strategy)
            .build()
    }

    private fun buildFallbackViewPort(aspectRatio: AspectRatioPreset, targetRotation: Int): ViewPort {
        val isPortrait = targetRotation == Surface.ROTATION_0 || targetRotation == Surface.ROTATION_180
        val rational =
            if (isPortrait) {
                Rational(aspectRatio.heightRatio, aspectRatio.widthRatio)
            } else {
                Rational(aspectRatio.widthRatio, aspectRatio.heightRatio)
            }
        return ViewPort.Builder(rational, targetRotation)
            .setScaleType(ViewPort.FILL_CENTER)
            .build()
    }

    private fun resolveAwbMode(preset: WhiteBalancePreset, supportedModes: Set<Int>): Int {
        val desired =
            when (preset) {
                WhiteBalancePreset.AUTO -> CaptureRequest.CONTROL_AWB_MODE_AUTO
                WhiteBalancePreset.DAYLIGHT -> CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT
                WhiteBalancePreset.CLOUDY -> CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
                WhiteBalancePreset.TUNGSTEN -> CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT
                WhiteBalancePreset.FLUORESCENT -> CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT
            }
        return if (supportedModes.isEmpty() || supportedModes.contains(desired)) {
            desired
        } else {
            CaptureRequest.CONTROL_AWB_MODE_AUTO
        }
    }

    private fun findCameraIdForSelector(provider: ProcessCameraProvider, selector: CameraSelector): String? = try {
        val matched = selector.filter(provider.availableCameraInfos)
        matched.firstOrNull()?.let { info ->
            Camera2CameraInfo.from(info).cameraId
        }
    } catch (_: Exception) {
        null
    }

    private suspend fun getOrAwaitCameraProvider(): ProcessCameraProvider =
        cameraProvider ?: suspendCancellableCoroutine { continuation ->
            val future = ProcessCameraProvider.getInstance(appContext)
            future.addListener(
                {
                    try {
                        val provider = future.get()
                        if (continuation.isActive) {
                            continuation.resume(provider)
                        }
                    } catch (t: Throwable) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(t)
                        }
                    }
                },
                mainExecutor
            )
        }

    private fun currentDeviceFingerprint(): DeviceFingerprint = DeviceFingerprint(
        manufacturer = Build.MANUFACTURER.orEmpty(),
        brand = Build.BRAND.orEmpty(),
        model = Build.MODEL.orEmpty(),
        device = Build.DEVICE.orEmpty(),
        hardware = Build.HARDWARE.orEmpty(),
        sdkInt = Build.VERSION.SDK_INT
    )

    companion object {
        const val DEFAULT_D3D_BURST_COUNT = 4
        const val MAX_BURST_COUNT = 12
        const val DEFAULT_BURST_INTERVAL_MS = 120L
        private const val FOCUS_AUTO_CANCEL_SECONDS = 3L
        private const val DEFAULT_MIN_ISO = 100
        private const val DEFAULT_MAX_ISO = 1600
        private const val DEFAULT_MIN_EXPOSURE_NS = 1_000_000L
        private const val DEFAULT_MAX_EXPOSURE_NS = 250_000_000L

        val DEFAULT_VIDEO_QUALITY_SELECTOR: QualitySelector =
            QualitySelector.from(
                Quality.FHD,
                FallbackStrategy.lowerQualityOrHigherThan(Quality.HD)
            )
    }
}
