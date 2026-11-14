package com.themasum.aayna.feature.viewfinder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.VideoRecordEvent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.themasum.aayna.core.camera.AaynaCameraController
import com.themasum.aayna.core.gl.OffscreenGlExporter
import com.themasum.aayna.core.model.AspectRatioPreset
import com.themasum.aayna.core.model.CameraCatalog
import com.themasum.aayna.core.model.CameraCategory
import com.themasum.aayna.core.model.CameraProfile
import com.themasum.aayna.core.model.CaptureRecipe
import com.themasum.aayna.core.model.CaptureStorageMode
import com.themasum.aayna.core.model.DateStampFormatter
import com.themasum.aayna.core.model.FrameBorderStyle
import com.themasum.aayna.core.model.LensAttachment
import com.themasum.aayna.core.model.MediaType
import com.themasum.aayna.core.model.OpticalSettings
import com.themasum.aayna.core.model.StampFontStyle
import com.themasum.aayna.core.model.StampFormat
import com.themasum.aayna.core.model.StampPosition
import com.themasum.aayna.core.model.StampStyle
import com.themasum.aayna.core.model.TimerDuration
import com.themasum.aayna.core.model.WhiteBalancePreset
import com.themasum.aayna.core.storage.AaynaLogger
import com.themasum.aayna.core.storage.AaynaPreferencesRepository
import com.themasum.aayna.core.storage.AaynaSoundEngine
import com.themasum.aayna.core.storage.FilmRollRepository
import com.themasum.aayna.core.storage.MediaStoreExporter
import com.themasum.aayna.core.storage.MediaStoreVideoOutput
import com.themasum.aayna.core.storage.NegativeRepository
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ViewfinderViewModel(
    private val appContext: Context,
    private val cameraController: AaynaCameraController,
    private val offscreenExporter: OffscreenGlExporter,
    private val preferencesRepository: AaynaPreferencesRepository,
    private val negativeRepository: NegativeRepository,
    private val filmRollRepository: FilmRollRepository,
    private val mediaStoreExporter: MediaStoreExporter,
    private val soundEngine: AaynaSoundEngine,
    private val logger: AaynaLogger
) : ViewModel() {

    private val _uiState = MutableStateFlow(ViewfinderUiState())
    val uiState: StateFlow<ViewfinderUiState> = _uiState.asStateFlow()

    private var reticleClearJob: Job? = null
    private var bannerClearJob: Job? = null
    private var ejectedClearJob: Job? = null
    private var recordingTimerJob: Job? = null
    private var activeVideoMeta: ActiveVideoSession? = null

    private data class ActiveVideoSession(
        val startedAtMs: Long,
        val output: MediaStoreVideoOutput,
        val profile: CameraProfile,
        val recipe: CaptureRecipe
    )

    init {
        hydrateInitialStateAndObserve()
    }

    private fun hydrateInitialStateAndObserve() {
        viewModelScope.launch {
            // Non-clobbering hydration: read persisted preferences first before observing flows
            // and never write defaults back during initialization.
            val initialSettings = preferencesRepository.readInitialSettings()
            val initialCameraId = preferencesRepository.readInitialActiveCameraId()
            val initialFavorites = preferencesRepository.readInitialFavoriteCameraIds()
            val initialProfile = CameraCatalog.findByIdOrDefault(initialCameraId)

            cameraController.setHardwareAeCompatibility(initialSettings.useHardwareAeCompatibility)

            _uiState.update { current ->
                val switched = ViewfinderStateReducer.switchCamera(current, initialProfile)
                ViewfinderStateReducer.applyUserSettings(
                    state = switched,
                    settings = initialSettings,
                    syncGridFromSettings = true
                ).copy(
                    favoriteCameraIds = initialFavorites,
                    isHydrated = true
                )
            }

            launch {
                preferencesRepository.settingsFlow.collect { snapshot ->
                    cameraController.setHardwareAeCompatibility(snapshot.useHardwareAeCompatibility)
                    _uiState.update { state ->
                        ViewfinderStateReducer.applyUserSettings(
                            state = state,
                            settings = snapshot,
                            syncGridFromSettings = false
                        )
                    }
                }
            }

            launch {
                preferencesRepository.activeCameraIdFlow.collect { cameraId ->
                    val currentId = _uiState.value.activeCamera.id
                    if (cameraId != currentId) {
                        val profile = CameraCatalog.findByIdOrDefault(cameraId)
                        _uiState.update { state ->
                            ViewfinderStateReducer.switchCamera(state, profile)
                        }
                    }
                }
            }

            launch {
                preferencesRepository.favoriteCameraIdsFlow.collect { favorites ->
                    _uiState.update { it.copy(favoriteCameraIds = favorites) }
                }
            }

            launch {
                filmRollRepository.activeRollFlow.collect { roll ->
                    _uiState.update { state ->
                        state.copy(
                            activeRollId = roll?.rollId,
                            rollShotsTaken = roll?.shotsTaken ?: 0,
                            rollCapacity = roll?.capacity ?: state.settings.rollCapacity
                        )
                    }
                }
            }

            launch {
                negativeRepository.activeNegativesFlow.collect { negatives ->
                    val latest = negatives.firstOrNull()
                    val thumbPath = latest?.thumbnailPath?.takeIf { File(it).exists() }
                    val lastModified = thumbPath?.let { File(it).lastModified() } ?: 0L
                    _uiState.update { state ->
                        state.copy(
                            lastNegativeThumbnailPath = thumbPath,
                            lastNegativeTimestampMs = lastModified,
                            lastCapturedUri = latest?.exportedUri ?: latest?.mediaUri ?: state.lastCapturedUri
                        )
                    }
                }
            }

            launch {
                cameraController.zoomUiState.collect { zoom ->
                    _uiState.update { it.copy(zoomUiState = zoom) }
                }
            }

            launch {
                cameraController.isRecording.collect { recording ->
                    _uiState.update { it.copy(isRecordingVideo = recording) }
                }
            }
        }
    }

    fun selectCamera(profile: CameraProfile) {
        _uiState.update { ViewfinderStateReducer.switchCamera(it, profile) }
        viewModelScope.launch {
            preferencesRepository.setActiveCameraId(profile.id)
        }
    }

    fun toggleFavoriteCamera(cameraId: String) {
        viewModelScope.launch {
            preferencesRepository.toggleFavoriteCamera(cameraId)
        }
    }

    fun setSkinForActiveCamera(skinId: String) {
        val cameraId = _uiState.value.activeCamera.id
        viewModelScope.launch {
            preferencesRepository.setSkinForCamera(cameraId, skinId)
        }
    }

    fun resetSkinForActiveCamera() {
        val cameraId = _uiState.value.activeCamera.id
        viewModelScope.launch {
            preferencesRepository.clearSkinForCamera(cameraId)
        }
    }

    fun openPanel(panel: ViewfinderPanel) {
        _uiState.update { state ->
            state.copy(
                activePanel = if (state.activePanel == panel) ViewfinderPanel.NONE else panel
            )
        }
    }

    fun closePanels() {
        _uiState.update { it.copy(activePanel = ViewfinderPanel.NONE) }
    }

    fun selectControlTab(tab: ControlTab) {
        _uiState.update { it.copy(activeControlTab = tab) }
    }

    fun selectRulerParameter(parameter: RulerParameter) {
        _uiState.update { it.copy(activeRulerParameter = parameter) }
    }

    fun setRulerValue(parameter: RulerParameter, value: Float) {
        _uiState.update { ViewfinderStateReducer.applyRulerValue(it, parameter, value) }
    }

    fun resetRulerParameter(parameter: RulerParameter) {
        val defaults = ViewfinderStateReducer.defaultOpticalSettingsFor(_uiState.value.activeCamera)
        _uiState.update { state ->
            when (parameter) {
                RulerParameter.EV ->
                    state.copy(opticalSettings = state.opticalSettings.copy(evCompensation = defaults.evCompensation))
                RulerParameter.WARMTH ->
                    state.copy(opticalSettings = state.opticalSettings.copy(warmthShift = defaults.warmthShift))
                RulerParameter.VIGNETTE ->
                    state.copy(
                        opticalSettings = state.opticalSettings.copy(vignetteIntensity = defaults.vignetteIntensity)
                    )
                RulerParameter.BLOOM ->
                    state.copy(opticalSettings = state.opticalSettings.copy(bloomIntensity = defaults.bloomIntensity))
                RulerParameter.GRAIN -> state.copy(grainStrengthOverride = null)
                RulerParameter.LEAK ->
                    state.copy(
                        opticalSettings = state.opticalSettings.copy(lightLeakIntensity = defaults.lightLeakIntensity)
                    )
            }
        }
    }

    fun toggleMinimalMode() {
        _uiState.update { state ->
            state.copy(
                isMinimalMode = !state.isMinimalMode,
                activePanel = ViewfinderPanel.NONE
            )
        }
    }

    fun toggleVideoMode(video: Boolean) {
        val state = _uiState.value
        if (state.isRecordingVideo) return
        if (video && !state.activeCamera.supportsVideo) {
            showBanner("${state.activeCamera.codeName} is still-only (use DCR / V80 / HDC / DV)")
            return
        }
        _uiState.update { it.copy(isVideoMode = video) }
    }

    fun cycleFlashMode() {
        _uiState.update { state ->
            state.copy(
                opticalSettings =
                state.opticalSettings.copy(
                    flashMode = ViewfinderStateReducer.cycleFlashMode(state.opticalSettings.flashMode)
                )
            )
        }
    }

    fun cycleAspectRatio() {
        _uiState.update { state ->
            state.copy(
                opticalSettings =
                state.opticalSettings.copy(
                    aspectRatio = ViewfinderStateReducer.cycleAspectRatio(state.opticalSettings.aspectRatio)
                )
            )
        }
    }

    fun selectAspectRatio(aspectRatio: AspectRatioPreset) {
        _uiState.update { state ->
            state.copy(opticalSettings = state.opticalSettings.copy(aspectRatio = aspectRatio))
        }
    }

    fun cycleTimer() {
        _uiState.update { state ->
            state.copy(timerDuration = ViewfinderStateReducer.cycleTimer(state.timerDuration))
        }
    }

    fun toggleGrid() {
        val nextGrid = !_uiState.value.isGridVisible
        _uiState.update { it.copy(isGridVisible = nextGrid) }
        viewModelScope.launch {
            preferencesRepository.setGridEnabled(nextGrid)
        }
    }

    fun flipCamera() {
        if (_uiState.value.isRecordingVideo) return
        _uiState.update { it.copy(isFrontCamera = !it.isFrontCamera) }
    }

    fun setZoomRatio(ratio: Float) {
        cameraController.setZoomRatio(ratio)
    }

    fun setInputAspectRatio(ratio: Float) {
        _uiState.update { it.copy(inputAspectRatio = ratio) }
    }

    fun setPreviewHoldOriginal(holding: Boolean) {
        _uiState.update { it.copy(isPreviewHoldOriginal = holding) }
    }

    fun showFocusReticle(normalizedX: Float, normalizedY: Float) {
        val reticle =
            FocusReticleState(
                normalizedX = normalizedX.coerceIn(0f, 1f),
                normalizedY = normalizedY.coerceIn(0f, 1f),
                triggeredAtMs = System.currentTimeMillis()
            )
        _uiState.update { it.copy(focusReticle = reticle) }
        reticleClearJob?.cancel()
        reticleClearJob =
            viewModelScope.launch {
                delay(1500L)
                _uiState.update { state ->
                    if (state.focusReticle?.triggeredAtMs == reticle.triggeredAtMs) {
                        state.copy(focusReticle = null)
                    } else {
                        state
                    }
                }
            }
    }

    fun updateOpticalSettings(transform: (OpticalSettings) -> OpticalSettings) {
        _uiState.update { state ->
            state.copy(opticalSettings = transform(state.opticalSettings).clamped())
        }
    }

    fun selectLensAttachment(lens: LensAttachment) {
        updateOpticalSettings { it.copy(lensAttachment = lens) }
    }

    fun selectFrameStyle(frameStyle: FrameBorderStyle) {
        updateOpticalSettings { it.copy(frameStyle = frameStyle) }
    }

    fun toggleLeaksAndDust() {
        _uiState.update { state ->
            state.copy(
                opticalSettings =
                ViewfinderStateReducer.toggleLeaksAndDust(state.activeCamera, state.opticalSettings)
            )
        }
    }

    fun shuffleSeed() {
        _uiState.update { it.copy(randomSeed = Random.nextLong(1L, 999_999L)) }
    }

    fun selectIso(iso: Int?) = updateOpticalSettings { it.copy(iso = iso) }

    fun selectShutter(shutterNs: Long?) = updateOpticalSettings { it.copy(shutterSpeedNs = shutterNs) }

    fun selectWhiteBalance(wb: WhiteBalancePreset) = updateOpticalSettings { it.copy(whiteBalance = wb) }

    fun stepEv(delta: Float) {
        updateOpticalSettings {
            it.copy(evCompensation = ViewfinderStateReducer.stepEv(it.evCompensation, delta))
        }
    }

    fun toggleStampEnabled() {
        _uiState.update { state ->
            state.copy(stampConfig = state.stampConfig.copy(enabled = !state.stampConfig.enabled))
        }
    }

    fun setStampColor(style: StampStyle) {
        _uiState.update { it.copy(stampConfig = it.stampConfig.copy(style = style)) }
        viewModelScope.launch { preferencesRepository.setStampColor(style) }
    }

    fun setStampFontStyle(fontStyle: StampFontStyle) {
        _uiState.update { it.copy(stampConfig = it.stampConfig.copy(fontStyle = fontStyle)) }
        viewModelScope.launch { preferencesRepository.setStampFontStyle(fontStyle) }
    }

    fun setStampPosition(position: StampPosition) {
        _uiState.update { it.copy(stampConfig = it.stampConfig.copy(position = position)) }
        viewModelScope.launch { preferencesRepository.setStampPosition(position) }
    }

    fun setStampFormat(format: StampFormat) {
        _uiState.update { it.copy(stampConfig = it.stampConfig.copy(format = format)) }
        viewModelScope.launch { preferencesRepository.setStampFormat(format) }
    }

    fun toggleEraYearOffset() {
        val next = !_uiState.value.stampConfig.useEraYearOffset
        _uiState.update { it.copy(stampConfig = it.stampConfig.copy(useEraYearOffset = next)) }
        viewModelScope.launch { preferencesRepository.setUseEraYearOffset(next) }
    }

    fun updateStampCustomText(text: String) {
        _uiState.update { state ->
            ViewfinderStateReducer.updateStampCustomText(state, text)
        }
    }

    fun setShelfCategory(category: CameraCategory?) {
        _uiState.update { it.copy(cameraShelfCategory = category, cameraShelfFavoritesOnly = false) }
    }

    fun toggleShelfFavoritesOnly() {
        _uiState.update { it.copy(cameraShelfFavoritesOnly = !it.cameraShelfFavoritesOnly) }
    }

    fun openSampleSpecs(profile: CameraProfile?) {
        _uiState.update { it.copy(sampleSpecsCamera = profile) }
    }

    fun dismissRollCompletePrompt() {
        _uiState.update { it.copy(showRollCompletePrompt = false) }
    }

    fun loadNewRoll() {
        viewModelScope.launch {
            val state = _uiState.value
            val fresh =
                filmRollRepository.loadNewRoll(
                    cameraId = state.activeCamera.id,
                    capacity = state.settings.rollCapacity
                )
            soundEngine.playFilmWind(state.settings.sfxEnabled)
            _uiState.update {
                it.copy(
                    activeRollId = fresh.rollId,
                    rollShotsTaken = fresh.shotsTaken,
                    rollCapacity = fresh.capacity,
                    showRollCompletePrompt = false
                )
            }
            showBanner("Loaded fresh ${fresh.capacity}-exp roll")
        }
    }

    fun showBanner(message: String) {
        _uiState.update { it.copy(statusBannerMessage = message) }
        bannerClearJob?.cancel()
        bannerClearJob =
            viewModelScope.launch {
                delay(2400L)
                _uiState.update { state ->
                    if (state.statusBannerMessage == message) state.copy(statusBannerMessage = null) else state
                }
            }
    }

    fun onShutterPressed(hasCameraPermission: Boolean) {
        val state = _uiState.value
        if (!hasCameraPermission) return

        if (state.isRecordingVideo) {
            stopVideoRecording()
            return
        }

        if (state.isCapturing) return

        val captureKind = ViewfinderStateReducer.resolveCaptureKind(state.activeCamera, state.isVideoMode)
        if (captureKind == CaptureKind.VIDEO) {
            startVideoRecording()
            return
        }

        executeStillOrBurstCapture(captureKind)
    }

    private fun startVideoRecording() {
        val state = _uiState.value
        val startedAtMs = System.currentTimeMillis()
        val output = mediaStoreExporter.createVideoMediaStoreOutput(state.activeCamera, startedAtMs)
        val legacyFile = output.legacyFile
        val outputOptions =
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && legacyFile != null) {
                FileOutputOptions.Builder(legacyFile).build()
            } else {
                MediaStoreOutputOptions
                    .Builder(appContext.contentResolver, output.collectionUri)
                    .setContentValues(output.contentValues)
                    .build()
            }

        val recipe =
            CaptureRecipe(
                recipeId = UUID.randomUUID().toString(),
                cameraId = state.activeCamera.id,
                capturedAtEpochMs = startedAtMs,
                randomSeed = state.randomSeed,
                mediaType = MediaType.VIDEO,
                skinId = state.activeSkinId,
                resolutionPreset = state.settings.resolutionPreset,
                opticalSettings = state.opticalSettings,
                stampConfig = state.stampConfig,
                videoDurationMs = 0L,
                burstFrameCount = 1
            )

        activeVideoMeta = ActiveVideoSession(startedAtMs, output, state.activeCamera, recipe)
        val recording =
            cameraController.startVideoRecording(
                outputOptions = outputOptions,
                audioEnabled = true
            ) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        soundEngine.playVideoStartStop(state.settings.sfxEnabled, isStarting = true)
                        startRecordingElapsedTicker(startedAtMs)
                    }
                    is VideoRecordEvent.Finalize -> {
                        stopRecordingElapsedTicker()
                        soundEngine.playVideoStartStop(state.settings.sfxEnabled, isStarting = false)
                        val session = activeVideoMeta
                        activeVideoMeta = null
                        if (event.hasError()) {
                            logger.logError("Viewfinder", "Video finalize error=${event.error}", event.cause)
                            showBanner("Video failed (${event.error})")
                        } else if (session != null) {
                            handleVideoFinalized(
                                session = session,
                                outputUri = event.outputResults.outputUri,
                                durationMs = (System.currentTimeMillis() - session.startedAtMs).coerceAtLeast(100L)
                            )
                        }
                    }
                }
            }

        if (recording == null) {
            activeVideoMeta = null
            showBanner("Video recorder unavailable")
        }
    }

    private fun stopVideoRecording() {
        cameraController.stopVideoRecording()
    }

    private fun handleVideoFinalized(session: ActiveVideoSession, outputUri: Uri, durationMs: Long) {
        viewModelScope.launch {
            val legacyFile = session.output.legacyFile
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && legacyFile != null) {
                mediaStoreExporter.scanLegacyVideoFile(legacyFile)
            }
            val thumb = extractVideoThumbnailOrFallback(outputUri, session.profile)
            try {
                val finalRecipe = session.recipe.copy(videoDurationMs = durationMs)
                val saved =
                    negativeRepository.saveMediaNegative(
                        thumbnailBitmap = thumb,
                        recipe = finalRecipe,
                        mediaUri = outputUri.toString()
                    )
                triggerEjectedCard(
                    saved.thumbnailPath,
                    session.profile.codeName,
                    "REC ${DateStampFormatter.formatTimecode(durationMs)}"
                )
                showBanner("Saved video to Movies/Aayna")
            } catch (t: Throwable) {
                logger.logError("Viewfinder", "Failed to save video negative", t)
                showBanner("Video saved, studio index failed")
            } finally {
                if (!thumb.isRecycled) thumb.recycle()
            }
        }
    }

    private suspend fun extractVideoThumbnailOrFallback(uri: Uri, profile: CameraProfile): Bitmap =
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(appContext, uri)
                val frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null) {
                    val scaled = createDownscaledThumbnail(frame, maxEdge = 360)
                    if (scaled !== frame && !frame.isRecycled) frame.recycle()
                    return@withContext scaled
                }
            } catch (_: Throwable) {
                // Fallback below
            } finally {
                runCatching { retriever.release() }
            }
            Bitmap.createBitmap(270, 360, Bitmap.Config.ARGB_8888).apply {
                val canvas = Canvas(this)
                val bg = runCatching {
                    AndroidColor.parseColor(profile.skin.bodyPrimaryHex)
                }.getOrDefault(0xFF1A1C22.toInt())
                canvas.drawColor(bg)
            }
        }

    private fun startRecordingElapsedTicker(startedAtMs: Long) {
        recordingTimerJob?.cancel()
        recordingTimerJob =
            viewModelScope.launch {
                while (true) {
                    _uiState.update {
                        it.copy(recordingElapsedMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(0L))
                    }
                    delay(250L)
                }
            }
    }

    private fun stopRecordingElapsedTicker() {
        recordingTimerJob?.cancel()
        recordingTimerJob = null
        _uiState.update { it.copy(recordingElapsedMs = 0L) }
    }

    private fun executeStillOrBurstCapture(captureKind: CaptureKind) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCapturing = true, lastErrorMessage = null) }
            try {
                val initialState = _uiState.value

                // Pre-capture 24/36 roll capacity gate BEFORE triggering hardware shutter!
                if (initialState.settings.storageMode == CaptureStorageMode.ANALOG_DEVELOPING_RITUAL) {
                    val activeRollState =
                        filmRollRepository.getOrCreateActiveRoll(
                            cameraId = initialState.activeCamera.id,
                            capacity = initialState.settings.rollCapacity,
                            nowEpochMs = System.currentTimeMillis()
                        )
                    _uiState.update {
                        it.copy(
                            activeRollId = activeRollState.rollId,
                            rollShotsTaken = activeRollState.shotsTaken,
                            rollCapacity = activeRollState.capacity
                        )
                    }
                    if (ViewfinderStateReducer.shouldBlockCaptureForRoll(
                            storageMode = initialState.settings.storageMode,
                            shotsTaken = activeRollState.shotsTaken,
                            capacity = activeRollState.capacity
                        )
                    ) {
                        _uiState.update { it.copy(showRollCompletePrompt = true) }
                        return@launch
                    }
                }

                if (initialState.timerDuration != TimerDuration.OFF) {
                    for (sec in initialState.timerDuration.seconds downTo 1) {
                        _uiState.update { it.copy(countdownRemainingSeconds = sec) }
                        soundEngine.playDialTick(_uiState.value.settings.sfxEnabled)
                        delay(1000L)
                    }
                    _uiState.update { it.copy(countdownRemainingSeconds = null) }
                }

                val state = _uiState.value
                val renderProfile = ViewfinderStateReducer.effectiveProfileForRender(state)
                val fallbackGains = cameraController.shaderExposureFallback.value
                val capturedAtMs = System.currentTimeMillis()
                val captureStampText =
                    DateStampFormatter.format(
                        epochMillis = capturedAtMs,
                        format = state.stampConfig.format,
                        useEraYearOffset = state.stampConfig.useEraYearOffset,
                        eraYear = renderProfile.eraYearOffset,
                        customText = state.stampConfig.customText
                    )

                when (captureKind) {
                    CaptureKind.STILL ->
                        runStillCapture(
                            state = state,
                            renderProfile = renderProfile,
                            capturedAtMs = capturedAtMs,
                            captureStampText = captureStampText,
                            fallbackGains = fallbackGains
                        )

                    CaptureKind.D3D_BURST ->
                        runD3dBurstCapture(
                            state = state,
                            renderProfile = renderProfile,
                            capturedAtMs = capturedAtMs,
                            captureStampText = captureStampText,
                            fallbackGains = fallbackGains
                        )

                    CaptureKind.PHOTO_BOOTH ->
                        runPhotoBoothCapture(
                            state = state,
                            renderProfile = renderProfile,
                            capturedAtMs = capturedAtMs,
                            captureStampText = captureStampText,
                            fallbackGains = fallbackGains
                        )

                    CaptureKind.VIDEO -> Unit
                }
            } catch (t: Throwable) {
                logger.logError("Viewfinder", "Capture failed", t)
                _uiState.update {
                    it.copy(
                        developingOverlay = null,
                        countdownRemainingSeconds = null,
                        boothSequenceStep = 0,
                        lastErrorMessage = t.message ?: "Capture failed"
                    )
                }
                showBanner("Capture failed")
            } finally {
                _uiState.update {
                    it.copy(
                        isCapturing = false,
                        countdownRemainingSeconds = null,
                        boothSequenceStep = 0
                    )
                }
            }
        }
    }

    private fun triggerShutterPulse(profile: CameraProfile, sfxEnabled: Boolean) {
        soundEngine.playShutter(soundAssetName = profile.soundAssetName, sfxEnabled = sfxEnabled)
        _uiState.update { it.copy(showCaptureFlashPulse = true) }
        viewModelScope.launch {
            delay(140L)
            _uiState.update { it.copy(showCaptureFlashPulse = false) }
        }
    }

    private suspend fun runStillCapture(
        state: ViewfinderUiState,
        renderProfile: CameraProfile,
        capturedAtMs: Long,
        captureStampText: String,
        fallbackGains: com.themasum.aayna.core.model.ShaderExposureFallback
    ) {
        triggerShutterPulse(renderProfile, state.settings.sfxEnabled)
        val recipe =
            CaptureRecipe(
                recipeId = UUID.randomUUID().toString(),
                cameraId = renderProfile.id,
                capturedAtEpochMs = capturedAtMs,
                randomSeed = state.randomSeed,
                mediaType = MediaType.STILL,
                skinId = state.activeSkinId,
                resolutionPreset = state.settings.resolutionPreset,
                opticalSettings = state.opticalSettings,
                stampConfig = state.stampConfig
            )

        val captured =
            cameraController.captureCleanFrame(
                maxEdgePx = state.settings.resolutionPreset.maxLongEdgePx,
                isFrontMirrorEnabled = state.settings.isFrontMirrorEnabled
            )
        var renderedBitmap: Bitmap? = null
        var thumbBitmap: Bitmap? = null
        try {
            when (state.settings.storageMode) {
                CaptureStorageMode.INSTANT_DIGICAM -> {
                    renderedBitmap =
                        offscreenExporter.renderBitmap(
                            sourceBitmap = captured.bitmap,
                            profile = renderProfile,
                            recipe = recipe,
                            stampText = captureStampText,
                            fallbackGains = fallbackGains
                        )
                    val uri =
                        mediaStoreExporter.saveJpegToGallery(
                            bitmap = renderedBitmap,
                            profile = renderProfile,
                            recipe = recipe,
                            jpegQuality = state.settings.clampedJpegQuality()
                        )
                    if (state.settings.dualSaveEnabled) {
                        mediaStoreExporter.saveJpegToGallery(
                            bitmap = captured.bitmap,
                            profile = renderProfile,
                            recipe = recipe,
                            suffix = "ORIG",
                            jpegQuality = state.settings.clampedJpegQuality()
                        )
                    }
                    thumbBitmap = createDownscaledThumbnail(renderedBitmap, maxEdge = 360)
                    val saved =
                        negativeRepository.saveNegative(
                            cleanBitmap = captured.bitmap,
                            thumbnailBitmap = thumbBitmap,
                            recipe = recipe,
                            exportedUri = uri?.toString()
                        )
                    _uiState.update { it.copy(lastCapturedUri = uri?.toString()) }
                    triggerEjectedCard(saved.thumbnailPath, renderProfile.codeName, "SAVED")
                    showBanner("Saved to Pictures/Aayna")
                }

                CaptureStorageMode.NON_DESTRUCTIVE_ROLL -> {
                    thumbBitmap = createDownscaledThumbnail(captured.bitmap, maxEdge = 360)
                    val saved =
                        negativeRepository.saveNegative(
                            cleanBitmap = captured.bitmap,
                            thumbnailBitmap = thumbBitmap,
                            recipe = recipe
                        )
                    if (state.settings.dualSaveEnabled) {
                        mediaStoreExporter.saveJpegToGallery(
                            bitmap = captured.bitmap,
                            profile = renderProfile,
                            recipe = recipe,
                            suffix = "ORIG",
                            jpegQuality = state.settings.clampedJpegQuality()
                        )
                    }
                    triggerEjectedCard(saved.thumbnailPath, renderProfile.codeName, "NEG")
                    showBanner("Negative saved to Studio")
                }

                CaptureStorageMode.ANALOG_DEVELOPING_RITUAL -> {
                    val advanceResult =
                        filmRollRepository.recordShot(
                            cameraId = renderProfile.id,
                            capacity = state.settings.rollCapacity,
                            nowEpochMs = capturedAtMs
                        )
                    soundEngine.playFilmWind(state.settings.sfxEnabled)
                    _uiState.update {
                        it.copy(
                            developingOverlay =
                            DevelopingOverlayState(
                                frameNumber = advanceResult.assignedFrameNumber,
                                capacity = advanceResult.updatedState.capacity,
                                cameraCodeName = renderProfile.codeName
                            )
                        )
                    }
                    val ritualDelay = viewModelScope.async { delay(1500L) }
                    renderedBitmap =
                        offscreenExporter.renderBitmap(
                            sourceBitmap = captured.bitmap,
                            profile = renderProfile,
                            recipe = recipe,
                            stampText = captureStampText,
                            fallbackGains = fallbackGains
                        )
                    val exportedUri =
                        mediaStoreExporter.saveJpegToGallery(
                            bitmap = renderedBitmap,
                            profile = renderProfile,
                            recipe = recipe,
                            jpegQuality = state.settings.clampedJpegQuality()
                        )
                    if (state.settings.dualSaveEnabled) {
                        mediaStoreExporter.saveJpegToGallery(
                            bitmap = captured.bitmap,
                            profile = renderProfile,
                            recipe = recipe,
                            suffix = "ORIG",
                            jpegQuality = state.settings.clampedJpegQuality()
                        )
                    }
                    thumbBitmap = createDownscaledThumbnail(renderedBitmap, maxEdge = 360)
                    val saved =
                        negativeRepository.saveNegative(
                            cleanBitmap = captured.bitmap,
                            thumbnailBitmap = thumbBitmap,
                            recipe = recipe,
                            rollId = advanceResult.updatedState.rollId,
                            frameNumber = advanceResult.assignedFrameNumber,
                            exportedUri = exportedUri?.toString()
                        )
                    ritualDelay.await()
                    _uiState.update {
                        it.copy(
                            developingOverlay = null,
                            lastCapturedUri = exportedUri?.toString(),
                            rollShotsTaken = advanceResult.updatedState.shotsTaken,
                            rollCapacity = advanceResult.updatedState.capacity,
                            showRollCompletePrompt = advanceResult.rollCompleted
                        )
                    }
                    val expLabel = "EXP ${advanceResult.assignedFrameNumber}/${advanceResult.updatedState.capacity}"
                    triggerEjectedCard(saved.thumbnailPath, renderProfile.codeName, expLabel)
                    showBanner("$expLabel developed")
                }
            }
        } finally {
            if (thumbBitmap != null &&
                thumbBitmap !== captured.bitmap &&
                thumbBitmap !== renderedBitmap &&
                !thumbBitmap.isRecycled
            ) {
                thumbBitmap.recycle()
            }
            if (renderedBitmap != null && !renderedBitmap.isRecycled) {
                renderedBitmap.recycle()
            }
            if (!captured.bitmap.isRecycled) {
                captured.bitmap.recycle()
            }
        }
    }

    private suspend fun runD3dBurstCapture(
        state: ViewfinderUiState,
        renderProfile: CameraProfile,
        capturedAtMs: Long,
        captureStampText: String,
        fallbackGains: com.themasum.aayna.core.model.ShaderExposureFallback
    ) {
        triggerShutterPulse(renderProfile, state.settings.sfxEnabled)
        val rawFrames =
            cameraController.captureBurstFrames(
                count = AaynaCameraController.DEFAULT_D3D_BURST_COUNT,
                maxEdgePx = minOf(state.settings.resolutionPreset.maxLongEdgePx, 1080),
                isFrontMirrorEnabled = state.settings.isFrontMirrorEnabled
            )
        val recipe =
            ViewfinderStateReducer.buildD3dBurstRecipe(
                recipeId = UUID.randomUUID().toString(),
                cameraId = renderProfile.id,
                capturedAtEpochMs = capturedAtMs,
                randomSeed = state.randomSeed,
                skinId = state.activeSkinId,
                resolutionPreset = state.settings.resolutionPreset,
                opticalSettings = state.opticalSettings,
                stampConfig = state.stampConfig,
                burstFrameCount = rawFrames.size
            )
        val renderedFrames = mutableListOf<Bitmap>()
        var thumbBitmap: Bitmap? = null
        try {
            val advanceResult =
                if (state.settings.storageMode == CaptureStorageMode.ANALOG_DEVELOPING_RITUAL) {
                    filmRollRepository.recordShot(
                        cameraId = renderProfile.id,
                        capacity = state.settings.rollCapacity,
                        nowEpochMs = capturedAtMs
                    )
                } else {
                    null
                }
            for (frame in rawFrames) {
                renderedFrames +=
                    offscreenExporter.renderBitmap(
                        sourceBitmap = frame.bitmap,
                        profile = renderProfile,
                        recipe = recipe,
                        stampText = captureStampText,
                        fallbackGains = fallbackGains,
                        maxEdgeOverride = 960
                    )
            }
            val gifUri =
                mediaStoreExporter.saveD3dGifToGallery(
                    frames = renderedFrames,
                    profile = renderProfile,
                    recipe = recipe,
                    suffix = "d3d",
                    pingPong = true
                )
            val heroRendered = renderedFrames.first()
            thumbBitmap = createDownscaledThumbnail(heroRendered, maxEdge = 360)
            val saved =
                negativeRepository.saveNegative(
                    cleanBitmap = rawFrames.first().bitmap,
                    thumbnailBitmap = thumbBitmap,
                    recipe = recipe,
                    rollId = advanceResult?.updatedState?.rollId,
                    frameNumber = advanceResult?.assignedFrameNumber,
                    exportedUri = gifUri?.toString(),
                    mediaUri = gifUri?.toString()
                )
            negativeRepository.saveD3dBurstFrames(
                recipeId = recipe.recipeId,
                frames = rawFrames.map { it.bitmap }
            )
            _uiState.update {
                it.copy(
                    lastCapturedUri = gifUri?.toString(),
                    rollShotsTaken = advanceResult?.updatedState?.shotsTaken ?: it.rollShotsTaken,
                    rollCapacity = advanceResult?.updatedState?.capacity ?: it.rollCapacity,
                    showRollCompletePrompt = advanceResult?.rollCompleted ?: false
                )
            }
            triggerEjectedCard(saved.thumbnailPath, renderProfile.codeName, "D3D GIF")
            showBanner("D3D 4-shot GIF saved")
        } finally {
            if (thumbBitmap != null && !thumbBitmap.isRecycled) thumbBitmap.recycle()
            renderedFrames.forEach { if (!it.isRecycled) it.recycle() }
            rawFrames.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
        }
    }

    private suspend fun runPhotoBoothCapture(
        state: ViewfinderUiState,
        renderProfile: CameraProfile,
        capturedAtMs: Long,
        captureStampText: String,
        fallbackGains: com.themasum.aayna.core.model.ShaderExposureFallback
    ) {
        val rawBitmaps = mutableListOf<Bitmap>()
        var compositeBitmap: Bitmap? = null
        var thumbBitmap: Bitmap? = null
        try {
            for (shot in 1..4) {
                _uiState.update { it.copy(boothSequenceStep = shot) }
                triggerShutterPulse(renderProfile, state.settings.sfxEnabled)
                val frame =
                    cameraController.captureCleanFrame(
                        maxEdgePx = minOf(state.settings.resolutionPreset.maxLongEdgePx, 1280),
                        isFrontMirrorEnabled = state.settings.isFrontMirrorEnabled
                    )
                rawBitmaps += frame.bitmap
                if (shot < 4) {
                    delay(550L)
                }
            }
            val renderRecipe =
                ViewfinderStateReducer.buildPhotoBoothRenderRecipe(
                    recipeId = UUID.randomUUID().toString(),
                    cameraId = renderProfile.id,
                    capturedAtEpochMs = capturedAtMs,
                    randomSeed = state.randomSeed,
                    skinId = state.activeSkinId,
                    resolutionPreset = state.settings.resolutionPreset,
                    opticalSettings = state.opticalSettings,
                    stampConfig = state.stampConfig
                )
            val advanceResult =
                if (state.settings.storageMode == CaptureStorageMode.ANALOG_DEVELOPING_RITUAL) {
                    filmRollRepository.recordShot(
                        cameraId = renderProfile.id,
                        capacity = state.settings.rollCapacity,
                        nowEpochMs = capturedAtMs
                    )
                } else {
                    null
                }
            compositeBitmap =
                offscreenExporter.renderPhotoBooth4Strip(
                    frames = rawBitmaps,
                    profile = renderProfile,
                    recipe = renderRecipe,
                    stampText = captureStampText,
                    fallbackGains = fallbackGains
                )
            val savedRecipe = ViewfinderStateReducer.finalizePhotoBoothRecipe(renderRecipe)
            val uri =
                mediaStoreExporter.saveJpegToGallery(
                    bitmap = compositeBitmap,
                    profile = renderProfile,
                    recipe = savedRecipe,
                    suffix = "BOOTH",
                    jpegQuality = state.settings.clampedJpegQuality()
                )
            thumbBitmap = createDownscaledThumbnail(compositeBitmap, maxEdge = 360)
            val saved =
                negativeRepository.saveNegative(
                    cleanBitmap = compositeBitmap,
                    thumbnailBitmap = thumbBitmap,
                    recipe = savedRecipe,
                    rollId = advanceResult?.updatedState?.rollId,
                    frameNumber = advanceResult?.assignedFrameNumber,
                    exportedUri = uri?.toString()
                )
            _uiState.update {
                it.copy(
                    lastCapturedUri = uri?.toString(),
                    rollShotsTaken = advanceResult?.updatedState?.shotsTaken ?: it.rollShotsTaken,
                    rollCapacity = advanceResult?.updatedState?.capacity ?: it.rollCapacity,
                    showRollCompletePrompt = advanceResult?.rollCompleted ?: false
                )
            }
            triggerEjectedCard(saved.thumbnailPath, renderProfile.codeName, "4-STRIP")
            showBanner("Photo Booth 4-strip saved")
        } finally {
            if (thumbBitmap != null && thumbBitmap !== compositeBitmap && !thumbBitmap.isRecycled) {
                thumbBitmap.recycle()
            }
            if (compositeBitmap != null && !compositeBitmap.isRecycled) {
                compositeBitmap.recycle()
            }
            rawBitmaps.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    private fun triggerEjectedCard(thumbnailPath: String?, cameraCodeName: String, label: String) {
        val card =
            EjectedPhotoCardState(
                thumbnailPath = thumbnailPath,
                cameraCodeName = cameraCodeName,
                label = label,
                triggeredAtMs = System.currentTimeMillis()
            )
        _uiState.update { it.copy(ejectedCardPreview = card) }
        ejectedClearJob?.cancel()
        ejectedClearJob =
            viewModelScope.launch {
                delay(1800L)
                _uiState.update { state ->
                    if (state.ejectedCardPreview?.triggeredAtMs == card.triggeredAtMs) {
                        state.copy(ejectedCardPreview = null)
                    } else {
                        state
                    }
                }
            }
    }

    override fun onCleared() {
        stopRecordingElapsedTicker()
        if (cameraController.isRecording.value) {
            cameraController.stopVideoRecording()
        }
        super.onCleared()
    }
}

class ViewfinderViewModelFactory(
    private val appContext: Context,
    private val cameraController: AaynaCameraController,
    private val offscreenExporter: OffscreenGlExporter,
    private val preferencesRepository: AaynaPreferencesRepository,
    private val negativeRepository: NegativeRepository,
    private val filmRollRepository: FilmRollRepository,
    private val mediaStoreExporter: MediaStoreExporter,
    private val soundEngine: AaynaSoundEngine,
    private val logger: AaynaLogger
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ViewfinderViewModel::class.java)) {
            return ViewfinderViewModel(
                appContext = appContext.applicationContext,
                cameraController = cameraController,
                offscreenExporter = offscreenExporter,
                preferencesRepository = preferencesRepository,
                negativeRepository = negativeRepository,
                filmRollRepository = filmRollRepository,
                mediaStoreExporter = mediaStoreExporter,
                soundEngine = soundEngine,
                logger = logger
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

internal fun createDownscaledThumbnail(source: Bitmap, maxEdge: Int): Bitmap {
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
