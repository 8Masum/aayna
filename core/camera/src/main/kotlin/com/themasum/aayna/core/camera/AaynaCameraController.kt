package com.themasum.aayna.core.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ExifInterface
import android.view.Surface
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.themasum.aayna.core.model.CameraCapabilityInfo
import com.themasum.aayna.core.model.ExposureMath
import com.themasum.aayna.core.model.FlashMode
import com.themasum.aayna.core.model.OpticalSettings
import com.themasum.aayna.core.model.ShaderExposureFallback
import com.themasum.aayna.core.model.WhiteBalancePreset
import java.io.ByteArrayInputStream
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
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

@OptIn(ExperimentalCamera2Interop::class)
class AaynaCameraController(context: Context) {
    private val appContext: Context = context.applicationContext
    private val mainExecutor: Executor = ContextCompat.getMainExecutor(appContext)
    private val captureExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val cameraManager: CameraManager =
        appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private val _activeCapabilityInfo = MutableStateFlow<CameraCapabilityInfo?>(null)
    val activeCapabilityInfo: StateFlow<CameraCapabilityInfo?> = _activeCapabilityInfo.asStateFlow()

    private val _isFrontCamera = MutableStateFlow(false)
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera.asStateFlow()

    private val _isTorchActive = MutableStateFlow(false)
    val isTorchActive: StateFlow<Boolean> = _isTorchActive.asStateFlow()

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
    private var imageCapture: ImageCapture? = null
    private var activeBounds: CameraHardwareBounds? = null

    suspend fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        useFrontCamera: Boolean = _isFrontCamera.value,
        surfaceTextureProvider: (width: Int, height: Int) -> SurfaceTexture?,
        onTransformationInfo: (PreviewSurfaceConfig) -> Unit = {}
    ) {
        _cameraError.value = null
        val provider = getOrAwaitCameraProvider()
        cameraProvider = provider

        val lensFacing =
            if (useFrontCamera) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        val selectedCameraId = findCameraIdForSelector(provider, selector)
        val probeResult =
            if (selectedCameraId != null) {
                Camera2CapabilityProbe.probe(cameraManager, selectedCameraId)
            } else {
                null
            }
        val bounds = probeResult?.bounds
        activeBounds = bounds
        _activeCapabilityInfo.value = probeResult?.capabilityInfo
        _isFrontCamera.value = useFrontCamera
        _isTorchActive.value = false

        val previewBuilder = Preview.Builder()
        applyIspNeutralization(previewBuilder, bounds)
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
        applyIspNeutralization(captureBuilder, bounds)
        val captureUseCase = captureBuilder.build()

        try {
            provider.unbindAll()
            boundCamera = provider.bindToLifecycle(lifecycleOwner, selector, preview, captureUseCase)
            imageCapture = captureUseCase
        } catch (t: Throwable) {
            _cameraError.value = t.message ?: "Failed to bind camera use cases"
            throw t
        }
    }

    fun applyOpticalSettings(settings: OpticalSettings) {
        val bounds = activeBounds
        val hasManualSensor = bounds?.hasManualSensor == true
        val fallback =
            ExposureMath.computeShaderFallbackGains(
                hasManualSensor = hasManualSensor,
                ev = settings.evCompensation,
                iso = settings.iso,
                shutterNs = settings.shutterSpeedNs
            )
        _shaderExposureFallback.value = fallback

        val camera = boundCamera ?: return
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

        if (bounds != null && hasManualSensor && (settings.iso != null || settings.shutterSpeedNs != null)) {
            optionsBuilder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_OFF
            )
            ExposureMath.clampIso(settings.iso, bounds.minIso, bounds.maxIso)?.let { clampedIso ->
                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.SENSOR_SENSITIVITY,
                    clampedIso
                )
            }
            ExposureMath.clampExposureTimeNs(
                settings.shutterSpeedNs,
                bounds.minExposureTimeNs,
                bounds.maxExposureTimeNs
            )?.let { clampedNs ->
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
            if (bounds != null) {
                val evIndex =
                    ExposureMath.evToCompensationSteps(
                        ev = settings.evCompensation,
                        stepNumerator = bounds.aeCompensationStepNumerator,
                        stepDenominator = bounds.aeCompensationStepDenominator,
                        minIndex = bounds.aeCompensationMinIndex,
                        maxIndex = bounds.aeCompensationMaxIndex
                    )
                camera.cameraControl.setExposureCompensationIndex(evIndex)
            }
        }

        camera2Control.setCaptureRequestOptions(optionsBuilder.build())
    }

    fun tapToFocus(normalizedX: Float, normalizedY: Float, viewWidth: Int, viewHeight: Int) {
        val camera = boundCamera ?: return
        if (viewWidth <= 0 || viewHeight <= 0) return

        val clampedX = normalizedX.coerceIn(0f, 1f) * viewWidth.toFloat()
        val clampedY = normalizedY.coerceIn(0f, 1f) * viewHeight.toFloat()
        val factory = SurfaceOrientedMeteringPointFactory(viewWidth.toFloat(), viewHeight.toFloat())
        val point = factory.createPoint(clampedX, clampedY)
        val action =
            FocusMeteringAction.Builder(
                point,
                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
            )
                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                .build()
        camera.cameraControl.startFocusAndMetering(action)
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

    suspend fun captureCleanFrame(maxEdgePx: Int = BitmapDecodeMath.DEFAULT_MAX_LONG_EDGE): CapturedFrameResult {
        val capture =
            imageCapture ?: throw IllegalStateException("ImageCapture is not bound")
        val frontCamera = _isFrontCamera.value
        val cameraId = activeBounds?.cameraId ?: "unknown"

        return suspendCancellableCoroutine { continuation ->
            capture.takePicture(
                captureExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        try {
                            val result = decodeImageProxy(image, maxEdgePx, frontCamera, cameraId)
                            if (continuation.isActive) {
                                continuation.resume(result)
                            }
                        } catch (t: Throwable) {
                            _cameraError.value = t.message ?: "Failed to decode captured frame"
                            if (continuation.isActive) {
                                continuation.resumeWithException(t)
                            }
                        } finally {
                            image.close()
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

    fun release() {
        cameraProvider?.unbindAll()
        boundCamera = null
        imageCapture = null
        captureExecutor.shutdown()
    }

    private fun decodeImageProxy(
        image: ImageProxy,
        maxEdgePx: Int,
        frontCamera: Boolean,
        cameraId: String
    ): CapturedFrameResult {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

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

        val transform = BitmapDecodeMath.computeExifTransform(exifOrientation, frontCamera)
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
        if (transform.mirrorHorizontal) {
            matrix.postScale(-1f, 1f, source.width / 2f, source.height / 2f)
        }
        if (transform.rotationDegrees != 0) {
            matrix.postRotate(transform.rotationDegrees.toFloat())
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun applyIspNeutralization(builder: Preview.Builder, bounds: CameraHardwareBounds?) {
        val extender = Camera2Interop.Extender(builder)
        bounds?.preferredEdgeMode?.let { mode ->
            extender.setCaptureRequestOption(CaptureRequest.EDGE_MODE, mode)
        }
        bounds?.preferredNoiseReductionMode?.let { mode ->
            extender.setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, mode)
        }
    }

    private fun applyIspNeutralization(builder: ImageCapture.Builder, bounds: CameraHardwareBounds?) {
        val extender = Camera2Interop.Extender(builder)
        bounds?.preferredEdgeMode?.let { mode ->
            extender.setCaptureRequestOption(CaptureRequest.EDGE_MODE, mode)
        }
        bounds?.preferredNoiseReductionMode?.let { mode ->
            extender.setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, mode)
        }
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
}
