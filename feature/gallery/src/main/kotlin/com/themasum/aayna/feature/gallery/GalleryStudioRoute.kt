package com.themasum.aayna.feature.gallery

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.themasum.aayna.core.gl.AaynaPersistentGlEngine
import com.themasum.aayna.core.gl.D3dMp4Config
import com.themasum.aayna.core.gl.D3dMp4Encoder
import com.themasum.aayna.core.gl.GlMath
import com.themasum.aayna.core.gl.OffscreenGlExporter
import com.themasum.aayna.core.gl.StudioGlPreviewRenderer
import com.themasum.aayna.core.gl.VideoGlTranscoder
import com.themasum.aayna.core.model.CameraCatalog
import com.themasum.aayna.core.model.CaptureRecipe
import com.themasum.aayna.core.model.DateStampFormatter
import com.themasum.aayna.core.model.FrameBorderStyle
import com.themasum.aayna.core.model.MediaType
import com.themasum.aayna.core.model.UserSettingsSnapshot
import com.themasum.aayna.core.storage.AaynaLogger
import com.themasum.aayna.core.storage.AaynaPreferencesRepository
import com.themasum.aayna.core.storage.AaynaSoundEngine
import com.themasum.aayna.core.storage.FilmRollRepository
import com.themasum.aayna.core.storage.MediaStoreExporter
import com.themasum.aayna.core.storage.NegativeRepository
import com.themasum.aayna.core.storage.ThumbnailMemoryCache
import com.themasum.aayna.core.storage.db.FilmRollEntity
import com.themasum.aayna.core.storage.db.NegativeEntity
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "GalleryStudio"

private data class StudioEditorSession(
    val originalBitmap: Bitmap,
    val undoState: StudioUndoState,
    val editingNegativeId: String?,
    val editingThumbnailPath: String = ""
) {
    val recipe: CaptureRecipe get() = undoState.current
    val canUndo: Boolean get() = undoState.canUndo
    val grainBoost: Float get() = recipe.opticalSettings.grainBoost
    val selectedCropPreset: StudioCropPreset
        get() {
            val (rotW, rotH) =
                GlMath.computeRotatedDimensions(
                    width = originalBitmap.width,
                    height = originalBitmap.height,
                    rotationDegrees = recipe.opticalSettings.rotationDegrees
                )
            return GalleryRecipeReducer.resolveCropPreset(
                opticalSettings = recipe.opticalSettings,
                isSourcePortrait = rotH > rotW
            )
        }
}

private data class D3dSession(
    val frames: List<Bitmap>,
    val recipe: CaptureRecipe,
    val editingNegativeId: String? = null
)

private data class BoothSession(val frames: List<Bitmap>, val recipe: CaptureRecipe)

@Composable
fun GalleryStudioRoute(
    onBack: () -> Unit,
    offscreenExporter: OffscreenGlExporter,
    preferencesRepository: AaynaPreferencesRepository,
    negativeRepository: NegativeRepository,
    filmRollRepository: FilmRollRepository,
    mediaStoreExporter: MediaStoreExporter,
    soundEngine: AaynaSoundEngine,
    logger: AaynaLogger,
    modifier: Modifier = Modifier,
    studioGlRenderer: StudioGlPreviewRenderer? = null,
    thumbnailCache: ThumbnailMemoryCache? = null,
    ledFontFamily: FontFamily = FontFamily.Monospace,
    dotMatrixFontFamily: FontFamily = FontFamily.Monospace,
    vcrOsdFontFamily: FontFamily = FontFamily.Monospace,
    pixelFontFamily: FontFamily = FontFamily.Monospace,
    condensedFontFamily: FontFamily = FontFamily.Monospace,
    persistentGlEngine: AaynaPersistentGlEngine? = null
) {
    val fontSet =
        remember(ledFontFamily, dotMatrixFontFamily, vcrOsdFontFamily, pixelFontFamily, condensedFontFamily) {
            ProCcdFontSet(
                led = ledFontFamily,
                dotMatrix = dotMatrixFontFamily,
                vcrOsd = vcrOsdFontFamily,
                pixel = pixelFontFamily,
                condensed = condensedFontFamily
            )
        }
    val effectiveThumbnailCache = remember(thumbnailCache) { thumbnailCache ?: ThumbnailMemoryCache() }

    CompositionLocalProvider(LocalProCcdFonts provides fontSet) {
        GalleryStudioContent(
            onBack = onBack,
            offscreenExporter = offscreenExporter,
            preferencesRepository = preferencesRepository,
            negativeRepository = negativeRepository,
            filmRollRepository = filmRollRepository,
            mediaStoreExporter = mediaStoreExporter,
            soundEngine = soundEngine,
            logger = logger,
            studioGlRenderer = studioGlRenderer,
            thumbnailCache = effectiveThumbnailCache,
            persistentGlEngine = persistentGlEngine,
            modifier = modifier
        )
    }
}

@Composable
private fun GalleryStudioContent(
    onBack: () -> Unit,
    offscreenExporter: OffscreenGlExporter,
    preferencesRepository: AaynaPreferencesRepository,
    negativeRepository: NegativeRepository,
    filmRollRepository: FilmRollRepository,
    mediaStoreExporter: MediaStoreExporter,
    soundEngine: AaynaSoundEngine,
    logger: AaynaLogger,
    studioGlRenderer: StudioGlPreviewRenderer?,
    thumbnailCache: ThumbnailMemoryCache,
    persistentGlEngine: AaynaPersistentGlEngine?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val settings by preferencesRepository.settingsFlow.collectAsStateWithLifecycle(
        initialValue = UserSettingsSnapshot()
    )
    val activeCameraId by preferencesRepository.activeCameraIdFlow.collectAsStateWithLifecycle(
        initialValue = CameraCatalog.defaultCamera.id
    )
    val favoriteCameraIds by preferencesRepository.favoriteCameraIdsFlow.collectAsStateWithLifecycle(
        initialValue = emptySet()
    )
    val activeNegatives by negativeRepository.activeNegativesFlow.collectAsStateWithLifecycle(
        initialValue = emptyList()
    )
    val trashedNegatives by negativeRepository.trashedNegativesFlow.collectAsStateWithLifecycle(
        initialValue = emptyList()
    )
    val allRolls by filmRollRepository.allRollsFlow.collectAsStateWithLifecycle(
        initialValue = emptyList()
    )

    var selectedTab by remember { mutableStateOf(GalleryFilterTab.ALL) }
    var selectedRoll by remember { mutableStateOf<FilmRollEntity?>(null) }
    val selectedRollFrames by remember(selectedRoll?.rollId) {
        val rollId = selectedRoll?.rollId
        if (rollId != null) {
            negativeRepository.observeNegativesForRoll(rollId)
        } else {
            flowOf(emptyList())
        }
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    var detailEntity by remember { mutableStateOf<NegativeEntity?>(null) }
    var editorSession by remember { mutableStateOf<StudioEditorSession?>(null) }
    var d3dSession by remember { mutableStateOf<D3dSession?>(null) }
    var boothSession by remember { mutableStateOf<BoothSession?>(null) }
    var pendingMultiShotFrames by remember { mutableStateOf<List<Bitmap>?>(null) }

    var showImportDialog by remember { mutableStateOf(false) }
    var isLoadingSource by remember { mutableStateOf(false) }
    var isBusyExport by remember { mutableStateOf(false) }
    var videoExportProgress by remember { mutableStateOf<Float?>(null) }

    val filteredNegatives =
        remember(selectedTab, activeNegatives, trashedNegatives) {
            GalleryFilterHelper.filterNegatives(
                tab = selectedTab,
                activeNegatives = activeNegatives,
                trashedNegatives = trashedNegatives
            )
        }

    val singlePhotoPicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                isLoadingSource = true
                runCatching {
                    val bitmap =
                        withContext(Dispatchers.IO) {
                            GalleryImageDecoder.decodeUriSafely(context.contentResolver, uri)
                        } ?: error("Unable to decode selected image")
                    val initialRecipe =
                        GalleryRecipeReducer.createImportedRecipe(
                            cameraId = activeCameraId,
                            settings = settings
                        )
                    StudioEditorSession(
                        originalBitmap = bitmap,
                        undoState = StudioUndoState(initialRecipe),
                        editingNegativeId = null
                    )
                }.onSuccess { session ->
                    editorSession = session
                    logger.logInfo(TAG, "Opened single photo import in studio editor")
                }.onFailure { err ->
                    logger.logError(TAG, "Single photo import failed", err)
                    snackbarHostState.showSnackbar("Failed to import photo: ${err.message ?: "Unknown error"}")
                }
                isLoadingSource = false
            }
        }

    val multiPhotoPicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 4)
        ) { uris ->
            if (uris.isEmpty()) return@rememberLauncherForActivityResult
            if (uris.size == 1) {
                scope.launch {
                    isLoadingSource = true
                    runCatching {
                        val bitmap =
                            withContext(Dispatchers.IO) {
                                GalleryImageDecoder.decodeUriSafely(context.contentResolver, uris.first())
                            } ?: error("Unable to decode selected image")
                        val initialRecipe =
                            GalleryRecipeReducer.createImportedRecipe(
                                cameraId = activeCameraId,
                                settings = settings
                            )
                        StudioEditorSession(
                            originalBitmap = bitmap,
                            undoState = StudioUndoState(initialRecipe),
                            editingNegativeId = null
                        )
                    }.onSuccess { session ->
                        editorSession = session
                    }.onFailure { err ->
                        snackbarHostState.showSnackbar("Failed to import photo: ${err.message ?: "Error"}")
                    }
                    isLoadingSource = false
                }
                return@rememberLauncherForActivityResult
            }

            scope.launch {
                isLoadingSource = true
                runCatching {
                    withContext(Dispatchers.IO) {
                        uris.take(4).mapNotNull { uri ->
                            GalleryImageDecoder.decodeUriSafely(context.contentResolver, uri, maxEdgePx = 1280)
                        }
                    }
                }.onSuccess { frames ->
                    if (frames.size >= 2) {
                        pendingMultiShotFrames = frames
                        logger.logInfo(TAG, "Imported ${frames.size} shots for Booth/D3D assembly")
                    } else {
                        snackbarHostState.showSnackbar("Select at least 2 valid photos for Booth or D3D")
                    }
                }.onFailure { err ->
                    logger.logError(TAG, "Multi-photo import failed", err)
                    snackbarHostState.showSnackbar("Failed to import multi-shot images")
                }
                isLoadingSource = false
            }
        }

    fun openSinglePicker() {
        showImportDialog = false
        soundEngine.playDialTick(settings.sfxEnabled)
        singlePhotoPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    fun openMultiPicker() {
        showImportDialog = false
        soundEngine.playDialTick(settings.sfxEnabled)
        multiPhotoPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    fun openNegativeInStudio(entity: NegativeEntity) {
        scope.launch {
            isLoadingSource = true
            runCatching {
                val bitmap =
                    withContext(Dispatchers.IO) {
                        GalleryImageDecoder.decodeFileSafely(entity.cleanImagePath)
                    } ?: error("Negative file missing on disk")
                val recipe = entity.toCaptureRecipe()
                StudioEditorSession(
                    originalBitmap = bitmap,
                    undoState = StudioUndoState(recipe),
                    editingNegativeId = entity.recipeId,
                    editingThumbnailPath = entity.thumbnailPath
                )
            }.onSuccess { session ->
                soundEngine.playDialTick(settings.sfxEnabled)
                detailEntity = null
                editorSession = session
            }.onFailure { err ->
                logger.logError(TAG, "Failed to open negative ${entity.recipeId}", err)
                snackbarHostState.showSnackbar("Could not open negative: ${err.message ?: "Missing file"}")
            }
            isLoadingSource = false
        }
    }

    fun openNegativeFromGrid(entity: NegativeEntity) {
        soundEngine.playDialTick(settings.sfxEnabled)
        when (entity.resolvedMediaType()) {
            MediaType.D3D_BURST -> {
                scope.launch {
                    isLoadingSource = true
                    runCatching {
                        val frames =
                            GalleryImageDecoder.loadD3dFrames(
                                filesDir = context.filesDir,
                                recipeId = entity.recipeId,
                                cleanImagePath = entity.cleanImagePath
                            )
                        if (frames.isEmpty()) error("No D3D burst frames found")
                        D3dSession(
                            frames = frames,
                            recipe = entity.toCaptureRecipe(),
                            editingNegativeId = entity.recipeId
                        )
                    }.onSuccess { session ->
                        d3dSession = session
                    }.onFailure { err ->
                        logger.logError(TAG, "Failed to open D3D ${entity.recipeId}", err)
                        snackbarHostState.showSnackbar("Unable to load D3D burst frames")
                    }
                    isLoadingSource = false
                }
            }
            MediaType.VIDEO,
            MediaType.STILL -> detailEntity = entity
        }
    }

    suspend fun exportSingleStillEntity(entity: NegativeEntity): Boolean {
        val bitmap =
            withContext(Dispatchers.IO) {
                GalleryImageDecoder.decodeFileSafely(entity.cleanImagePath)
            } ?: return false
        val recipe = entity.toCaptureRecipe()
        val profile = CameraCatalog.findByIdOrDefault(recipe.cameraId)
        val stampText =
            DateStampFormatter.format(
                epochMillis = recipe.capturedAtEpochMs,
                format = recipe.stampConfig.format,
                useEraYearOffset = recipe.stampConfig.useEraYearOffset,
                eraYear = profile.eraYearOffset,
                customText = recipe.stampConfig.customText
            )
        val params =
            GalleryRecipeReducer.buildGlRenderParams(
                recipe = recipe,
                profile = profile,
                stampText = stampText
            )
        val rendered =
            try {
                offscreenExporter.renderBitmap(
                    sourceBitmap = bitmap,
                    profile = profile,
                    recipe = recipe,
                    stampText = stampText,
                    fallbackGains = params.fallbackGains,
                    skipStamp = params.skipStamp
                )
            } finally {
                bitmap.recycle()
            }
        val uri =
            try {
                mediaStoreExporter.saveJpegToGallery(
                    bitmap = rendered,
                    profile = profile,
                    recipe = recipe,
                    jpegQuality = settings.clampedJpegQuality()
                )
            } finally {
                rendered.recycle()
            }
        if (uri != null) {
            negativeRepository.updateExportedUri(entity.recipeId, uri.toString())
            return true
        }
        return false
    }

    suspend fun exportSingleD3dEntity(entity: NegativeEntity): Boolean {
        val frames =
            GalleryImageDecoder.loadD3dFrames(
                filesDir = context.filesDir,
                recipeId = entity.recipeId,
                cleanImagePath = entity.cleanImagePath,
                maxEdgePx = 720
            )
        if (frames.isEmpty()) return false
        val recipe = entity.toCaptureRecipe()
        val profile = CameraCatalog.findByIdOrDefault(recipe.cameraId)
        val stampText =
            DateStampFormatter.format(
                epochMillis = recipe.capturedAtEpochMs,
                format = recipe.stampConfig.format,
                useEraYearOffset = recipe.stampConfig.useEraYearOffset,
                eraYear = profile.eraYearOffset,
                customText = recipe.stampConfig.customText
            )
        val params =
            GalleryRecipeReducer.buildGlRenderParams(
                recipe = recipe,
                profile = profile,
                stampText = stampText
            )
        val graded =
            try {
                frames.map { frame ->
                    offscreenExporter.renderBitmap(
                        sourceBitmap = frame,
                        profile = profile,
                        recipe = recipe,
                        stampText = stampText,
                        fallbackGains = params.fallbackGains,
                        maxEdgeOverride = 720,
                        skipStamp = params.skipStamp
                    )
                }
            } finally {
                frames.forEach { it.recycle() }
            }
        val uri =
            try {
                mediaStoreExporter.saveD3dGifToGallery(
                    frames = graded,
                    profile = profile,
                    recipe = recipe,
                    frameDelayMs = GalleryD3dTimingMath.DEFAULT_FRAME_DELAY_MS,
                    pingPong = true
                )
            } finally {
                graded.forEach { it.recycle() }
            }
        if (uri != null) {
            negativeRepository.updateExportedUri(entity.recipeId, uri.toString())
            return true
        }
        return false
    }

    Scaffold(
        modifier = modifier.fillMaxSize().background(ProCcdPalette.VoidObsidian).systemBarsPadding(),
        containerColor = ProCcdPalette.VoidObsidian,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = ProCcdPalette.SurfaceElevated,
                    contentColor = ProCcdPalette.CreamInk,
                    actionColor = ProCcdPalette.WarmAmber
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val currentEditor = editorSession
            val currentD3d = d3dSession
            val currentBooth = boothSession
            val currentDetail = detailEntity

            when {
                currentEditor != null -> {
                    GalleryStudioEditor(
                        sourceBitmap = currentEditor.originalBitmap,
                        recipe = currentEditor.recipe,
                        isSavedNegative = currentEditor.editingNegativeId != null,
                        isExporting = isBusyExport,
                        hapticsEnabled = settings.hapticsEnabled,
                        offscreenExporter = offscreenExporter,
                        studioGlRenderer = studioGlRenderer,
                        favoriteCameraIds = favoriteCameraIds,
                        selectedCropPreset = currentEditor.selectedCropPreset,
                        grainBoost = currentEditor.grainBoost,
                        canUndo = currentEditor.canUndo,
                        onUndo = {
                            if (currentEditor.canUndo) {
                                editorSession = currentEditor.copy(undoState = currentEditor.undoState.undo())
                            }
                        },
                        onGrainBoostChange = { boost ->
                            val nextRecipe = GalleryRecipeReducer.updateGrainBoost(currentEditor.recipe, boost)
                            editorSession = currentEditor.copy(undoState = currentEditor.undoState.push(nextRecipe))
                        },
                        onSelectCropPreset = { preset ->
                            val nextRecipe = GalleryRecipeReducer.updateCropPreset(currentEditor.recipe, preset)
                            editorSession = currentEditor.copy(undoState = currentEditor.undoState.push(nextRecipe))
                        },
                        onRotate90Cw = {
                            val nextRecipe = GalleryRecipeReducer.rotate90Cw(currentEditor.recipe)
                            editorSession = currentEditor.copy(undoState = currentEditor.undoState.push(nextRecipe))
                        },
                        onToggleFavoriteCamera = { camId ->
                            scope.launch { preferencesRepository.toggleFavoriteCamera(camId) }
                        },
                        onRecipeChange = { updated ->
                            editorSession = currentEditor.copy(undoState = currentEditor.undoState.push(updated))
                        },
                        onDialTick = { soundEngine.playDialTick(settings.sfxEnabled) },
                        onSaveEdits = {
                            if (isBusyExport) return@GalleryStudioEditor
                            scope.launch {
                                isBusyExport = true
                                val recipe = currentEditor.recipe
                                val profile = CameraCatalog.findByIdOrDefault(recipe.cameraId)
                                val stampText =
                                    DateStampFormatter.format(
                                        epochMillis = recipe.capturedAtEpochMs,
                                        format = recipe.stampConfig.format,
                                        useEraYearOffset = recipe.stampConfig.useEraYearOffset,
                                        eraYear = profile.eraYearOffset,
                                        customText = recipe.stampConfig.customText
                                    )
                                val params =
                                    GalleryRecipeReducer.buildGlRenderParams(
                                        recipe = recipe,
                                        profile = profile,
                                        stampText = stampText,
                                        grainBoost = currentEditor.grainBoost
                                    )
                                runCatching {
                                    val fullGraded =
                                        offscreenExporter.renderBitmap(
                                            sourceBitmap = currentEditor.originalBitmap,
                                            profile = profile,
                                            recipe = recipe,
                                            stampText = stampText,
                                            fallbackGains = params.fallbackGains,
                                            skipStamp = params.skipStamp
                                        )
                                    val gradedThumbnail =
                                        try {
                                            GalleryImageDecoder.createScaledThumbnail(fullGraded)
                                        } finally {
                                            fullGraded.recycle()
                                        }
                                    try {
                                        val negId = currentEditor.editingNegativeId
                                        if (negId != null) {
                                            if (currentEditor.editingThumbnailPath.isNotBlank()) {
                                                thumbnailCache.invalidatePath(currentEditor.editingThumbnailPath)
                                            }
                                            val updatedThumbPath =
                                                negativeRepository.updateRecipeAndThumbnail(
                                                    recipe = recipe,
                                                    gradedThumbnailBitmap = gradedThumbnail
                                                )
                                            editorSession =
                                                currentEditor.copy(editingThumbnailPath = updatedThumbPath)
                                        } else {
                                            val saved =
                                                negativeRepository.saveNegative(
                                                    cleanBitmap = currentEditor.originalBitmap,
                                                    thumbnailBitmap = gradedThumbnail,
                                                    recipe = recipe
                                                )
                                            editorSession =
                                                currentEditor.copy(
                                                    editingNegativeId = saved.recipeId,
                                                    editingThumbnailPath = saved.thumbnailPath
                                                )
                                        }
                                    } finally {
                                        gradedThumbnail.recycle()
                                    }
                                }.onSuccess {
                                    soundEngine.playFilmWind(settings.sfxEnabled)
                                    if (settings.hapticsEnabled) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                    logger.logInfo(TAG, "Saved studio edits & graded thumbnail for ${recipe.recipeId}")
                                    snackbarHostState.showSnackbar("Edits saved to Works")
                                }.onFailure { err ->
                                    logger.logError(TAG, "Save edits failed", err)
                                    snackbarHostState.showSnackbar("Failed to save edits: ${err.message ?: "Error"}")
                                }
                                isBusyExport = false
                            }
                        },
                        onExportToGallery = {
                            if (isBusyExport) return@GalleryStudioEditor
                            scope.launch {
                                isBusyExport = true
                                val recipe = currentEditor.recipe
                                val profile = CameraCatalog.findByIdOrDefault(recipe.cameraId)
                                val stampText =
                                    DateStampFormatter.format(
                                        epochMillis = recipe.capturedAtEpochMs,
                                        format = recipe.stampConfig.format,
                                        useEraYearOffset = recipe.stampConfig.useEraYearOffset,
                                        eraYear = profile.eraYearOffset,
                                        customText = recipe.stampConfig.customText
                                    )
                                val params =
                                    GalleryRecipeReducer.buildGlRenderParams(
                                        recipe = recipe,
                                        profile = profile,
                                        stampText = stampText,
                                        grainBoost = currentEditor.grainBoost
                                    )
                                runCatching {
                                    val fullBitmap =
                                        offscreenExporter.renderBitmap(
                                            sourceBitmap = currentEditor.originalBitmap,
                                            profile = profile,
                                            recipe = recipe,
                                            stampText = stampText,
                                            fallbackGains = params.fallbackGains,
                                            skipStamp = params.skipStamp
                                        )
                                    val exportedUri =
                                        try {
                                            mediaStoreExporter.saveJpegToGallery(
                                                bitmap = fullBitmap,
                                                profile = profile,
                                                recipe = recipe,
                                                jpegQuality = settings.clampedJpegQuality()
                                            )
                                        } finally {
                                            fullBitmap.recycle()
                                        }
                                    val negId = currentEditor.editingNegativeId
                                    if (negId != null && exportedUri != null) {
                                        negativeRepository.updateRecipe(recipe)
                                        negativeRepository.updateExportedUri(negId, exportedUri.toString())
                                    }
                                    exportedUri
                                }.onSuccess { exportedUri ->
                                    soundEngine.playShutter(profile.soundAssetName, settings.sfxEnabled)
                                    if (settings.hapticsEnabled) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                    logger.logInfo(TAG, "Exported studio image to gallery: $exportedUri")
                                    snackbarHostState.showSnackbar("Exported to Pictures/Aayna")
                                }.onFailure { err ->
                                    logger.logError(TAG, "Gallery export failed", err)
                                    snackbarHostState.showSnackbar("Export failed: ${err.message ?: "Unknown error"}")
                                }
                                isBusyExport = false
                            }
                        },
                        onCloseEditor = { editorSession = null }
                    )
                }

                currentD3d != null -> {
                    D3dWigglegramStudioScreen(
                        sourceFrames = currentD3d.frames,
                        recipe = currentD3d.recipe,
                        isSavedNegative = currentD3d.editingNegativeId != null,
                        isBusy = isBusyExport,
                        offscreenExporter = offscreenExporter,
                        onRecipeChange = { updated -> d3dSession = currentD3d.copy(recipe = updated) },
                        onSaveToWorks = { activeFrames ->
                            if (isBusyExport || activeFrames.isEmpty()) return@D3dWigglegramStudioScreen
                            scope.launch {
                                isBusyExport = true
                                runCatching {
                                    val recipe =
                                        currentD3d.recipe.copy(
                                            mediaType = MediaType.D3D_BURST,
                                            burstFrameCount = currentD3d.frames.size
                                        )
                                    val thumb = GalleryImageDecoder.createScaledThumbnail(activeFrames.first())
                                    try {
                                        val negId = currentD3d.editingNegativeId
                                        if (negId != null) {
                                            negativeRepository.updateRecipeAndThumbnail(
                                                recipe = recipe,
                                                gradedThumbnailBitmap = thumb
                                            )
                                            d3dSession = currentD3d.copy(recipe = recipe)
                                        } else {
                                            GalleryImageDecoder.saveD3dFramesToDir(
                                                filesDir = context.filesDir,
                                                recipeId = recipe.recipeId,
                                                frames = currentD3d.frames
                                            )
                                            val saved =
                                                negativeRepository.saveNegative(
                                                    cleanBitmap = currentD3d.frames.first(),
                                                    thumbnailBitmap = thumb,
                                                    recipe = recipe
                                                )
                                            d3dSession =
                                                currentD3d.copy(
                                                    editingNegativeId = saved.recipeId,
                                                    recipe = recipe
                                                )
                                        }
                                    } finally {
                                        thumb.recycle()
                                    }
                                }.onSuccess {
                                    soundEngine.playFilmWind(settings.sfxEnabled)
                                    snackbarHostState.showSnackbar("D3D Wigglegram saved to Works")
                                }.onFailure { err ->
                                    logger.logError(TAG, "Save D3D failed", err)
                                    snackbarHostState.showSnackbar("Failed to save D3D")
                                }
                                isBusyExport = false
                            }
                        },
                        onExportLoopingGif = { activeFrames, delayMs, pingPong ->
                            if (isBusyExport || activeFrames.isEmpty()) return@D3dWigglegramStudioScreen
                            scope.launch {
                                isBusyExport = true
                                val recipe = currentD3d.recipe
                                val profile = CameraCatalog.findByIdOrDefault(recipe.cameraId)
                                runCatching {
                                    mediaStoreExporter.saveD3dGifToGallery(
                                        frames = activeFrames,
                                        profile = profile,
                                        recipe = recipe,
                                        frameDelayMs = GalleryD3dTimingMath.clampFrameDelayMs(delayMs),
                                        pingPong = pingPong
                                    )
                                }.onSuccess { uri ->
                                    val negId = currentD3d.editingNegativeId
                                    if (negId != null && uri != null) {
                                        negativeRepository.updateExportedUri(negId, uri.toString())
                                    }
                                    soundEngine.playShutter(profile.soundAssetName, settings.sfxEnabled)
                                    snackbarHostState.showSnackbar("Looping GIF exported to Pictures/Aayna")
                                }.onFailure { err ->
                                    logger.logError(TAG, "GIF export failed", err)
                                    snackbarHostState.showSnackbar("GIF export failed: ${err.message ?: "Error"}")
                                }
                                isBusyExport = false
                            }
                        },
                        onExportMp4 = { delayMs, pingPong ->
                            if (isBusyExport || currentD3d.frames.isEmpty()) return@D3dWigglegramStudioScreen
                            scope.launch {
                                isBusyExport = true
                                val recipe = currentD3d.recipe
                                val profile = CameraCatalog.findByIdOrDefault(recipe.cameraId)
                                val stampText =
                                    DateStampFormatter.format(
                                        epochMillis = recipe.capturedAtEpochMs,
                                        format = recipe.stampConfig.format,
                                        useEraYearOffset = recipe.stampConfig.useEraYearOffset,
                                        eraYear = profile.eraYearOffset,
                                        customText = recipe.stampConfig.customText
                                    )
                                val params =
                                    GalleryRecipeReducer.buildGlRenderParams(
                                        recipe = recipe,
                                        profile = profile,
                                        stampText = stampText
                                    )
                                val gradedFrames = mutableListOf<Bitmap>()
                                var tempMp4: File? = null
                                runCatching {
                                    currentD3d.frames.forEach { frame ->
                                        gradedFrames +=
                                            offscreenExporter.renderBitmap(
                                                sourceBitmap = frame,
                                                profile = profile,
                                                recipe = recipe,
                                                stampText = stampText,
                                                fallbackGains = params.fallbackGains,
                                                maxEdgeOverride = D3dMp4Config.DEFAULT_MAX_EDGE_PX,
                                                skipStamp = params.skipStamp
                                            )
                                    }
                                    val outFile = File.createTempFile("aayna_d3d_", ".mp4", context.cacheDir)
                                    tempMp4 = outFile
                                    withPersistentGlEngine(persistentGlEngine, context) { glEngine ->
                                        D3dMp4Encoder(glEngine).encode(
                                            frames = gradedFrames,
                                            outputFile = outFile,
                                            frameDelayMs = delayMs,
                                            pingPong = pingPong
                                        )
                                    }
                                    mediaStoreExporter.saveVideoFileToGallery(
                                        sourceFile = outFile,
                                        profile = profile,
                                        capturedAtMs = recipe.capturedAtEpochMs,
                                        fileSuffix = "d3d"
                                    )
                                }.onSuccess { uri ->
                                    val negId = currentD3d.editingNegativeId
                                    if (negId != null && uri != null) {
                                        negativeRepository.updateExportedUri(negId, uri.toString())
                                    }
                                    soundEngine.playShutter(profile.soundAssetName, settings.sfxEnabled)
                                    snackbarHostState.showSnackbar("D3D MP4 exported to Movies/Aayna")
                                }.onFailure { err ->
                                    logger.logError(TAG, "D3D MP4 export failed", err)
                                    snackbarHostState.showSnackbar("MP4 export failed: ${err.message ?: "Error"}")
                                }.also {
                                    gradedFrames.forEach { it.recycle() }
                                    tempMp4?.delete()
                                }
                                isBusyExport = false
                            }
                        },
                        onBack = { d3dSession = null }
                    )
                }

                currentBooth != null -> {
                    PhotoBoothStudioScreen(
                        sourceFrames = currentBooth.frames,
                        recipe = currentBooth.recipe,
                        isBusy = isBusyExport,
                        offscreenExporter = offscreenExporter,
                        onRecipeChange = { updated -> boothSession = currentBooth.copy(recipe = updated) },
                        onSwitchToD3d = {
                            val d3dRecipe =
                                currentBooth.recipe.copy(
                                    mediaType = MediaType.D3D_BURST,
                                    burstFrameCount = currentBooth.frames.size,
                                    opticalSettings =
                                    currentBooth.recipe.opticalSettings.copy(
                                        frameStyle = FrameBorderStyle.NONE
                                    )
                                )
                            boothSession = null
                            d3dSession = D3dSession(frames = currentBooth.frames, recipe = d3dRecipe)
                        },
                        onSaveStripToWorks = { strip ->
                            if (isBusyExport) return@PhotoBoothStudioScreen
                            scope.launch {
                                isBusyExport = true
                                runCatching {
                                    val recipe =
                                        currentBooth.recipe.copy(
                                            opticalSettings =
                                            currentBooth.recipe.opticalSettings.copy(
                                                frameStyle = FrameBorderStyle.NONE
                                            ),
                                            isPreComposited = true
                                        )
                                    val thumb = GalleryImageDecoder.createScaledThumbnail(strip)
                                    try {
                                        negativeRepository.saveNegative(
                                            cleanBitmap = strip,
                                            thumbnailBitmap = thumb,
                                            recipe = recipe
                                        )
                                    } finally {
                                        thumb.recycle()
                                    }
                                }.onSuccess {
                                    soundEngine.playFilmWind(settings.sfxEnabled)
                                    snackbarHostState.showSnackbar("Photo Booth 4-Strip saved to Works")
                                }.onFailure { err ->
                                    logger.logError(TAG, "Save Booth failed", err)
                                    snackbarHostState.showSnackbar("Failed to save strip")
                                }
                                isBusyExport = false
                            }
                        },
                        onExportStripJpeg = { strip ->
                            if (isBusyExport) return@PhotoBoothStudioScreen
                            scope.launch {
                                isBusyExport = true
                                val recipe = currentBooth.recipe
                                val profile = CameraCatalog.findByIdOrDefault(recipe.cameraId)
                                runCatching {
                                    mediaStoreExporter.saveJpegToGallery(
                                        bitmap = strip,
                                        profile = profile,
                                        recipe = recipe,
                                        suffix = "booth",
                                        jpegQuality = settings.clampedJpegQuality()
                                    )
                                }.onSuccess {
                                    soundEngine.playShutter(profile.soundAssetName, settings.sfxEnabled)
                                    snackbarHostState.showSnackbar("Booth 4-Strip exported to Pictures/Aayna")
                                }.onFailure { err ->
                                    logger.logError(TAG, "Booth export failed", err)
                                    snackbarHostState.showSnackbar("Booth export failed")
                                }
                                isBusyExport = false
                            }
                        },
                        onBack = { boothSession = null }
                    )
                }

                currentDetail != null -> {
                    if (currentDetail.resolvedMediaType() == MediaType.VIDEO) {
                        VideoDetailScreen(
                            entity = currentDetail,
                            thumbnailCache = thumbnailCache,
                            isBusy = isBusyExport,
                            exportProgress = videoExportProgress,
                            onBack = { detailEntity = null },
                            onPlayOffline = {
                                val uriStr = currentDetail.mediaUri ?: currentDetail.exportedUri
                                if (uriStr.isNullOrBlank()) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("No MediaStore URI recorded for clip")
                                    }
                                    return@VideoDetailScreen
                                }
                                val shareableUri = MediaStoreExporter.resolveShareableVideoUri(context, uriStr)
                                if (shareableUri == null) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Video file is unavailable")
                                    }
                                    return@VideoDetailScreen
                                }
                                runCatching {
                                    val intent =
                                        Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(shareableUri, "video/mp4")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                    context.startActivity(intent)
                                }.onFailure {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("No offline video player found")
                                    }
                                }
                            },
                            onShareOffline = {
                                val uriStr = currentDetail.mediaUri ?: currentDetail.exportedUri
                                if (uriStr.isNullOrBlank()) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("No MediaStore URI recorded for clip")
                                    }
                                    return@VideoDetailScreen
                                }
                                val shareableUri = MediaStoreExporter.resolveShareableVideoUri(context, uriStr)
                                if (shareableUri == null) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Video file is unavailable")
                                    }
                                    return@VideoDetailScreen
                                }
                                runCatching {
                                    val shareIntent =
                                        Intent(Intent.ACTION_SEND).apply {
                                            type = "video/mp4"
                                            putExtra(Intent.EXTRA_STREAM, shareableUri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Aayna Video"))
                                }.onFailure {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Unable to share video")
                                    }
                                }
                            },
                            onExportEditedMp4 = { editedRecipe, trimStartMs, trimEndMs ->
                                if (isBusyExport) return@VideoDetailScreen
                                val rawUri = currentDetail.mediaUri ?: currentDetail.exportedUri
                                val sourceUri =
                                    rawUri?.let { MediaStoreExporter.resolveShareableVideoUri(context, it) }
                                if (sourceUri == null) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Source video file is unavailable")
                                    }
                                    return@VideoDetailScreen
                                }
                                scope.launch {
                                    isBusyExport = true
                                    videoExportProgress = 0f
                                    val profile = CameraCatalog.findByIdOrDefault(editedRecipe.cameraId)
                                    val stampText =
                                        DateStampFormatter.format(
                                            epochMillis = editedRecipe.capturedAtEpochMs,
                                            format = editedRecipe.stampConfig.format,
                                            useEraYearOffset = editedRecipe.stampConfig.useEraYearOffset,
                                            eraYear = profile.eraYearOffset,
                                            customText = editedRecipe.stampConfig.customText
                                        )
                                    val params =
                                        GalleryRecipeReducer.buildGlRenderParams(
                                            recipe = editedRecipe,
                                            profile = profile,
                                            stampText = stampText
                                        )
                                    var tempMp4: File? = null
                                    runCatching {
                                        val outFile = File.createTempFile("aayna_vid_edit_", ".mp4", context.cacheDir)
                                        tempMp4 = outFile
                                        withPersistentGlEngine(persistentGlEngine, context) { glEngine ->
                                            VideoGlTranscoder(context, glEngine).transcode(
                                                sourceUri = sourceUri,
                                                outputFile = outFile,
                                                params = params,
                                                trimStartMs = trimStartMs,
                                                trimEndMs = trimEndMs,
                                                onProgress = { progress -> videoExportProgress = progress }
                                            )
                                        }
                                        mediaStoreExporter.saveVideoFileToGallery(
                                            sourceFile = outFile,
                                            profile = profile,
                                            capturedAtMs = System.currentTimeMillis(),
                                            fileSuffix = "edit"
                                        )
                                    }.onSuccess { exportedUri ->
                                        if (exportedUri != null) {
                                            negativeRepository.updateExportedUri(
                                                currentDetail.recipeId,
                                                exportedUri.toString()
                                            )
                                        }
                                        soundEngine.playShutter(profile.soundAssetName, settings.sfxEnabled)
                                        snackbarHostState.showSnackbar("Edited MP4 exported to Movies/Aayna")
                                    }.onFailure { err ->
                                        logger.logError(TAG, "Video MP4 export failed", err)
                                        snackbarHostState.showSnackbar("MP4 export failed: ${err.message ?: "Error"}")
                                    }.also {
                                        tempMp4?.delete()
                                    }
                                    videoExportProgress = null
                                    isBusyExport = false
                                }
                            },
                            onSaveCaption = { newCaption ->
                                scope.launch {
                                    val updated =
                                        GalleryRecipeReducer.updateCaption(
                                            currentDetail.toCaptureRecipe(),
                                            newCaption
                                        )
                                    negativeRepository.updateRecipe(updated)
                                    detailEntity = currentDetail.copy(caption = updated.caption)
                                    soundEngine.playDialTick(settings.sfxEnabled)
                                    snackbarHostState.showSnackbar("Caption saved")
                                }
                            },
                            onSoftDelete = {
                                scope.launch {
                                    negativeRepository.softDelete(currentDetail.recipeId)
                                    detailEntity = null
                                    soundEngine.playDialTick(settings.sfxEnabled)
                                    snackbarHostState.showSnackbar("Moved to Trash")
                                }
                            }
                        )
                    } else {
                        PhotoDetailScreen(
                            entity = currentDetail,
                            offscreenExporter = offscreenExporter,
                            onBack = { detailEntity = null },
                            onOpenInStudio = { openNegativeInStudio(currentDetail) },
                            onSaveCaption = { newCaption ->
                                scope.launch {
                                    val updated =
                                        GalleryRecipeReducer.updateCaption(
                                            currentDetail.toCaptureRecipe(),
                                            newCaption
                                        )
                                    negativeRepository.updateRecipe(updated)
                                    detailEntity = currentDetail.copy(caption = updated.caption)
                                    soundEngine.playDialTick(settings.sfxEnabled)
                                    snackbarHostState.showSnackbar("Caption saved")
                                }
                            },
                            onExportJpeg = {
                                if (isBusyExport) return@PhotoDetailScreen
                                scope.launch {
                                    isBusyExport = true
                                    runCatching { exportSingleStillEntity(currentDetail) }
                                        .onSuccess { ok ->
                                            if (ok) {
                                                soundEngine.playShutter(
                                                    CameraCatalog.findByIdOrDefault(
                                                        currentDetail.cameraId
                                                    ).soundAssetName,
                                                    settings.sfxEnabled
                                                )
                                                snackbarHostState.showSnackbar("Exported to Pictures/Aayna")
                                            } else {
                                                snackbarHostState.showSnackbar("Export failed")
                                            }
                                        }
                                        .onFailure { err ->
                                            logger.logError(TAG, "Detail export failed", err)
                                            snackbarHostState.showSnackbar("Export failed")
                                        }
                                    isBusyExport = false
                                }
                            },
                            onSoftDelete = {
                                scope.launch {
                                    negativeRepository.softDelete(currentDetail.recipeId)
                                    detailEntity = null
                                    soundEngine.playDialTick(settings.sfxEnabled)
                                    snackbarHostState.showSnackbar("Moved to Trash")
                                }
                            },
                            isBusy = isBusyExport
                        )
                    }
                }

                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        WorksTopHeader(
                            selectedTab = selectedTab,
                            isSelectionMode = isSelectionMode,
                            onBack = onBack,
                            onToggleSelectMode = {
                                isSelectionMode = !isSelectionMode
                                if (!isSelectionMode) selectedIds = emptySet()
                            },
                            onSelectTab = { tab ->
                                soundEngine.playDialTick(settings.sfxEnabled)
                                selectedTab = tab
                                isSelectionMode = false
                                selectedIds = emptySet()
                                if (tab != GalleryFilterTab.ROLLS) {
                                    selectedRoll = null
                                }
                            },
                            onImportPhotoClick = { showImportDialog = true }
                        )

                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            when (selectedTab) {
                                GalleryFilterTab.ROLLS ->
                                    FilmRollsView(
                                        rolls = allRolls,
                                        selectedRoll = selectedRoll,
                                        selectedRollFrames = selectedRollFrames,
                                        thumbnailCache = thumbnailCache,
                                        isSelectionMode = isSelectionMode,
                                        selectedIds = selectedIds,
                                        onToggleSelect = { id ->
                                            selectedIds =
                                                if (selectedIds.contains(id)) {
                                                    selectedIds - id
                                                } else {
                                                    selectedIds + id
                                                }
                                        },
                                        onEnterSelection = { id ->
                                            isSelectionMode = true
                                            selectedIds = selectedIds + id
                                        },
                                        onSelectRoll = { roll ->
                                            soundEngine.playDialTick(settings.sfxEnabled)
                                            selectedRoll = roll
                                            isSelectionMode = false
                                            selectedIds = emptySet()
                                        },
                                        onLoadNewRoll = {
                                            scope.launch {
                                                runCatching {
                                                    filmRollRepository.loadNewRoll(
                                                        cameraId = activeCameraId,
                                                        capacity = settings.rollCapacity
                                                    )
                                                }.onSuccess {
                                                    soundEngine.playFilmWind(settings.sfxEnabled)
                                                    if (settings.hapticsEnabled) {
                                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    }
                                                    snackbarHostState.showSnackbar(
                                                        "Loaded fresh ${settings.rollCapacity}-exp roll"
                                                    )
                                                }.onFailure { err ->
                                                    logger.logError(TAG, "Load new roll failed", err)
                                                    snackbarHostState.showSnackbar("Failed to load new roll")
                                                }
                                            }
                                        },
                                        onOpenFrame = ::openNegativeFromGrid,
                                        onSoftDeleteFrame = { entity ->
                                            scope.launch {
                                                negativeRepository.softDelete(entity.recipeId)
                                                soundEngine.playDialTick(settings.sfxEnabled)
                                                snackbarHostState.showSnackbar("Frame moved to Trash")
                                            }
                                        },
                                        onImportPhotoClick = { showImportDialog = true },
                                        modifier = Modifier.fillMaxSize()
                                    )

                                else ->
                                    WorksGalleryGrid(
                                        negatives = filteredNegatives,
                                        activeTab = selectedTab,
                                        thumbnailCache = thumbnailCache,
                                        isSelectionMode = isSelectionMode,
                                        selectedIds = selectedIds,
                                        onToggleSelect = { id ->
                                            selectedIds =
                                                if (selectedIds.contains(id)) {
                                                    selectedIds - id
                                                } else {
                                                    selectedIds + id
                                                }
                                        },
                                        onEnterSelection = { id ->
                                            isSelectionMode = true
                                            selectedIds = selectedIds + id
                                        },
                                        onOpenNegative = ::openNegativeFromGrid,
                                        onSoftDeleteNegative = { entity ->
                                            scope.launch {
                                                negativeRepository.softDelete(entity.recipeId)
                                                soundEngine.playDialTick(settings.sfxEnabled)
                                                snackbarHostState.showSnackbar("Moved to Trash")
                                            }
                                        },
                                        onRestoreNegative = { entity ->
                                            scope.launch {
                                                negativeRepository.restoreFromTrash(entity.recipeId)
                                                soundEngine.playDialTick(settings.sfxEnabled)
                                                snackbarHostState.showSnackbar("Restored from Trash")
                                            }
                                        },
                                        onPurgeExpiredTrash = {
                                            scope.launch {
                                                val purged = negativeRepository.purgeExpiredTrash()
                                                soundEngine.playDialTick(settings.sfxEnabled)
                                                snackbarHostState.showSnackbar("Purged $purged expired item(s)")
                                            }
                                        },
                                        onEmptyTrash = {
                                            scope.launch {
                                                trashedNegatives.forEach { entity ->
                                                    GalleryImageDecoder.deleteD3dFramesDir(
                                                        context.filesDir,
                                                        entity.recipeId
                                                    )
                                                    negativeRepository.deleteNegative(entity.recipeId)
                                                }
                                                soundEngine.playDialTick(settings.sfxEnabled)
                                                snackbarHostState.showSnackbar("Trash emptied")
                                            }
                                        },
                                        onImportPhotoClick = { showImportDialog = true },
                                        modifier = Modifier.fillMaxSize()
                                    )
                            }
                        }

                        if (isSelectionMode) {
                            val currentList =
                                if (selectedTab == GalleryFilterTab.ROLLS && selectedRoll != null) {
                                    selectedRollFrames
                                } else {
                                    filteredNegatives
                                }
                            BatchSelectionToolbar(
                                selectedCount = selectedIds.size,
                                isTrashTab = selectedTab == GalleryFilterTab.TRASH,
                                isBatchBusy = isBusyExport,
                                onSelectAll = {
                                    selectedIds = currentList.map { it.recipeId }.toSet()
                                },
                                onClearSelection = {
                                    isSelectionMode = false
                                    selectedIds = emptySet()
                                },
                                onBatchExport = {
                                    if (isBusyExport || selectedIds.isEmpty()) return@BatchSelectionToolbar
                                    scope.launch {
                                        isBusyExport = true
                                        val targets = currentList.filter { selectedIds.contains(it.recipeId) }
                                        var exportedCount = 0
                                        targets.forEach { entity ->
                                            val ok =
                                                runCatching {
                                                    when (entity.resolvedMediaType()) {
                                                        MediaType.D3D_BURST -> exportSingleD3dEntity(entity)
                                                        MediaType.STILL -> exportSingleStillEntity(entity)
                                                        MediaType.VIDEO -> false
                                                    }
                                                }.getOrDefault(false)
                                            if (ok) exportedCount++
                                        }
                                        isBusyExport = false
                                        isSelectionMode = false
                                        selectedIds = emptySet()
                                        soundEngine.playFilmWind(settings.sfxEnabled)
                                        snackbarHostState.showSnackbar(
                                            "Batch exported $exportedCount/${targets.size} item(s)"
                                        )
                                    }
                                },
                                onBatchSoftDelete = {
                                    if (selectedIds.isEmpty()) return@BatchSelectionToolbar
                                    scope.launch {
                                        val targets = selectedIds.toList()
                                        targets.forEach { id -> negativeRepository.softDelete(id) }
                                        isSelectionMode = false
                                        selectedIds = emptySet()
                                        soundEngine.playDialTick(settings.sfxEnabled)
                                        snackbarHostState.showSnackbar("Moved ${targets.size} item(s) to Trash")
                                    }
                                },
                                onBatchRestore = {
                                    if (selectedIds.isEmpty()) return@BatchSelectionToolbar
                                    scope.launch {
                                        val targets = selectedIds.toList()
                                        targets.forEach { id -> negativeRepository.restoreFromTrash(id) }
                                        isSelectionMode = false
                                        selectedIds = emptySet()
                                        soundEngine.playDialTick(settings.sfxEnabled)
                                        snackbarHostState.showSnackbar("Restored ${targets.size} item(s)")
                                    }
                                },
                                onBatchPermanentDelete = {
                                    if (selectedIds.isEmpty()) return@BatchSelectionToolbar
                                    scope.launch {
                                        val targets = selectedIds.toList()
                                        targets.forEach { id ->
                                            GalleryImageDecoder.deleteD3dFramesDir(context.filesDir, id)
                                            negativeRepository.deleteNegative(id)
                                        }
                                        isSelectionMode = false
                                        selectedIds = emptySet()
                                        soundEngine.playDialTick(settings.sfxEnabled)
                                        snackbarHostState.showSnackbar("Permanently deleted ${targets.size} item(s)")
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (isLoadingSource) {
                Box(
                    modifier =
                    Modifier
                        .fillMaxSize()
                        .background(ProCcdPalette.VoidObsidian.copy(alpha = 0.72f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ProCcdPalette.HardwareSlate,
                        border = BorderStroke(1.dp, ProCcdPalette.BorderMetallic)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = ProCcdPalette.WarmAmber
                            )
                            Text(
                                text = "LOADING DARKROOM...",
                                color = ProCcdPalette.CreamInk,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = LocalProCcdFonts.current.condensed
                            )
                        }
                    }
                }
            }
        }
    }

    if (showImportDialog) {
        ImportModeDialog(
            onDismiss = { showImportDialog = false },
            onPickSinglePhoto = ::openSinglePicker,
            onPickMultiPhoto = ::openMultiPicker
        )
    }

    val multiFrames = pendingMultiShotFrames
    if (multiFrames != null) {
        MultiShotChoiceDialog(
            shotCount = multiFrames.size,
            onDismiss = { pendingMultiShotFrames = null },
            onChooseBooth = {
                pendingMultiShotFrames = null
                val recipe =
                    GalleryRecipeReducer.createImportedRecipe(
                        cameraId = "booth",
                        settings = settings
                    ).let {
                        GalleryRecipeReducer.updateFrameStyle(it, FrameBorderStyle.PHOTO_BOOTH_4STRIP)
                    }
                boothSession = BoothSession(frames = multiFrames, recipe = recipe)
            },
            onChooseD3d = {
                pendingMultiShotFrames = null
                val recipe =
                    GalleryRecipeReducer.createImportedRecipe(
                        cameraId = "d3d",
                        settings = settings,
                        mediaType = MediaType.D3D_BURST,
                        burstFrameCount = multiFrames.size
                    )
                d3dSession = D3dSession(frames = multiFrames, recipe = recipe)
            }
        )
    }
}

@Composable
private fun WorksTopHeader(
    selectedTab: GalleryFilterTab,
    isSelectionMode: Boolean,
    onBack: () -> Unit,
    onToggleSelectMode: () -> Unit,
    onSelectTab: (GalleryFilterTab) -> Unit,
    onImportPhotoClick: () -> Unit
) {
    val fonts = LocalProCcdFonts.current
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .background(ProCcdPalette.HardwareSlate)
            .border(1.dp, ProCcdPalette.BorderMetallic)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = ProCcdPalette.CreamInk
                    )
                }
                Column {
                    Text(
                        text = "AAYNA WORKS",
                        color = ProCcdPalette.CreamInk,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fonts.condensed
                    )
                    Text(
                        text = "CHARCOAL DARKROOM • STUDIO",
                        color = ProCcdPalette.TextMuted,
                        fontSize = 10.sp,
                        fontFamily = fonts.led
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(onClick = onToggleSelectMode) {
                    Icon(
                        imageVector = Icons.Rounded.Checklist,
                        contentDescription = "Multi-select",
                        tint = if (isSelectionMode) ProCcdPalette.WarmAmber else ProCcdPalette.TextSecondary
                    )
                }

                Button(
                    onClick = onImportPhotoClick,
                    colors =
                    ButtonDefaults.buttonColors(
                        containerColor = ProCcdPalette.WarmAmber,
                        contentColor = ProCcdPalette.VoidObsidian
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AddPhotoAlternate,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "IMPORT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fonts.condensed
                    )
                }
            }
        }

        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            GalleryFilterTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                val accent = if (tab == GalleryFilterTab.TRASH) ProCcdPalette.LedRed else ProCcdPalette.WarmAmber
                Box(
                    modifier =
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) ProCcdPalette.SurfaceElevated else ProCcdPalette.VoidObsidian
                        )
                        .border(
                            width = 1.dp,
                            color = if (selected) accent else ProCcdPalette.BorderMetallic,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onSelectTab(tab) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tab.label.uppercase(Locale.US),
                        color = if (selected) accent else ProCcdPalette.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fonts.condensed
                    )
                }
            }
        }
    }
}

private suspend inline fun <T> withPersistentGlEngine(
    sharedEngine: AaynaPersistentGlEngine?,
    context: Context,
    crossinline block: suspend (AaynaPersistentGlEngine) -> T
): T {
    if (sharedEngine != null && !sharedEngine.isReleased()) {
        return block(sharedEngine)
    }
    val localEngine = AaynaPersistentGlEngine(context.assets)
    return try {
        block(localEngine)
    } finally {
        localEngine.close()
    }
}
