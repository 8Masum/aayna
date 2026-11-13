package com.themasum.aayna.feature.gallery

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddPhotoAlternate
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
import com.themasum.aayna.core.gl.OffscreenGlExporter
import com.themasum.aayna.core.model.CameraCatalog
import com.themasum.aayna.core.model.CaptureRecipe
import com.themasum.aayna.core.model.DateStampFormatter
import com.themasum.aayna.core.model.ExposureMath
import com.themasum.aayna.core.model.UserSettingsSnapshot
import com.themasum.aayna.core.storage.AaynaLogger
import com.themasum.aayna.core.storage.AaynaPreferencesRepository
import com.themasum.aayna.core.storage.AaynaSoundEngine
import com.themasum.aayna.core.storage.FilmRollRepository
import com.themasum.aayna.core.storage.MediaStoreExporter
import com.themasum.aayna.core.storage.NegativeRepository
import com.themasum.aayna.core.storage.db.FilmRollEntity
import com.themasum.aayna.core.storage.db.NegativeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "GalleryStudio"

private data class StudioEditorSession(
    val sourceBitmap: Bitmap,
    val recipe: CaptureRecipe,
    val editingNegativeId: String?
)

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
    val negatives by negativeRepository.negativesFlow.collectAsStateWithLifecycle(
        initialValue = emptyList()
    )
    val allRolls by filmRollRepository.allRollsFlow.collectAsStateWithLifecycle(
        initialValue = emptyList()
    )

    var selectedLabMode by remember { mutableStateOf(GalleryLabMode.NEGATIVES) }
    var selectedRoll by remember { mutableStateOf<FilmRollEntity?>(null) }
    val selectedRollFrames by remember(selectedRoll?.rollId) {
        val rollId = selectedRoll?.rollId
        if (rollId != null) {
            negativeRepository.observeNegativesForRoll(rollId)
        } else {
            flowOf(emptyList())
        }
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    var editorSession by remember { mutableStateOf<StudioEditorSession?>(null) }
    var isLoadingSource by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }

    val photoPickerLauncher =
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
                        sourceBitmap = bitmap,
                        recipe = initialRecipe,
                        editingNegativeId = null
                    )
                }.onSuccess { session ->
                    editorSession = session
                    logger.logInfo(TAG, "Opened photo picker import in studio editor")
                }.onFailure { err ->
                    logger.logError(TAG, "Photo picker import failed", err)
                    snackbarHostState.showSnackbar("Failed to import photo: ${err.message ?: "Unknown error"}")
                }
                isLoadingSource = false
            }
        }

    fun launchPhotoPicker() {
        soundEngine.playDialTick(settings.sfxEnabled)
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    fun openNegativeInEditor(entity: NegativeEntity) {
        scope.launch {
            isLoadingSource = true
            runCatching {
                val bitmap =
                    withContext(Dispatchers.IO) {
                        GalleryImageDecoder.decodeFileSafely(entity.cleanImagePath)
                    } ?: error("Negative file missing on disk")
                val recipe = entity.toCaptureRecipe()
                StudioEditorSession(
                    sourceBitmap = bitmap,
                    recipe = recipe,
                    editingNegativeId = entity.recipeId
                )
            }.onSuccess { session ->
                soundEngine.playDialTick(settings.sfxEnabled)
                editorSession = session
            }.onFailure { err ->
                logger.logError(TAG, "Failed to open negative ${entity.recipeId}", err)
                snackbarHostState.showSnackbar("Could not open negative: ${err.message ?: "Missing file"}")
            }
            isLoadingSource = false
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize().background(ProCcdPalette.BgCharcoal).systemBarsPadding(),
        containerColor = ProCcdPalette.BgCharcoal,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = ProCcdPalette.SurfaceElevated,
                    contentColor = ProCcdPalette.TextPrimary,
                    actionColor = ProCcdPalette.LedAmber
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
            val currentSession = editorSession
            if (currentSession != null) {
                GalleryStudioEditor(
                    sourceBitmap = currentSession.sourceBitmap,
                    recipe = currentSession.recipe,
                    isSavedNegative = currentSession.editingNegativeId != null,
                    isExporting = isExporting,
                    hapticsEnabled = settings.hapticsEnabled,
                    offscreenExporter = offscreenExporter,
                    onRecipeChange = { updated ->
                        editorSession = currentSession.copy(recipe = updated)
                    },
                    onDialTick = { soundEngine.playDialTick(settings.sfxEnabled) },
                    onUpdateNegative = {
                        val negativeId = currentSession.editingNegativeId ?: return@GalleryStudioEditor
                        scope.launch {
                            runCatching {
                                negativeRepository.updateRecipe(currentSession.recipe)
                            }.onSuccess {
                                soundEngine.playFilmWind(settings.sfxEnabled)
                                if (settings.hapticsEnabled) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                logger.logInfo(TAG, "Updated negative recipe $negativeId")
                                snackbarHostState.showSnackbar("Negative recipe updated")
                            }.onFailure { err ->
                                logger.logError(TAG, "Failed to update negative $negativeId", err)
                                snackbarHostState.showSnackbar("Failed to update negative")
                            }
                        }
                    },
                    onExportToGallery = {
                        if (isExporting) return@GalleryStudioEditor
                        scope.launch {
                            isExporting = true
                            val recipe = currentSession.recipe
                            val profile = CameraCatalog.findByIdOrDefault(recipe.cameraId)
                            val stampText =
                                DateStampFormatter.format(
                                    epochMillis = recipe.capturedAtEpochMs,
                                    format = recipe.stampConfig.format,
                                    useEraYearOffset = recipe.stampConfig.useEraYearOffset,
                                    eraYear = profile.eraYearOffset,
                                    customText = recipe.stampConfig.customText
                                )
                            val fallbackGains =
                                ExposureMath.computeShaderFallbackGains(
                                    hasManualSensor = false,
                                    ev = recipe.opticalSettings.evCompensation,
                                    iso = recipe.opticalSettings.iso,
                                    shutterNs = recipe.opticalSettings.shutterSpeedNs
                                )
                            runCatching {
                                val fullBitmap =
                                    offscreenExporter.renderBitmap(
                                        sourceBitmap = currentSession.sourceBitmap,
                                        profile = profile,
                                        recipe = recipe,
                                        stampText = stampText,
                                        fallbackGains = fallbackGains,
                                        maxEdgeOverride = null,
                                        splitCompareRatio = 0f,
                                        isHoldOriginal = false
                                    )
                                val exportedUri =
                                    try {
                                        mediaStoreExporter.saveJpegToGallery(
                                            bitmap = fullBitmap,
                                            profile = profile,
                                            recipe = recipe
                                        )
                                    } finally {
                                        fullBitmap.recycle()
                                    }
                                val negId = currentSession.editingNegativeId
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
                            isExporting = false
                        }
                    },
                    onCloseEditor = {
                        editorSession = null
                    }
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    StudioTopHeader(
                        selectedMode = selectedLabMode,
                        onBack = onBack,
                        onSelectMode = { mode ->
                            soundEngine.playDialTick(settings.sfxEnabled)
                            selectedLabMode = mode
                            if (mode != GalleryLabMode.FILM_ROLLS) {
                                selectedRoll = null
                            }
                        },
                        onImportPhotoClick = ::launchPhotoPicker
                    )

                    when (selectedLabMode) {
                        GalleryLabMode.NEGATIVES ->
                            NegativesLabGrid(
                                negatives = negatives,
                                onOpenNegative = ::openNegativeInEditor,
                                onDeleteNegative = { entity ->
                                    scope.launch {
                                        runCatching {
                                            negativeRepository.deleteNegative(entity.recipeId)
                                        }.onSuccess {
                                            soundEngine.playDialTick(settings.sfxEnabled)
                                            snackbarHostState.showSnackbar("Negative deleted")
                                        }.onFailure { err ->
                                            logger.logError(TAG, "Delete negative failed", err)
                                            snackbarHostState.showSnackbar("Failed to delete negative")
                                        }
                                    }
                                },
                                onImportPhotoClick = ::launchPhotoPicker,
                                modifier = Modifier.weight(1f)
                            )
                        GalleryLabMode.FILM_ROLLS ->
                            FilmRollsView(
                                rolls = allRolls,
                                selectedRoll = selectedRoll,
                                selectedRollFrames = selectedRollFrames,
                                onSelectRoll = { roll ->
                                    soundEngine.playDialTick(settings.sfxEnabled)
                                    selectedRoll = roll
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
                                onOpenFrame = ::openNegativeInEditor,
                                onDeleteFrame = { entity ->
                                    scope.launch {
                                        runCatching {
                                            negativeRepository.deleteNegative(entity.recipeId)
                                        }.onSuccess {
                                            soundEngine.playDialTick(settings.sfxEnabled)
                                            snackbarHostState.showSnackbar("Frame deleted")
                                        }
                                    }
                                },
                                onImportPhotoClick = ::launchPhotoPicker,
                                modifier = Modifier.weight(1f)
                            )
                    }
                }
            }

            if (isLoadingSource) {
                Box(
                    modifier =
                    Modifier
                        .fillMaxSize()
                        .background(ProCcdPalette.BgCharcoal.copy(alpha = 0.72f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ProCcdPalette.SurfaceDark,
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
                                color = ProCcdPalette.LedAmber
                            )
                            Text(
                                text = "LOADING DARKROOM...",
                                color = ProCcdPalette.TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioTopHeader(
    selectedMode: GalleryLabMode,
    onBack: () -> Unit,
    onSelectMode: (GalleryLabMode) -> Unit,
    onImportPhotoClick: () -> Unit
) {
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .background(ProCcdPalette.SurfaceDark)
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
                        tint = ProCcdPalette.TextPrimary
                    )
                }
                Column {
                    Text(
                        text = "AAYNA DARKROOM",
                        color = ProCcdPalette.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "NON-DESTRUCTIVE GPU STUDIO",
                        color = ProCcdPalette.TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Button(
                onClick = onImportPhotoClick,
                colors =
                ButtonDefaults.buttonColors(
                    containerColor = ProCcdPalette.LedAmber,
                    contentColor = ProCcdPalette.BgCharcoal
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
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GalleryLabMode.entries.forEach { mode ->
                val selected = mode == selectedMode
                Box(
                    modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) ProCcdPalette.SurfaceElevated else ProCcdPalette.BgCharcoal
                        )
                        .border(
                            width = 1.dp,
                            color = if (selected) ProCcdPalette.LedCyan else ProCcdPalette.BorderMetallic,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onSelectMode(mode) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.label,
                        color = if (selected) ProCcdPalette.LedCyan else ProCcdPalette.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
