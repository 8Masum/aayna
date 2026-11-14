package com.themasum.aayna.feature.viewfinder

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.opengl.GLSurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.themasum.aayna.core.camera.AaynaCameraController
import com.themasum.aayna.core.gl.AaynaSurfaceProcessor
import com.themasum.aayna.core.gl.AaynaViewfinderGlRenderer
import com.themasum.aayna.core.gl.GlRenderParams
import com.themasum.aayna.core.gl.OffscreenGlExporter
import com.themasum.aayna.core.model.CameraCatalog
import com.themasum.aayna.core.model.DateStampFormatter
import com.themasum.aayna.core.model.GlDiagnosticsInfo
import com.themasum.aayna.core.model.PassShaderStatus
import com.themasum.aayna.core.storage.AaynaLogger
import com.themasum.aayna.core.storage.AaynaPreferencesRepository
import com.themasum.aayna.core.storage.AaynaSoundEngine
import com.themasum.aayna.core.storage.FilmRollRepository
import com.themasum.aayna.core.storage.MediaStoreExporter
import com.themasum.aayna.core.storage.NegativeRepository
import com.themasum.aayna.core.storage.ThumbnailMemoryCache

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
    modifier: Modifier = Modifier,
    surfaceProcessor: AaynaSurfaceProcessor? = null,
    thumbnailCache: ThumbnailMemoryCache? = null,
    ledFontFamily: FontFamily = FontFamily.Monospace,
    dotMatrixFontFamily: FontFamily = FontFamily.Monospace,
    vcrOsdFontFamily: FontFamily = FontFamily.Monospace,
    pixelFontFamily: FontFamily = FontFamily.Monospace,
    condensedFontFamily: FontFamily = FontFamily.Monospace
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current

    val vmFactory =
        remember(
            context.applicationContext,
            cameraController,
            offscreenExporter,
            preferencesRepository,
            negativeRepository,
            filmRollRepository,
            mediaStoreExporter,
            soundEngine,
            logger
        ) {
            ViewfinderViewModelFactory(
                appContext = context.applicationContext,
                cameraController = cameraController,
                offscreenExporter = offscreenExporter,
                preferencesRepository = preferencesRepository,
                negativeRepository = negativeRepository,
                filmRollRepository = filmRollRepository,
                mediaStoreExporter = mediaStoreExporter,
                soundEngine = soundEngine,
                logger = logger
            )
        }
    val viewModel: ViewfinderViewModel = viewModel(factory = vmFactory)
    val uiState by viewModel.uiState.collectAsState()
    val fallbackGains by cameraController.shaderExposureFallback.collectAsState()

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

    val skinManifest = rememberSkinManifest(context.assets)
    val skinBitmapCache = rememberSkinBitmapCache()
    val skinAssetEntry = skinManifest.skinForId(uiState.activeSkinId)
    val chassisAssetPath = skinAssetEntry?.chassisAssetPath ?: uiState.activeSkin.chassisBgAsset
    val bezelAssetPath = skinAssetEntry?.bezelAssetPath ?: uiState.activeSkin.bezelOverlayAsset
    val shutterAssetPath = skinAssetEntry?.shutterAssetPath ?: uiState.activeSkin.shutterButtonAsset
    val glassSheenPath =
        skinAssetEntry?.screenGlassSheenPath ?: skinManifest.screenGlassSheenAssetPath

    val chassisBitmap = rememberSkinImageBitmap(chassisAssetPath, skinBitmapCache, context.assets)
    val bezelBitmap = rememberSkinImageBitmap(bezelAssetPath, skinBitmapCache, context.assets)
    val shutterBitmap = rememberSkinImageBitmap(shutterAssetPath, skinBitmapCache, context.assets)
    val glassSheenBitmap = rememberSkinImageBitmap(glassSheenPath, skinBitmapCache, context.assets)
    val carouselIconBitmap =
        rememberSkinImageBitmap(
            CameraCatalog.CAROUSEL_BADGE_ICON_ASSET,
            skinBitmapCache,
            context.assets
        )

    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    val glSurfaceViewRef = remember { mutableStateOf<GLSurfaceView?>(null) }
    var readySurfaceTexture by remember { mutableStateOf<SurfaceTexture?>(null) }
    var viewfinderSize by remember { mutableStateOf(IntSize.Zero) }

    val legacyRenderer =
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

    DisposableEffect(lifecycleOwner, surfaceProcessor) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (surfaceProcessor == null) {
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> glSurfaceViewRef.value?.onResume()
                        Lifecycle.Event.ON_PAUSE -> glSurfaceViewRef.value?.onPause()
                        else -> Unit
                    }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (surfaceProcessor == null) {
                val glView = glSurfaceViewRef.value
                if (glView != null) {
                    glView.queueEvent { legacyRenderer.release() }
                } else {
                    legacyRenderer.release()
                }
            } else {
                surfaceProcessor.releaseSurfacesOnly()
            }
        }
    }

    val effectiveProfile =
        remember(uiState.activeCamera, uiState.grainStrengthOverride) {
            ViewfinderStateReducer.effectiveProfileForRender(uiState)
        }

    val stampText =
        remember(uiState.stampConfig, effectiveProfile.eraYearOffset) {
            DateStampFormatter.format(
                epochMillis = System.currentTimeMillis(),
                format = uiState.stampConfig.format,
                useEraYearOffset = uiState.stampConfig.useEraYearOffset,
                eraYear = effectiveProfile.eraYearOffset,
                customText = uiState.stampConfig.customText
            )
        }

    val renderParams =
        remember(
            effectiveProfile,
            uiState.opticalSettings,
            uiState.stampConfig,
            stampText,
            fallbackGains,
            uiState.isPreviewHoldOriginal,
            uiState.randomSeed,
            uiState.inputAspectRatio
        ) {
            GlRenderParams(
                profile = effectiveProfile,
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

    LaunchedEffect(renderParams, surfaceProcessor) {
        if (surfaceProcessor != null) {
            surfaceProcessor.updateParams(renderParams)
            val (glInfo, shaderStatuses) = surfaceProcessor.getDiagnostics()
            onGlDiagnosticsUpdated(glInfo, shaderStatuses)
        } else {
            legacyRenderer.updateParams(renderParams)
            glSurfaceViewRef.value?.requestRender()
            val (glInfo, shaderStatuses) = legacyRenderer.getDiagnostics()
            onGlDiagnosticsUpdated(glInfo, shaderStatuses)
        }
    }

    LaunchedEffect(uiState.opticalSettings, uiState.settings.useHardwareAeCompatibility) {
        cameraController.applyOpticalSettings(
            settings = uiState.opticalSettings,
            useHardwareAeCompatibility = uiState.settings.useHardwareAeCompatibility
        )
    }

    // Bind CameraX via PreviewView + SurfaceProcessor when available, or legacy GLSurfaceView fallback
    LaunchedEffect(
        hasCameraPermission,
        surfaceProcessor,
        previewViewRef,
        readySurfaceTexture,
        uiState.isFrontCamera,
        uiState.activeCamera.supportsVideo,
        uiState.opticalSettings.aspectRatio,
        uiState.settings.useHardwareAeCompatibility
    ) {
        if (!hasCameraPermission) return@LaunchedEffect
        try {
            if (surfaceProcessor != null) {
                val pv = previewViewRef ?: return@LaunchedEffect
                cameraController.bindCamera(
                    lifecycleOwner = lifecycleOwner,
                    previewView = pv,
                    useFrontCamera = uiState.isFrontCamera,
                    aspectRatio = uiState.opticalSettings.aspectRatio,
                    surfaceProcessor = surfaceProcessor,
                    surfaceProcessorExecutor = surfaceProcessor.glExecutor,
                    enableVideoCapture = uiState.activeCamera.supportsVideo,
                    preferCompatPreview = true,
                    useHardwareAeCompatibility = uiState.settings.useHardwareAeCompatibility,
                    onEffectError = { err ->
                        logger.logWarn("Viewfinder", "CameraEffect fallback triggered", err)
                    }
                )
                cameraController.applyOpticalSettings(
                    settings = uiState.opticalSettings,
                    useHardwareAeCompatibility = uiState.settings.useHardwareAeCompatibility
                )
                val (glInfo, shaderStatuses) = surfaceProcessor.getDiagnostics()
                onGlDiagnosticsUpdated(glInfo, shaderStatuses)
            } else {
                val st = readySurfaceTexture ?: return@LaunchedEffect
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
                        viewModel.setInputAspectRatio(ratio)
                    }
                )
                cameraController.applyOpticalSettings(
                    settings = uiState.opticalSettings,
                    useHardwareAeCompatibility = uiState.settings.useHardwareAeCompatibility
                )
                val (glInfo, shaderStatuses) = legacyRenderer.getDiagnostics()
                onGlDiagnosticsUpdated(glInfo, shaderStatuses)
            }
        } catch (t: Throwable) {
            logger.logError("Viewfinder", "Failed to bind CameraX", t)
            viewModel.showBanner("Camera error: ${t.message ?: "bind failed"}")
        }
    }

    fun triggerDialFeedback() {
        if (uiState.settings.hapticsEnabled) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        soundEngine.playDialTick(uiState.settings.sfxEnabled)
    }

    fun triggerShutterHaptic() {
        if (uiState.settings.hapticsEnabled) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    val accent = parseHexColor(uiState.activeSkin.accentHex, androidx.compose.ui.graphics.Color(0xFF4CD4E8))
    val proportions = skinManifest.proportions

    ProccdChassisBackdrop(
        state = uiState,
        chassisBitmap = chassisBitmap,
        modifier = modifier.fillMaxSize()
    ) {
        BoxWithConstraints(
            modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            val containerWidth =
                minOf(
                    maxWidth * proportions.containerWidthFraction,
                    (maxHeight * 0.58f) * proportions.cameraScreenContainerRatio
                )
            val containerHeight = containerWidth / proportions.cameraScreenContainerRatio
            val minLowerDeckSpace = 196.dp
            val maxTopOffset = (maxHeight - containerHeight - minLowerDeckSpace).coerceAtLeast(6.dp)
            val desiredTopOffset = (maxHeight * proportions.cameraScreenHMidLine) - (containerHeight / 2f)
            val topOffset = desiredTopOffset.coerceIn(6.dp, maxTopOffset)

            // Fixed-deck top camera screen container: NEVER resizes when drawers/sheets open!
            ProccdCameraScreenContainer(
                state = uiState,
                proportions = proportions,
                bezelBitmap = bezelBitmap,
                glassSheenBitmap = glassSheenBitmap,
                hasCameraPermission = hasCameraPermission,
                useSurfaceProcessorMode = surfaceProcessor != null,
                glRenderer = legacyRenderer,
                onPreviewViewReady = { previewViewRef = it },
                onGlSurfaceViewReady = { glSurfaceViewRef.value = it },
                onViewfinderSizeChanged = { viewfinderSize = it },
                onTapFocus = { offset, boxSize ->
                    val pv = previewViewRef
                    if (surfaceProcessor != null && pv != null) {
                        cameraController.tapToFocus(pv, offset.x, offset.y)
                    } else if (boxSize.width > 0 && boxSize.height > 0) {
                        val nx = (offset.x / boxSize.width).coerceIn(0f, 1f)
                        val ny = (offset.y / boxSize.height).coerceIn(0f, 1f)
                        cameraController.tapToFocus(
                            normalizedX = nx,
                            normalizedY = ny,
                            viewWidth = boxSize.width,
                            viewHeight = boxSize.height
                        )
                    }
                    if (boxSize.width > 0 && boxSize.height > 0) {
                        viewModel.showFocusReticle(
                            normalizedX = offset.x / boxSize.width,
                            normalizedY = offset.y / boxSize.height
                        )
                    }
                },
                onHoldOriginalChanged = viewModel::setPreviewHoldOriginal,
                onSelectZoomRatio = { ratio ->
                    triggerDialFeedback()
                    viewModel.setZoomRatio(ratio)
                },
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onOpenGallery = onOpenGallery,
                onToggleMinimalMode = {
                    triggerDialFeedback()
                    viewModel.toggleMinimalMode()
                },
                onToggleCameraShelf = {
                    triggerDialFeedback()
                    viewModel.openPanel(ViewfinderPanel.CAMERA_SHELF)
                },
                ledFontFamily = ledFontFamily,
                vcrOsdFontFamily = vcrOsdFontFamily,
                condensedFontFamily = condensedFontFamily,
                containerWidth = containerWidth,
                containerHeight = containerHeight,
                modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topOffset)
            )

            // Fixed lower tactile deck anchored at bottom
            Column(
                modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!uiState.isMinimalMode) {
                    ProccdNotchedRuler(
                        state = uiState,
                        onSelectParameter = viewModel::selectRulerParameter,
                        onValueChange = viewModel::setRulerValue,
                        onResetParameter = viewModel::resetRulerParameter,
                        onNotchTick = ::triggerDialFeedback
                    )
                }

                ProccdLowerTactileDeck(
                    state = uiState,
                    shutterBitmap = shutterBitmap,
                    carouselIconBitmap = carouselIconBitmap,
                    thumbnailCache = thumbnailCache,
                    onOpenGallery = onOpenGallery,
                    onShutterClick = {
                        triggerShutterHaptic()
                        viewModel.onShutterPressed(hasCameraPermission)
                    },
                    onToggleVideoMode = { video ->
                        triggerDialFeedback()
                        viewModel.toggleVideoMode(video)
                    },
                    onOpenPanel = { panel ->
                        triggerDialFeedback()
                        viewModel.openPanel(panel)
                    },
                    onCycleFlash = {
                        triggerDialFeedback()
                        viewModel.cycleFlashMode()
                    },
                    onCycleTimer = {
                        triggerDialFeedback()
                        viewModel.cycleTimer()
                    },
                    onFlipCamera = {
                        triggerDialFeedback()
                        viewModel.flipCamera()
                    },
                    onToggleGrid = {
                        triggerDialFeedback()
                        viewModel.toggleGrid()
                    },
                    onToggleMinimalMode = {
                        triggerDialFeedback()
                        viewModel.toggleMinimalMode()
                    },
                    onOpenSettings = onOpenSettings
                )
            }

            InstantEjectCardOverlay(
                card = uiState.ejectedCardPreview,
                thumbnailCache = thumbnailCache,
                accent = accent,
                onOpenGallery = onOpenGallery,
                modifier = Modifier.align(Alignment.BottomStart)
            )

            // Overlay drawers float above the lower deck without resizing the 3:4 viewfinder
            AnimatedVisibility(
                visible = uiState.activePanel != ViewfinderPanel.NONE,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                when (uiState.activePanel) {
                    ViewfinderPanel.CAMERA_SHELF ->
                        ProccdCameraShelfDrawer(
                            state = uiState,
                            manifest = skinManifest,
                            bitmapCache = skinBitmapCache,
                            assetManager = context.assets,
                            onSelectCamera = { profile ->
                                triggerDialFeedback()
                                viewModel.selectCamera(profile)
                            },
                            onToggleFavorite = { camId ->
                                triggerDialFeedback()
                                viewModel.toggleFavoriteCamera(camId)
                            },
                            onSelectCategory = viewModel::setShelfCategory,
                            onToggleFavoritesOnly = viewModel::toggleShelfFavoritesOnly,
                            onOpenSampleSpecs = viewModel::openSampleSpecs,
                            onClose = viewModel::closePanels
                        )

                    ViewfinderPanel.SKIN_MANAGER ->
                        ProccdSkinManagerSheet(
                            state = uiState,
                            manifest = skinManifest,
                            bitmapCache = skinBitmapCache,
                            assetManager = context.assets,
                            onSelectSkin = { skinId ->
                                triggerDialFeedback()
                                viewModel.setSkinForActiveCamera(skinId)
                            },
                            onResetSkin = {
                                triggerDialFeedback()
                                viewModel.resetSkinForActiveCamera()
                            },
                            onClose = viewModel::closePanels
                        )

                    ViewfinderPanel.CONTROLS ->
                        ProccdManualDrawer(
                            state = uiState,
                            onSelectTab = { tab ->
                                triggerDialFeedback()
                                viewModel.selectControlTab(tab)
                            },
                            onStepEv = { delta ->
                                triggerDialFeedback()
                                viewModel.stepEv(delta)
                            },
                            onSelectIso = { iso ->
                                triggerDialFeedback()
                                viewModel.selectIso(iso)
                            },
                            onSelectShutter = { shutterNs ->
                                triggerDialFeedback()
                                viewModel.selectShutter(shutterNs)
                            },
                            onSelectWhiteBalance = { wb ->
                                triggerDialFeedback()
                                viewModel.selectWhiteBalance(wb)
                            },
                            onSelectAspectRatio = { ratio ->
                                triggerDialFeedback()
                                viewModel.selectAspectRatio(ratio)
                            },
                            onHighlightsChange = { v ->
                                viewModel.updateOpticalSettings { it.copy(highlights = v) }
                            },
                            onShadowsChange = { v ->
                                viewModel.updateOpticalSettings { it.copy(shadows = v) }
                            },
                            onSharpenChange = { v ->
                                viewModel.updateOpticalSettings { it.copy(sharpen = v) }
                            },
                            onBeautyChange = { v ->
                                viewModel.updateOpticalSettings { it.copy(beautySoftness = v) }
                            },
                            onToggleLeaksAndDust = {
                                triggerDialFeedback()
                                viewModel.toggleLeaksAndDust()
                            },
                            onShuffleSeed = {
                                triggerDialFeedback()
                                viewModel.shuffleSeed()
                            },
                            onClose = viewModel::closePanels
                        )

                    ViewfinderPanel.LENS ->
                        ProccdLensDrawer(
                            state = uiState,
                            onSelectLens = { lens ->
                                triggerDialFeedback()
                                viewModel.selectLensAttachment(lens)
                            },
                            onClose = viewModel::closePanels
                        )

                    ViewfinderPanel.FRAME ->
                        ProccdFrameDrawer(
                            state = uiState,
                            onSelectFrame = { frame ->
                                triggerDialFeedback()
                                viewModel.selectFrameStyle(frame)
                            },
                            onClose = viewModel::closePanels
                        )

                    ViewfinderPanel.STAMP ->
                        ProccdStampDrawer(
                            state = uiState,
                            ledFontFamily = ledFontFamily,
                            dotMatrixFontFamily = dotMatrixFontFamily,
                            vcrOsdFontFamily = vcrOsdFontFamily,
                            pixelFontFamily = pixelFontFamily,
                            onToggleStamp = {
                                triggerDialFeedback()
                                viewModel.toggleStampEnabled()
                            },
                            onSelectFont = { font ->
                                triggerDialFeedback()
                                viewModel.setStampFontStyle(font)
                            },
                            onSelectColor = { color ->
                                triggerDialFeedback()
                                viewModel.setStampColor(color)
                            },
                            onSelectPosition = { pos ->
                                triggerDialFeedback()
                                viewModel.setStampPosition(pos)
                            },
                            onSelectFormat = { fmt ->
                                triggerDialFeedback()
                                viewModel.setStampFormat(fmt)
                            },
                            onToggleEraYear = {
                                triggerDialFeedback()
                                viewModel.toggleEraYearOffset()
                            },
                            onClose = viewModel::closePanels
                        )

                    ViewfinderPanel.NONE -> Unit
                }
            }

            DevelopingRitualOverlay(
                overlay = uiState.developingOverlay,
                accent = accent
            )

            uiState.sampleSpecsCamera?.let { specsProfile ->
                SampleSpecsDialog(
                    profile = specsProfile,
                    accent = accent,
                    onSelectCamera = { profile ->
                        triggerDialFeedback()
                        viewModel.selectCamera(profile)
                    },
                    onDismiss = { viewModel.openSampleSpecs(null) }
                )
            }

            if (uiState.showRollCompletePrompt) {
                RollCompleteDialog(
                    capacity = uiState.rollCapacity,
                    accent = accent,
                    onLoadNewRoll = viewModel::loadNewRoll,
                    onDismiss = viewModel::dismissRollCompletePrompt
                )
            }
        }
    }
}
