package com.themasum.aayna.feature.viewfinder

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.GLSurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.themasum.aayna.core.camera.AaynaCameraController
import com.themasum.aayna.core.gl.AaynaViewfinderGlRenderer
import com.themasum.aayna.core.gl.GlRenderParams
import com.themasum.aayna.core.gl.OffscreenGlExporter
import com.themasum.aayna.core.model.CameraCatalog
import com.themasum.aayna.core.model.CaptureRecipe
import com.themasum.aayna.core.model.CaptureStorageMode
import com.themasum.aayna.core.model.DateStampFormatter
import com.themasum.aayna.core.model.FlashMode
import com.themasum.aayna.core.model.GlDiagnosticsInfo
import com.themasum.aayna.core.model.PassShaderStatus
import com.themasum.aayna.core.model.TimerDuration
import com.themasum.aayna.core.storage.AaynaLogger
import com.themasum.aayna.core.storage.AaynaPreferencesRepository
import com.themasum.aayna.core.storage.AaynaSoundEngine
import com.themasum.aayna.core.storage.FilmRollRepository
import com.themasum.aayna.core.storage.MediaStoreExporter
import com.themasum.aayna.core.storage.NegativeRepository
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ViewfinderRoute(
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    cameraController: AaynaCameraController,
    offscreenExporter: OffscreenGlExporter,
    preferencesRepository: AaynaPreferencesRepository,
    negativeRepository: NegativeRepository,
    filmRollRepository: FilmRollRepository,
    mediaStoreExporter: MediaStoreExporter,
    soundEngine: AaynaSoundEngine,
    logger: AaynaLogger,
    onGlDiagnosticsUpdated: (GlDiagnosticsInfo?, List<PassShaderStatus>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val settingsSnapshot by preferencesRepository.settingsFlow.collectAsState(
        initial = ViewfinderUiState().settings
    )
    val savedCameraId by preferencesRepository.activeCameraIdFlow.collectAsState(
        initial = CameraCatalog.defaultCamera.id
    )
    val activeRoll by filmRollRepository.activeRollFlow.collectAsState(initial = null)
    val fallbackGains by cameraController.shaderExposureFallback.collectAsState()

    var uiState by remember { mutableStateOf(ViewfinderUiState()) }
    var initializedFromPrefs by remember { mutableStateOf(false) }

    LaunchedEffect(savedCameraId) {
        if (!initializedFromPrefs) {
            val profile = CameraCatalog.findByIdOrDefault(savedCameraId)
            uiState =
                ViewfinderStateReducer.switchCamera(uiState, profile).copy(
                    isGridVisible = settingsSnapshot.gridEnabled
                )
            initializedFromPrefs = true
        }
    }

    LaunchedEffect(settingsSnapshot) {
        uiState =
            ViewfinderStateReducer.applyUserSettings(
                state = uiState,
                settings = settingsSnapshot,
                syncGridFromSettings = !initializedFromPrefs
            )
    }

    LaunchedEffect(activeRoll) {
        val roll = activeRoll
        uiState =
            uiState.copy(
                activeRollId = roll?.rollId,
                rollShotsTaken = roll?.shotsTaken ?: 0,
                rollCapacity = roll?.capacity ?: settingsSnapshot.rollCapacity
            )
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionRequestedOnce by remember { mutableStateOf(false) }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasCameraPermission = granted
            if (!granted) {
                logger.logWarn("Viewfinder", "CAMERA permission denied by user")
            }
        }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission && !permissionRequestedOnce) {
            permissionRequestedOnce = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val glSurfaceViewRef = remember { mutableStateOf<GLSurfaceView?>(null) }
    var readySurfaceTexture by remember { mutableStateOf<SurfaceTexture?>(null) }
    var viewfinderSize by remember { mutableStateOf(IntSize.Zero) }

    val renderer =
        remember(context) {
            AaynaViewfinderGlRenderer(
                assetManager = context.assets,
                onSurfaceTextureReady = { surfaceTexture ->
                    readySurfaceTexture = surfaceTexture
                },
                onFrameAvailable = {
                    glSurfaceViewRef.value?.requestRender()
                }
            )
        }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> glSurfaceViewRef.value?.onResume()
                    Lifecycle.Event.ON_PAUSE -> glSurfaceViewRef.value?.onPause()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            val glView = glSurfaceViewRef.value
            if (glView != null) {
                glView.queueEvent { renderer.release() }
            } else {
                renderer.release()
            }
        }
    }

    val stampText =
        remember(uiState.stampConfig, uiState.activeCamera.eraYearOffset) {
            DateStampFormatter.format(
                epochMillis = System.currentTimeMillis(),
                format = uiState.stampConfig.format,
                useEraYearOffset = uiState.stampConfig.useEraYearOffset,
                eraYear = uiState.activeCamera.eraYearOffset,
                customText = uiState.stampConfig.customText
            )
        }

    val renderParams =
        remember(
            uiState.activeCamera,
            uiState.opticalSettings,
            uiState.stampConfig,
            stampText,
            fallbackGains,
            uiState.isPreviewHoldOriginal,
            uiState.randomSeed,
            uiState.inputAspectRatio
        ) {
            GlRenderParams(
                profile = uiState.activeCamera,
                opticalSettings = uiState.opticalSettings,
                stampConfig = uiState.stampConfig,
                stampText = stampText,
                fallbackGains = fallbackGains,
                isPreviewHoldOriginal = uiState.isPreviewHoldOriginal,
                splitCompareRatio = 0f,
                randomSeed = uiState.randomSeed,
                inputAspectRatio = uiState.inputAspectRatio
            )
        }

    LaunchedEffect(renderParams) {
        renderer.updateParams(renderParams)
        glSurfaceViewRef.value?.requestRender()
        val (glInfo, shaderStatuses) = renderer.getDiagnostics()
        onGlDiagnosticsUpdated(glInfo, shaderStatuses)
    }

    LaunchedEffect(uiState.opticalSettings) {
        cameraController.applyOpticalSettings(uiState.opticalSettings)
    }

    LaunchedEffect(hasCameraPermission, readySurfaceTexture, uiState.isFrontCamera) {
        val st = readySurfaceTexture
        if (!hasCameraPermission || st == null) return@LaunchedEffect
        try {
            cameraController.bindCamera(
                lifecycleOwner = lifecycleOwner,
                useFrontCamera = uiState.isFrontCamera,
                surfaceTextureProvider = { _, _ -> st },
                onTransformationInfo = { config ->
                    val ratio =
                        if (config.rotationDegrees == 90 || config.rotationDegrees == 270) {
                            config.height.toFloat() / config.width.coerceAtLeast(1)
                        } else {
                            config.width.toFloat() / config.height.coerceAtLeast(1)
                        }
                    uiState = uiState.copy(inputAspectRatio = ratio)
                }
            )
            cameraController.applyOpticalSettings(uiState.opticalSettings)
            val (glInfo, shaderStatuses) = renderer.getDiagnostics()
            onGlDiagnosticsUpdated(glInfo, shaderStatuses)
        } catch (t: Throwable) {
            logger.logError("Viewfinder", "Failed to bind CameraX", t)
            uiState = uiState.copy(lastErrorMessage = t.message ?: "Camera binding failed")
        }
    }

    LaunchedEffect(uiState.focusReticle?.triggeredAtMs) {
        val reticle = uiState.focusReticle ?: return@LaunchedEffect
        delay(1500L)
        if (uiState.focusReticle?.triggeredAtMs == reticle.triggeredAtMs) {
            uiState = uiState.copy(focusReticle = null)
        }
    }

    LaunchedEffect(uiState.statusBannerMessage) {
        val msg = uiState.statusBannerMessage ?: return@LaunchedEffect
        delay(2200L)
        if (uiState.statusBannerMessage == msg) {
            uiState = uiState.copy(statusBannerMessage = null)
        }
    }

    fun triggerDialFeedback() {
        if (uiState.settings.hapticsEnabled) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        soundEngine.playDialTick(uiState.settings.sfxEnabled)
    }

    fun executeCapture() {
        if (uiState.isCapturing || !hasCameraPermission) return
        scope.launch {
            uiState = uiState.copy(isCapturing = true, lastErrorMessage = null)
            try {
                if (uiState.timerDuration != TimerDuration.OFF) {
                    for (sec in uiState.timerDuration.seconds downTo 1) {
                        uiState = uiState.copy(countdownRemainingSeconds = sec)
                        soundEngine.playDialTick(uiState.settings.sfxEnabled)
                        delay(1000L)
                    }
                    uiState = uiState.copy(countdownRemainingSeconds = null)
                }

                if (uiState.settings.hapticsEnabled) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                soundEngine.playShutter(
                    soundAssetName = uiState.activeCamera.soundAssetName,
                    sfxEnabled = uiState.settings.sfxEnabled
                )
                uiState = uiState.copy(showCaptureFlashPulse = true)
                scope.launch {
                    delay(140L)
                    uiState = uiState.copy(showCaptureFlashPulse = false)
                }

                val capturedAtMs = System.currentTimeMillis()
                val recipe =
                    CaptureRecipe(
                        recipeId = UUID.randomUUID().toString(),
                        cameraId = uiState.activeCamera.id,
                        capturedAtEpochMs = capturedAtMs,
                        randomSeed = uiState.randomSeed,
                        resolutionPreset = uiState.settings.resolutionPreset,
                        opticalSettings = uiState.opticalSettings,
                        stampConfig = uiState.stampConfig
                    )
                val captureStampText =
                    DateStampFormatter.format(
                        epochMillis = capturedAtMs,
                        format = recipe.stampConfig.format,
                        useEraYearOffset = recipe.stampConfig.useEraYearOffset,
                        eraYear = uiState.activeCamera.eraYearOffset,
                        customText = recipe.stampConfig.customText
                    )

                val captured =
                    cameraController.captureCleanFrame(
                        maxEdgePx = uiState.settings.resolutionPreset.maxLongEdgePx
                    )
                var renderedBitmap: Bitmap? = null
                var thumbBitmap: Bitmap? = null
                try {
                    when (uiState.settings.storageMode) {
                        CaptureStorageMode.INSTANT_DIGICAM -> {
                            renderedBitmap =
                                offscreenExporter.renderBitmap(
                                    sourceBitmap = captured.bitmap,
                                    profile = uiState.activeCamera,
                                    recipe = recipe,
                                    stampText = captureStampText,
                                    fallbackGains = fallbackGains
                                )
                            val uri =
                                mediaStoreExporter.saveJpegToGallery(
                                    bitmap = renderedBitmap,
                                    profile = uiState.activeCamera,
                                    recipe = recipe
                                )
                            if (uiState.settings.dualSaveEnabled) {
                                mediaStoreExporter.saveJpegToGallery(
                                    bitmap = captured.bitmap,
                                    profile = uiState.activeCamera,
                                    recipe = recipe,
                                    suffix = "ORIG"
                                )
                            }
                            uiState =
                                uiState.copy(
                                    lastCapturedUri = uri?.toString(),
                                    statusBannerMessage = "Saved to Pictures/Aayna"
                                )
                        }

                        CaptureStorageMode.NON_DESTRUCTIVE_ROLL -> {
                            thumbBitmap = createDownscaledThumbnail(captured.bitmap, maxEdge = 360)
                            negativeRepository.saveNegative(
                                cleanBitmap = captured.bitmap,
                                thumbnailBitmap = thumbBitmap,
                                recipe = recipe
                            )
                            if (uiState.settings.dualSaveEnabled) {
                                mediaStoreExporter.saveJpegToGallery(
                                    bitmap = captured.bitmap,
                                    profile = uiState.activeCamera,
                                    recipe = recipe,
                                    suffix = "ORIG"
                                )
                            }
                            uiState =
                                uiState.copy(
                                    statusBannerMessage = "Negative saved to Studio"
                                )
                        }

                        CaptureStorageMode.ANALOG_DEVELOPING_RITUAL -> {
                            val activeRollState =
                                filmRollRepository.getOrCreateActiveRoll(
                                    cameraId = uiState.activeCamera.id,
                                    capacity = uiState.settings.rollCapacity,
                                    nowEpochMs = capturedAtMs
                                )
                            if (activeRollState.shotsTaken >= activeRollState.capacity) {
                                uiState = uiState.copy(showRollCompletePrompt = true)
                                return@launch
                            }
                            val advanceResult =
                                filmRollRepository.recordShot(
                                    cameraId = uiState.activeCamera.id,
                                    capacity = uiState.settings.rollCapacity,
                                    nowEpochMs = capturedAtMs
                                )
                            soundEngine.playFilmWind(uiState.settings.sfxEnabled)
                            uiState =
                                uiState.copy(
                                    developingOverlay =
                                    DevelopingOverlayState(
                                        frameNumber = advanceResult.assignedFrameNumber,
                                        capacity = advanceResult.updatedState.capacity,
                                        cameraCodeName = uiState.activeCamera.codeName
                                    )
                                )
                            val ritualDelay = async { delay(1500L) }
                            renderedBitmap =
                                offscreenExporter.renderBitmap(
                                    sourceBitmap = captured.bitmap,
                                    profile = uiState.activeCamera,
                                    recipe = recipe,
                                    stampText = captureStampText,
                                    fallbackGains = fallbackGains
                                )
                            val exportedUri =
                                mediaStoreExporter.saveJpegToGallery(
                                    bitmap = renderedBitmap,
                                    profile = uiState.activeCamera,
                                    recipe = recipe
                                )
                            if (uiState.settings.dualSaveEnabled) {
                                mediaStoreExporter.saveJpegToGallery(
                                    bitmap = captured.bitmap,
                                    profile = uiState.activeCamera,
                                    recipe = recipe,
                                    suffix = "ORIG"
                                )
                            }
                            thumbBitmap = createDownscaledThumbnail(captured.bitmap, maxEdge = 360)
                            negativeRepository.saveNegative(
                                cleanBitmap = captured.bitmap,
                                thumbnailBitmap = thumbBitmap,
                                recipe = recipe,
                                rollId = advanceResult.updatedState.rollId,
                                frameNumber = advanceResult.assignedFrameNumber,
                                exportedUri = exportedUri?.toString()
                            )
                            ritualDelay.await()
                            uiState =
                                uiState.copy(
                                    developingOverlay = null,
                                    lastCapturedUri = exportedUri?.toString(),
                                    rollShotsTaken = advanceResult.updatedState.shotsTaken,
                                    rollCapacity = advanceResult.updatedState.capacity,
                                    showRollCompletePrompt = advanceResult.rollCompleted,
                                    statusBannerMessage =
                                    "EXP ${advanceResult.assignedFrameNumber}/" +
                                        "${advanceResult.updatedState.capacity} developed"
                                )
                        }
                    }
                } finally {
                    if (thumbBitmap != null && thumbBitmap !== captured.bitmap && !thumbBitmap.isRecycled) {
                        thumbBitmap.recycle()
                    }
                    if (renderedBitmap != null && !renderedBitmap.isRecycled) {
                        renderedBitmap.recycle()
                    }
                    if (!captured.bitmap.isRecycled) {
                        captured.bitmap.recycle()
                    }
                }
            } catch (t: Throwable) {
                logger.logError("Viewfinder", "Capture failed", t)
                uiState =
                    uiState.copy(
                        developingOverlay = null,
                        countdownRemainingSeconds = null,
                        lastErrorMessage = t.message ?: "Capture failed",
                        statusBannerMessage = "Capture failed"
                    )
            } finally {
                uiState =
                    uiState.copy(
                        isCapturing = false,
                        countdownRemainingSeconds = null
                    )
            }
        }
    }

    val skin = uiState.activeCamera.skin
    val accent = parseHexColor(skin.accentHex, Color(0xFF4CD4E8))
    val bezelColor = parseHexColor(skin.bodySecondaryHex, Color(0xFF25282E))

    Box(
        modifier =
        modifier
            .fillMaxSize()
            .cameraChassisSurface(skin)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ViewfinderTopDeck(
                state = uiState,
                onCycleFlash = {
                    triggerDialFeedback()
                    uiState =
                        uiState.copy(
                            opticalSettings =
                            uiState.opticalSettings.copy(
                                flashMode =
                                ViewfinderStateReducer.cycleFlashMode(
                                    uiState.opticalSettings.flashMode
                                )
                            )
                        )
                },
                onCycleAspect = {
                    triggerDialFeedback()
                    uiState =
                        uiState.copy(
                            opticalSettings =
                            uiState.opticalSettings.copy(
                                aspectRatio =
                                ViewfinderStateReducer.cycleAspectRatio(
                                    uiState.opticalSettings.aspectRatio
                                )
                            )
                        )
                },
                onCycleTimer = {
                    triggerDialFeedback()
                    uiState =
                        uiState.copy(
                            timerDuration = ViewfinderStateReducer.cycleTimer(uiState.timerDuration)
                        )
                },
                onToggleGrid = {
                    triggerDialFeedback()
                    val nextGrid = !uiState.isGridVisible
                    uiState = uiState.copy(isGridVisible = nextGrid)
                    scope.launch { preferencesRepository.setGridEnabled(nextGrid) }
                },
                onToggleCameraShelf = {
                    triggerDialFeedback()
                    uiState =
                        uiState.copy(
                            activePanel =
                            if (uiState.activePanel == ViewfinderPanel.CAMERA_SHELF) {
                                ViewfinderPanel.NONE
                            } else {
                                ViewfinderPanel.CAMERA_SHELF
                            }
                        )
                },
                onOpenSettings = onOpenSettings
            )

            Box(
                modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black)
                    .border(2.5.dp, bezelColor, RoundedCornerShape(14.dp))
                    .onSizeChanged { viewfinderSize = it }
                    .pointerInput(hasCameraPermission) {
                        if (!hasCameraPermission) return@pointerInput
                        detectTapGestures(
                            onPress = {
                                val holdJob =
                                    scope.launch {
                                        delay(180L)
                                        uiState = uiState.copy(isPreviewHoldOriginal = true)
                                    }
                                tryAwaitRelease()
                                holdJob.cancel()
                                if (uiState.isPreviewHoldOriginal) {
                                    uiState = uiState.copy(isPreviewHoldOriginal = false)
                                }
                            },
                            onTap = { offset ->
                                if (viewfinderSize.width > 0 && viewfinderSize.height > 0) {
                                    val nx = (offset.x / viewfinderSize.width).coerceIn(0f, 1f)
                                    val ny = (offset.y / viewfinderSize.height).coerceIn(0f, 1f)
                                    cameraController.tapToFocus(
                                        normalizedX = nx,
                                        normalizedY = ny,
                                        viewWidth = viewfinderSize.width,
                                        viewHeight = viewfinderSize.height
                                    )
                                    uiState =
                                        uiState.copy(
                                            focusReticle =
                                            FocusReticleState(
                                                normalizedX = nx,
                                                normalizedY = ny,
                                                triggeredAtMs = System.currentTimeMillis()
                                            )
                                        )
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (hasCameraPermission) {
                    AndroidView(
                        factory = { ctx ->
                            GLSurfaceView(ctx).apply {
                                setEGLContextClientVersion(3)
                                setRenderer(renderer)
                                renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                                glSurfaceViewRef.value = this
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CameraPermissionCard(
                        accent = accent,
                        onRequestPermission = {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        onOpenGallery = onOpenGallery,
                        modifier = Modifier.padding(20.dp)
                    )
                }

                if (uiState.isGridVisible && hasCameraPermission) {
                    RuleOfThirdsGridOverlay(modifier = Modifier.fillMaxSize())
                }

                uiState.focusReticle?.let { reticle ->
                    FocusReticleOverlay(
                        reticle = reticle,
                        accent = accent,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (uiState.isPreviewHoldOriginal) {
                    Box(
                        modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.72f))
                            .border(1.dp, accent, RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "ORIGINAL (UNGRADED)",
                            color = accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                uiState.statusBannerMessage?.let { banner ->
                    Box(
                        modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 10.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF14161B).copy(alpha = 0.88f))
                            .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = banner,
                            color = Color(0xFFF3F4F6),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = uiState.showCaptureFlashPulse,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    val pulseColor =
                        if (uiState.opticalSettings.flashMode == FlashMode.OPTICAL_FLASH_SIM) {
                            Color.White.copy(alpha = 0.85f)
                        } else {
                            Color.Black.copy(alpha = 0.55f)
                        }
                    Box(
                        modifier =
                        Modifier
                            .fillMaxSize()
                            .background(pulseColor)
                    )
                }
            }

            HardwareLcdStrip(state = uiState)

            AnimatedVisibility(visible = uiState.activePanel == ViewfinderPanel.CONTROLS) {
                ViewfinderControlsDrawer(
                    state = uiState,
                    onSelectTab = { tab ->
                        triggerDialFeedback()
                        uiState = uiState.copy(activeControlTab = tab)
                    },
                    onStepEv = { delta ->
                        triggerDialFeedback()
                        uiState =
                            uiState.copy(
                                opticalSettings =
                                uiState.opticalSettings.copy(
                                    evCompensation =
                                    ViewfinderStateReducer.stepEv(
                                        uiState.opticalSettings.evCompensation,
                                        delta
                                    )
                                )
                            )
                    },
                    onSelectIso = { iso ->
                        triggerDialFeedback()
                        uiState =
                            uiState.copy(
                                opticalSettings = uiState.opticalSettings.copy(iso = iso)
                            )
                    },
                    onSelectShutter = { shutterNs ->
                        triggerDialFeedback()
                        uiState =
                            uiState.copy(
                                opticalSettings =
                                uiState.opticalSettings.copy(shutterSpeedNs = shutterNs)
                            )
                    },
                    onSelectWhiteBalance = { wb ->
                        triggerDialFeedback()
                        uiState =
                            uiState.copy(
                                opticalSettings = uiState.opticalSettings.copy(whiteBalance = wb)
                            )
                    },
                    onWarmthChange = { warmth ->
                        uiState =
                            uiState.copy(
                                opticalSettings =
                                uiState.opticalSettings.copy(warmthShift = warmth)
                            )
                    },
                    onBloomChange = { bloom ->
                        uiState =
                            uiState.copy(
                                opticalSettings =
                                uiState.opticalSettings.copy(bloomIntensity = bloom)
                            )
                    },
                    onSelectLens = { lens ->
                        triggerDialFeedback()
                        uiState =
                            uiState.copy(
                                opticalSettings =
                                uiState.opticalSettings.copy(lensAttachment = lens)
                            )
                    },
                    onToggleLeaksAndDust = {
                        triggerDialFeedback()
                        uiState =
                            uiState.copy(
                                opticalSettings =
                                ViewfinderStateReducer.toggleLeaksAndDust(
                                    uiState.activeCamera,
                                    uiState.opticalSettings
                                )
                            )
                    },
                    onShuffleSeed = {
                        triggerDialFeedback()
                        uiState = uiState.copy(randomSeed = Random.nextLong(1L, 999_999L))
                    },
                    onToggleFrame = {
                        triggerDialFeedback()
                        uiState =
                            uiState.copy(
                                opticalSettings =
                                uiState.opticalSettings.copy(
                                    frameStyle =
                                    ViewfinderStateReducer.toggleFrameStyle(
                                        uiState.activeCamera,
                                        uiState.opticalSettings.frameStyle
                                    )
                                )
                            )
                    },
                    onToggleStamp = {
                        triggerDialFeedback()
                        uiState =
                            uiState.copy(
                                stampConfig =
                                uiState.stampConfig.copy(
                                    enabled = !uiState.stampConfig.enabled
                                )
                            )
                    }
                )
            }

            AnimatedVisibility(visible = uiState.activePanel == ViewfinderPanel.CAMERA_SHELF) {
                CameraShelfDrawer(
                    activeCameraId = uiState.activeCamera.id,
                    onSelectCamera = { profile ->
                        triggerDialFeedback()
                        uiState = ViewfinderStateReducer.switchCamera(uiState, profile)
                        scope.launch { preferencesRepository.setActiveCameraId(profile.id) }
                    }
                )
            }

            ViewfinderBottomDeck(
                state = uiState,
                onOpenGallery = onOpenGallery,
                onToggleControls = {
                    triggerDialFeedback()
                    uiState =
                        uiState.copy(
                            activePanel =
                            if (uiState.activePanel == ViewfinderPanel.CONTROLS) {
                                ViewfinderPanel.NONE
                            } else {
                                ViewfinderPanel.CONTROLS
                            }
                        )
                },
                onShutterClick = { executeCapture() },
                onToggleCameraShelf = {
                    triggerDialFeedback()
                    uiState =
                        uiState.copy(
                            activePanel =
                            if (uiState.activePanel == ViewfinderPanel.CAMERA_SHELF) {
                                ViewfinderPanel.NONE
                            } else {
                                ViewfinderPanel.CAMERA_SHELF
                            }
                        )
                },
                onFlipCamera = {
                    triggerDialFeedback()
                    uiState = uiState.copy(isFrontCamera = !uiState.isFrontCamera)
                }
            )
            Spacer(modifier = Modifier.height(2.dp))
        }

        DevelopingRitualOverlay(
            overlay = uiState.developingOverlay,
            accent = accent
        )

        if (uiState.showRollCompletePrompt) {
            RollCompleteDialog(
                capacity = uiState.rollCapacity,
                accent = accent,
                onLoadNewRoll = {
                    scope.launch {
                        val fresh =
                            filmRollRepository.loadNewRoll(
                                cameraId = uiState.activeCamera.id,
                                capacity = uiState.settings.rollCapacity
                            )
                        soundEngine.playFilmWind(uiState.settings.sfxEnabled)
                        uiState =
                            uiState.copy(
                                activeRollId = fresh.rollId,
                                rollShotsTaken = fresh.shotsTaken,
                                rollCapacity = fresh.capacity,
                                showRollCompletePrompt = false,
                                statusBannerMessage = "Loaded fresh ${fresh.capacity}-exp roll"
                            )
                    }
                },
                onDismiss = {
                    uiState = uiState.copy(showRollCompletePrompt = false)
                }
            )
        }
    }
}

@Composable
private fun RuleOfThirdsGridOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val lineColor = Color.White.copy(alpha = 0.28f)
        val stroke = 1.dp.toPx()
        val x1 = size.width / 3f
        val x2 = size.width * 2f / 3f
        val y1 = size.height / 3f
        val y2 = size.height * 2f / 3f
        drawLine(lineColor, Offset(x1, 0f), Offset(x1, size.height), strokeWidth = stroke)
        drawLine(lineColor, Offset(x2, 0f), Offset(x2, size.height), strokeWidth = stroke)
        drawLine(lineColor, Offset(0f, y1), Offset(size.width, y1), strokeWidth = stroke)
        drawLine(lineColor, Offset(0f, y2), Offset(size.width, y2), strokeWidth = stroke)
    }
}

@Composable
private fun FocusReticleOverlay(reticle: FocusReticleState, accent: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cx = reticle.normalizedX * size.width
        val cy = reticle.normalizedY * size.height
        val boxSize = 54.dp.toPx()
        val half = boxSize / 2f
        drawRect(
            color = accent.copy(alpha = 0.9f),
            topLeft = Offset(cx - half, cy - half),
            size = Size(boxSize, boxSize),
            style = Stroke(width = 1.8.dp.toPx())
        )
        drawCircle(
            color = accent,
            radius = 2.5.dp.toPx(),
            center = Offset(cx, cy)
        )
    }
}

private fun createDownscaledThumbnail(source: Bitmap, maxEdge: Int): Bitmap {
    val w = source.width.coerceAtLeast(1)
    val h = source.height.coerceAtLeast(1)
    val longEdge = maxOf(w, h)
    if (longEdge <= maxEdge) {
        return source
    }
    val scale = maxEdge.toFloat() / longEdge.toFloat()
    val targetW = (w * scale).roundToInt().coerceAtLeast(2)
    val targetH = (h * scale).roundToInt().coerceAtLeast(2)
    return Bitmap.createScaledBitmap(source, targetW, targetH, true)
}
