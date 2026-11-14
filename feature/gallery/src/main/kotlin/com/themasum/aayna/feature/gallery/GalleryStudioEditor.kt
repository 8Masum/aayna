package com.themasum.aayna.feature.gallery

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.themasum.aayna.core.gl.GlRenderParams
import com.themasum.aayna.core.gl.OffscreenGlExporter
import com.themasum.aayna.core.gl.StudioGlPreviewRenderer
import com.themasum.aayna.core.model.CameraCatalog
import com.themasum.aayna.core.model.CameraProfile
import com.themasum.aayna.core.model.CaptureRecipe
import com.themasum.aayna.core.model.DateStampFormatter
import com.themasum.aayna.core.model.FlashMode
import com.themasum.aayna.core.model.FrameBorderStyle
import com.themasum.aayna.core.model.LensAttachment
import com.themasum.aayna.core.model.ResolutionPreset
import com.themasum.aayna.core.model.StampFontStyle
import com.themasum.aayna.core.model.StampFormat
import com.themasum.aayna.core.model.StampPosition
import com.themasum.aayna.core.model.StampStyle
import com.themasum.aayna.core.model.WhiteBalancePreset
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private const val PREVIEW_MAX_EDGE_PX = 1280
private const val PREVIEW_DEBOUNCE_MS = 70L

@Composable
internal fun GalleryStudioEditor(
    sourceBitmap: Bitmap,
    recipe: CaptureRecipe,
    isSavedNegative: Boolean,
    isExporting: Boolean,
    hapticsEnabled: Boolean,
    offscreenExporter: OffscreenGlExporter,
    studioGlRenderer: StudioGlPreviewRenderer?,
    favoriteCameraIds: Set<String>,
    selectedCropPreset: StudioCropPreset,
    grainBoost: Float,
    onGrainBoostChange: (Float) -> Unit,
    onSelectCropPreset: (StudioCropPreset) -> Unit,
    onRotate90Cw: () -> Unit,
    onToggleFavoriteCamera: (String) -> Unit,
    onRecipeChange: (CaptureRecipe) -> Unit,
    onDialTick: () -> Unit,
    onSaveEdits: () -> Unit,
    onExportToGallery: () -> Unit,
    onCloseEditor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val activeProfile =
        remember(recipe.cameraId) { CameraCatalog.findByIdOrDefault(recipe.cameraId) }

    var selectedTab by remember { mutableStateOf(StudioEditorTab.CAMERA) }
    var splitCompareRatio by remember { mutableFloatStateOf(0f) }
    var isHoldOriginal by remember { mutableStateOf(false) }
    var showSplitBar by remember { mutableStateOf(false) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isRenderingPreview by remember { mutableStateOf(false) }
    var previewError by remember { mutableStateOf<String?>(null) }

    val formattedStamp =
        remember(recipe.capturedAtEpochMs, recipe.stampConfig, activeProfile.eraYearOffset) {
            DateStampFormatter.format(
                epochMillis = recipe.capturedAtEpochMs,
                format = recipe.stampConfig.format,
                useEraYearOffset = recipe.stampConfig.useEraYearOffset,
                eraYear = activeProfile.eraYearOffset,
                customText = recipe.stampConfig.customText
            )
        }

    val inputAspect =
        remember(sourceBitmap) {
            sourceBitmap.width.toFloat() / sourceBitmap.height.coerceAtLeast(1).toFloat()
        }

    val renderParams =
        remember(
            recipe,
            activeProfile,
            formattedStamp,
            grainBoost,
            splitCompareRatio,
            isHoldOriginal,
            inputAspect
        ) {
            GalleryRecipeReducer.buildGlRenderParams(
                recipe = recipe,
                profile = activeProfile,
                stampText = formattedStamp,
                grainBoost = grainBoost,
                splitCompareRatio = splitCompareRatio,
                isHoldOriginal = isHoldOriginal,
                inputAspectRatio = inputAspect,
                skipStamp = isHoldOriginal
            )
        }

    if (studioGlRenderer != null) {
        LaunchedEffect(studioGlRenderer, sourceBitmap) {
            studioGlRenderer.setSourceBitmap(sourceBitmap, recycleUploadedCopy = false)
        }
        LaunchedEffect(studioGlRenderer, renderParams) {
            studioGlRenderer.requestRender(renderParams)
        }
        DisposableEffect(studioGlRenderer) {
            onDispose {
                studioGlRenderer.detachSurface()
                studioGlRenderer.clearSourceBitmap()
            }
        }
    } else {
        LaunchedEffect(sourceBitmap, renderParams) {
            delay(PREVIEW_DEBOUNCE_MS)
            isRenderingPreview = true
            previewError = null
            runCatching {
                offscreenExporter.renderBitmap(
                    sourceBitmap = sourceBitmap,
                    profile = activeProfile,
                    recipe = recipe,
                    stampText = formattedStamp,
                    fallbackGains = renderParams.fallbackGains,
                    maxEdgeOverride = PREVIEW_MAX_EDGE_PX,
                    splitCompareRatio = splitCompareRatio,
                    isHoldOriginal = isHoldOriginal,
                    skipStamp = isHoldOriginal
                )
            }.onSuccess { rendered ->
                previewBitmap = rendered
                isRenderingPreview = false
            }.onFailure { err ->
                previewError = err.message ?: "Preview render failed"
                isRenderingPreview = false
            }
        }
    }

    Column(
        modifier =
        modifier
            .fillMaxSize()
            .background(ProCcdPalette.VoidObsidian)
    ) {
        EditorTopBar(
            cameraCodeName = activeProfile.codeName,
            isSavedNegative = isSavedNegative,
            isExporting = isExporting,
            showSplitBar = showSplitBar,
            isGlRealtime = studioGlRenderer != null,
            onToggleSplitBar = {
                showSplitBar = !showSplitBar
                if (!showSplitBar) {
                    splitCompareRatio = 0f
                } else if (splitCompareRatio == 0f) {
                    splitCompareRatio = 0.5f
                }
            },
            onSaveEdits = onSaveEdits,
            onExportToGallery = onExportToGallery,
            onCloseEditor = onCloseEditor
        )

        EditorPreviewViewport(
            studioGlRenderer = studioGlRenderer,
            renderParams = renderParams,
            previewBitmap = previewBitmap,
            fallbackBitmap = sourceBitmap,
            isRenderingPreview = isRenderingPreview,
            isExporting = isExporting,
            isHoldOriginal = isHoldOriginal,
            showSplitBar = showSplitBar,
            splitCompareRatio = splitCompareRatio,
            previewError = previewError,
            onSplitRatioChange = { splitCompareRatio = it },
            onHoldChange = { held -> isHoldOriginal = held },
            modifier =
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )

        CompareActionStrip(
            showSplitBar = showSplitBar,
            splitCompareRatio = splitCompareRatio,
            isHoldOriginal = isHoldOriginal,
            onSplitRatioChange = { splitCompareRatio = it },
            onResetSplit = {
                showSplitBar = false
                splitCompareRatio = 0f
            },
            onHoldChange = { isHoldOriginal = it }
        )

        EditorTabStrip(
            selectedTab = selectedTab,
            onSelectTab = { tab ->
                selectedTab = tab
                onDialTick()
                if (hapticsEnabled) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
        )

        Box(
            modifier =
            Modifier
                .fillMaxWidth()
                .height(252.dp)
                .background(ProCcdPalette.HardwareSlate)
                .border(width = 1.dp, color = ProCcdPalette.BorderMetallic)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            when (selectedTab) {
                StudioEditorTab.CAMERA ->
                    CameraSwapDeck(
                        recipe = recipe,
                        favoriteCameraIds = favoriteCameraIds,
                        onToggleFavorite = onToggleFavoriteCamera,
                        onSelectProfile = { profile ->
                            onDialTick()
                            if (hapticsEnabled) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            onRecipeChange(GalleryRecipeReducer.swapCameraProfile(recipe, profile))
                        },
                        onResetDefaults = {
                            onDialTick()
                            onRecipeChange(
                                GalleryRecipeReducer.swapCameraProfile(
                                    recipe = recipe,
                                    newProfile = activeProfile,
                                    applyProfileDefaults = true
                                )
                            )
                        }
                    )
                StudioEditorTab.ADJUST ->
                    AdjustControlDeck(
                        recipe = recipe,
                        hapticsEnabled = hapticsEnabled,
                        onDialTick = onDialTick,
                        onRecipeChange = onRecipeChange
                    )
                StudioEditorTab.FILM_FX ->
                    FilmFxControlDeck(
                        recipe = recipe,
                        grainBoost = grainBoost,
                        onGrainBoostChange = onGrainBoostChange,
                        onRecipeChange = onRecipeChange,
                        onShuffleSeed = {
                            onDialTick()
                            if (hapticsEnabled) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            onRecipeChange(GalleryRecipeReducer.shuffleRandomSeed(recipe))
                        },
                        onToggleFlash = {
                            onDialTick()
                            onRecipeChange(GalleryRecipeReducer.toggleFlashSim(recipe))
                        }
                    )
                StudioEditorTab.FRAME_LENS ->
                    FrameAndLensDeck(
                        recipe = recipe,
                        onRecipeChange = onRecipeChange,
                        onDialTick = onDialTick
                    )
                StudioEditorTab.DATE_STAMP ->
                    DateStampDeck(
                        recipe = recipe,
                        formattedStamp = formattedStamp,
                        onRecipeChange = onRecipeChange,
                        onDialTick = onDialTick
                    )
                StudioEditorTab.CROP_ROTATE ->
                    CropAndRotateDeck(
                        recipe = recipe,
                        selectedCropPreset = selectedCropPreset,
                        hapticsEnabled = hapticsEnabled,
                        onSelectCropPreset = { preset ->
                            onDialTick()
                            onSelectCropPreset(preset)
                        },
                        onRotate90Cw = {
                            onDialTick()
                            onRotate90Cw()
                        },
                        onDialTick = onDialTick,
                        onRecipeChange = onRecipeChange
                    )
            }
        }
    }
}

@Composable
private fun EditorTopBar(
    cameraCodeName: String,
    isSavedNegative: Boolean,
    isExporting: Boolean,
    showSplitBar: Boolean,
    isGlRealtime: Boolean,
    onToggleSplitBar: () -> Unit,
    onSaveEdits: () -> Unit,
    onExportToGallery: () -> Unit,
    onCloseEditor: () -> Unit
) {
    val fonts = LocalProCcdFonts.current
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .background(ProCcdPalette.HardwareSlate)
            .border(1.dp, ProCcdPalette.BorderMetallic)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCloseEditor) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back to Gallery",
                    tint = ProCcdPalette.CreamInk
                )
            }
            LedStatusPill(label = cameraCodeName, accentColor = ProCcdPalette.LedCyan)
            if (isGlRealtime) {
                Spacer(modifier = Modifier.width(6.dp))
                LedStatusPill(label = "GL LIVE", accentColor = ProCcdPalette.LedEmerald)
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onToggleSplitBar) {
                Icon(
                    imageVector = Icons.Rounded.Compare,
                    contentDescription = "Split Compare",
                    tint = if (showSplitBar) ProCcdPalette.WarmAmber else ProCcdPalette.TextSecondary
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onSaveEdits,
                enabled = !isExporting,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                border = BorderStroke(1.dp, ProCcdPalette.BorderHighlight),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Save,
                    contentDescription = null,
                    tint = ProCcdPalette.LedCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isSavedNegative) "SAVE" else "KEEP",
                    color = ProCcdPalette.CreamInk,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fonts.condensed
                )
            }

            Button(
                onClick = onExportToGallery,
                enabled = !isExporting,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                colors =
                ButtonDefaults.buttonColors(
                    containerColor = ProCcdPalette.WarmAmber,
                    contentColor = ProCcdPalette.VoidObsidian
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isExporting) "EXPORTING" else "EXPORT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fonts.condensed
                )
            }
        }
    }
}

@Composable
private fun EditorPreviewViewport(
    studioGlRenderer: StudioGlPreviewRenderer?,
    renderParams: GlRenderParams,
    previewBitmap: Bitmap?,
    fallbackBitmap: Bitmap,
    isRenderingPreview: Boolean,
    isExporting: Boolean,
    isHoldOriginal: Boolean,
    showSplitBar: Boolean,
    splitCompareRatio: Float,
    previewError: String?,
    onSplitRatioChange: (Float) -> Unit,
    onHoldChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var attachedSurface by remember { mutableStateOf<Surface?>(null) }
    val latestParams by rememberUpdatedState(renderParams)
    val latestShowSplit by rememberUpdatedState(showSplitBar)
    val latestSplitRatio by rememberUpdatedState(splitCompareRatio)
    val fonts = LocalProCcdFonts.current

    BoxWithConstraints(
        modifier =
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(ProCcdPalette.SurfaceRecessed)
            .border(1.dp, ProCcdPalette.BorderMetallic, RoundedCornerShape(10.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        zoomScale = 1f
                        panOffset = Offset.Zero
                    },
                    onPress = {
                        onHoldChange(true)
                        tryAwaitRelease()
                        onHoldChange(false)
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val nextScale = (zoomScale * zoom).coerceIn(1f, 5f)
                    zoomScale = nextScale
                    if (nextScale <= 1.01f) {
                        zoomScale = 1f
                        panOffset = Offset.Zero
                    } else {
                        val maxX = (size.width * (nextScale - 1f)) * 0.5f
                        val maxY = (size.height * (nextScale - 1f)) * 0.5f
                        panOffset =
                            Offset(
                                x = (panOffset.x + pan.x).coerceIn(-maxX, maxX),
                                y = (panOffset.y + pan.y).coerceIn(-maxY, maxY)
                            )
                    }
                }
            }
            .pointerInput(showSplitBar) {
                if (showSplitBar) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        if (zoomScale <= 1.02f) {
                            change.consume()
                            val width = size.width.coerceAtLeast(1).toFloat()
                            val base = if (latestSplitRatio <= 0f) 0.5f else latestSplitRatio
                            val next = (base + dragAmount / width).coerceIn(0.02f, 0.98f)
                            onSplitRatioChange(next)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val transformModifier =
            Modifier
                .fillMaxSize()
                .padding(6.dp)
                .graphicsLayer {
                    scaleX = zoomScale
                    scaleY = zoomScale
                    translationX = panOffset.x
                    translationY = panOffset.y
                }

        if (studioGlRenderer != null) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        isOpaque = true
                        surfaceTextureListener =
                            object : TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(
                                    surfaceTexture: SurfaceTexture,
                                    width: Int,
                                    height: Int
                                ) {
                                    attachedSurface?.release()
                                    val surface = Surface(surfaceTexture)
                                    attachedSurface = surface
                                    studioGlRenderer.attachSurface(surface, width, height)
                                    studioGlRenderer.requestRender(latestParams)
                                }

                                override fun onSurfaceTextureSizeChanged(
                                    surfaceTexture: SurfaceTexture,
                                    width: Int,
                                    height: Int
                                ) {
                                    studioGlRenderer.resizeSurface(width, height)
                                }

                                override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                                    studioGlRenderer.detachSurface()
                                    attachedSurface?.release()
                                    attachedSurface = null
                                    return true
                                }

                                override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
                            }
                    }
                },
                modifier = transformModifier
            )
        } else {
            val activeBitmap = previewBitmap ?: fallbackBitmap
            Image(
                bitmap = activeBitmap.asImageBitmap(),
                contentDescription = "Studio Preview",
                contentScale = ContentScale.Fit,
                modifier = transformModifier
            )
        }

        if (latestShowSplit && splitCompareRatio > 0f && !isHoldOriginal) {
            val clampedRatio = splitCompareRatio.coerceIn(0.02f, 0.98f)
            val viewportConstraints = constraints
            val dividerOffsetX = (viewportConstraints.maxWidth * clampedRatio).roundToInt()
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier =
                    Modifier
                        .offset { IntOffset(dividerOffsetX, 0) }
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(ProCcdPalette.WarmAmber)
                )
                Box(
                    modifier =
                    Modifier
                        .offset { IntOffset(dividerOffsetX - 11, viewportConstraints.maxHeight / 2 - 11) }
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(ProCcdPalette.WarmAmber)
                        .border(1.5.dp, ProCcdPalette.VoidObsidian, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "<>",
                        color = ProCcdPalette.VoidObsidian,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = fonts.led
                    )
                }
            }
        }

        Row(
            modifier =
            Modifier
                .align(Alignment.TopStart)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isHoldOriginal) {
                LedStatusPill(label = "CLEAN NEGATIVE (NO STAMP)", accentColor = ProCcdPalette.LedRed)
            } else if (splitCompareRatio > 0f) {
                val pct = (splitCompareRatio * 100f).roundToInt()
                LedStatusPill(label = "SPLIT $pct%", accentColor = ProCcdPalette.WarmAmber)
            } else {
                LedStatusPill(
                    label = if (zoomScale > 1.02f) {
                        String.format(Locale.US, "%.1fx ZOOM • DBL-TAP RESET", zoomScale)
                    } else {
                        "PINCH ZOOM • HOLD CLEAN NEG"
                    },
                    accentColor = ProCcdPalette.TextSecondary,
                    filledDot = false
                )
            }
        }

        if (isRenderingPreview || isExporting) {
            Surface(
                modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp),
                shape = RoundedCornerShape(8.dp),
                color = ProCcdPalette.VoidObsidian.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, ProCcdPalette.BorderMetallic)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = ProCcdPalette.WarmAmber
                    )
                    Text(
                        text = if (isExporting) "EXPORTING FULL RES" else "GPU RENDER",
                        color = ProCcdPalette.CreamInk,
                        fontSize = 10.sp,
                        fontFamily = fonts.led
                    )
                }
            }
        }

        if (previewError != null) {
            Surface(
                modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp),
                shape = RoundedCornerShape(6.dp),
                color = ProCcdPalette.LedRed.copy(alpha = 0.2f),
                border = BorderStroke(1.dp, ProCcdPalette.LedRed)
            ) {
                Text(
                    text = previewError,
                    color = ProCcdPalette.LedRed,
                    fontSize = 10.sp,
                    fontFamily = fonts.led,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun CompareActionStrip(
    showSplitBar: Boolean,
    splitCompareRatio: Float,
    isHoldOriginal: Boolean,
    onSplitRatioChange: (Float) -> Unit,
    onResetSplit: () -> Unit,
    onHoldChange: (Boolean) -> Unit
) {
    val fonts = LocalProCcdFonts.current
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .background(ProCcdPalette.SurfaceElevated)
            .border(1.dp, ProCcdPalette.BorderMetallic)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(7.dp),
            color = if (isHoldOriginal) ProCcdPalette.LedRed.copy(alpha = 0.22f) else ProCcdPalette.HardwareSlate,
            border =
            BorderStroke(
                1.dp,
                if (isHoldOriginal) ProCcdPalette.LedRed else ProCcdPalette.BorderHighlight
            ),
            modifier =
            Modifier.pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onHoldChange(true)
                        tryAwaitRelease()
                        onHoldChange(false)
                    }
                )
            }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Visibility,
                    contentDescription = null,
                    tint = if (isHoldOriginal) ProCcdPalette.LedRed else ProCcdPalette.WarmAmber,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "HOLD FOR CLEAN NEGATIVE",
                    color = if (isHoldOriginal) ProCcdPalette.LedRed else ProCcdPalette.CreamInk,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fonts.condensed
                )
            }
        }

        if (showSplitBar) {
            Slider(
                value = splitCompareRatio,
                onValueChange = onSplitRatioChange,
                valueRange = 0f..1f,
                colors =
                SliderDefaults.colors(
                    thumbColor = ProCcdPalette.WarmAmber,
                    activeTrackColor = ProCcdPalette.WarmAmber,
                    inactiveTrackColor = ProCcdPalette.BorderMetallic
                ),
                modifier = Modifier.weight(1f).height(26.dp)
            )
            HardwareChip(
                label = "OFF",
                selected = false,
                onClick = onResetSplit
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "SWIPE COMPARE VIA TOP ICON",
                color = ProCcdPalette.TextMuted,
                fontSize = 9.sp,
                fontFamily = fonts.led
            )
        }
    }
}

@Composable
private fun EditorTabStrip(selectedTab: StudioEditorTab, onSelectTab: (StudioEditorTab) -> Unit) {
    val fonts = LocalProCcdFonts.current
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .background(ProCcdPalette.VoidObsidian)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        StudioEditorTab.entries.forEach { tab ->
            val selected = tab == selectedTab
            Box(
                modifier =
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selected) ProCcdPalette.SurfaceElevated else ProCcdPalette.HardwareSlate
                    )
                    .border(
                        width = 1.dp,
                        color = if (selected) ProCcdPalette.WarmAmber else ProCcdPalette.BorderMetallic,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelectTab(tab) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tab.label.uppercase(Locale.US),
                    color = if (selected) ProCcdPalette.WarmAmber else ProCcdPalette.TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fonts.condensed,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun CameraSwapDeck(
    recipe: CaptureRecipe,
    favoriteCameraIds: Set<String>,
    onToggleFavorite: (String) -> Unit,
    onSelectProfile: (CameraProfile) -> Unit,
    onResetDefaults: () -> Unit
) {
    val fonts = LocalProCcdFonts.current
    var selectedGroup by remember { mutableStateOf(CameraShelfGroup.POPULAR) }
    val groupCameras =
        remember(selectedGroup, favoriteCameraIds) {
            GalleryCameraShelfHelper.camerasForGroup(selectedGroup, favoriteCameraIds)
        }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CameraShelfGroup.entries.forEach { group ->
                    val count =
                        remember(group, favoriteCameraIds) {
                            GalleryCameraShelfHelper.camerasForGroup(group, favoriteCameraIds).size
                        }
                    HardwareChip(
                        label = "${group.label} ($count)",
                        selected = selectedGroup == group,
                        onClick = { selectedGroup = group },
                        accentColor = ProCcdPalette.WarmAmber
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            HardwareChip(
                label = "RESET",
                selected = false,
                onClick = onResetDefaults,
                accentColor = ProCcdPalette.LedCyan
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 6.dp)
        ) {
            items(groupCameras, key = { it.id }) { profile ->
                val selected = profile.id == recipe.cameraId
                val isFav = favoriteCameraIds.contains(profile.id)
                val accent = parseHexColorOrDefault(profile.skin.accentHex, ProCcdPalette.WarmAmber)
                val bodyPrimary = parseHexColorOrDefault(profile.skin.bodyPrimaryHex, ProCcdPalette.SurfaceElevated)
                Surface(
                    modifier =
                    Modifier
                        .width(134.dp)
                        .clickable { onSelectProfile(profile) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) ProCcdPalette.SurfaceElevated else ProCcdPalette.VoidObsidian,
                    border =
                    BorderStroke(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) ProCcdPalette.WarmAmber else ProCcdPalette.BorderMetallic
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier =
                                    Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(bodyPrimary)
                                        .border(1.5.dp, accent, CircleShape)
                                )
                                Text(
                                    text = profile.eraYearOffset.toString(),
                                    color = ProCcdPalette.TextMuted,
                                    fontSize = 10.sp,
                                    fontFamily = fonts.led
                                )
                            }
                            Icon(
                                imageVector = if (isFav) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                                contentDescription = "Favorite",
                                tint = if (isFav) ProCcdPalette.WarmAmber else ProCcdPalette.TextMuted,
                                modifier =
                                Modifier
                                    .size(16.dp)
                                    .clickable { onToggleFavorite(profile.id) }
                            )
                        }
                        Text(
                            text = profile.codeName,
                            color = if (selected) ProCcdPalette.WarmAmber else ProCcdPalette.CreamInk,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = fonts.condensed,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = profile.subtitle,
                            color = ProCcdPalette.TextSecondary,
                            fontSize = 10.sp,
                            maxLines = 2,
                            lineHeight = 13.sp,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdjustControlDeck(
    recipe: CaptureRecipe,
    hapticsEnabled: Boolean,
    onDialTick: () -> Unit,
    onRecipeChange: (CaptureRecipe) -> Unit
) {
    val optics = recipe.opticalSettings
    var activeParam by remember { mutableStateOf(AdjustParameter.EV) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AdjustParameter.entries.forEach { param ->
                HardwareChip(
                    label = param.label,
                    selected = activeParam == param,
                    onClick = {
                        onDialTick()
                        activeParam = param
                    }
                )
            }
        }

        when (activeParam) {
            AdjustParameter.EV ->
                NotchedRulerDial(
                    label = "EXPOSURE COMPENSATION (EV)",
                    valueText = String.format(Locale.US, "%+.2f EV", optics.evCompensation),
                    value = optics.evCompensation,
                    valueRange = GalleryRecipeReducer.MIN_EV..GalleryRecipeReducer.MAX_EV,
                    stepSize = 0.1f,
                    resetValue = 0f,
                    hapticsEnabled = hapticsEnabled,
                    onTick = onDialTick,
                    onValueChange = { onRecipeChange(GalleryRecipeReducer.updateEv(recipe, it)) }
                )
            AdjustParameter.HIGHLIGHTS ->
                NotchedRulerDial(
                    label = "HIGHLIGHTS",
                    valueText = "${(optics.highlights * 100f).roundToInt()}",
                    value = optics.highlights,
                    valueRange = GalleryRecipeReducer.MIN_TONAL..GalleryRecipeReducer.MAX_TONAL,
                    stepSize = 0.05f,
                    resetValue = 0f,
                    hapticsEnabled = hapticsEnabled,
                    onTick = onDialTick,
                    onValueChange = { onRecipeChange(GalleryRecipeReducer.updateHighlights(recipe, it)) }
                )
            AdjustParameter.SHADOWS ->
                NotchedRulerDial(
                    label = "SHADOWS",
                    valueText = "${(optics.shadows * 100f).roundToInt()}",
                    value = optics.shadows,
                    valueRange = GalleryRecipeReducer.MIN_TONAL..GalleryRecipeReducer.MAX_TONAL,
                    stepSize = 0.05f,
                    resetValue = 0f,
                    hapticsEnabled = hapticsEnabled,
                    onTick = onDialTick,
                    onValueChange = { onRecipeChange(GalleryRecipeReducer.updateShadows(recipe, it)) }
                )
            AdjustParameter.SHARPEN ->
                NotchedRulerDial(
                    label = "CONTRAST / SHARPEN",
                    valueText = "${(optics.sharpen * 100f).roundToInt()}%",
                    value = optics.sharpen,
                    valueRange = 0f..1f,
                    stepSize = 0.05f,
                    resetValue = 0f,
                    hapticsEnabled = hapticsEnabled,
                    onTick = onDialTick,
                    onValueChange = { onRecipeChange(GalleryRecipeReducer.updateSharpen(recipe, it)) }
                )
            AdjustParameter.WARMTH ->
                NotchedRulerDial(
                    label = "WARMTH",
                    valueText = String.format(Locale.US, "%+.2f", optics.warmthShift),
                    value = optics.warmthShift,
                    valueRange = GalleryRecipeReducer.MIN_WARMTH..GalleryRecipeReducer.MAX_WARMTH,
                    stepSize = 0.05f,
                    resetValue = 0f,
                    hapticsEnabled = hapticsEnabled,
                    accentColor = ProCcdPalette.LedCyan,
                    onTick = onDialTick,
                    onValueChange = { onRecipeChange(GalleryRecipeReducer.updateWarmth(recipe, it)) }
                )
            AdjustParameter.BEAUTY ->
                NotchedRulerDial(
                    label = "BEAUTY SOFTNESS",
                    valueText = "${(optics.beautySoftness * 100f).roundToInt()}%",
                    value = optics.beautySoftness,
                    valueRange = 0f..1f,
                    stepSize = 0.05f,
                    resetValue = 0f,
                    hapticsEnabled = hapticsEnabled,
                    accentColor = ProCcdPalette.LedEmerald,
                    onTick = onDialTick,
                    onValueChange = { onRecipeChange(GalleryRecipeReducer.updateBeautySoftness(recipe, it)) }
                )
        }

        HardwareSectionLabel(text = "WHITE BALANCE PRESET")
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            WhiteBalancePreset.entries.forEach { wb ->
                HardwareChip(
                    label = wb.label,
                    selected = optics.whiteBalance == wb,
                    onClick = {
                        onDialTick()
                        onRecipeChange(GalleryRecipeReducer.updateWhiteBalance(recipe, wb))
                    },
                    accentColor = ProCcdPalette.LedCyan
                )
            }
        }
    }
}

@Composable
private fun FilmFxControlDeck(
    recipe: CaptureRecipe,
    grainBoost: Float,
    onGrainBoostChange: (Float) -> Unit,
    onRecipeChange: (CaptureRecipe) -> Unit,
    onShuffleSeed: () -> Unit,
    onToggleFlash: () -> Unit
) {
    val optics = recipe.opticalSettings
    val flashSimOn = optics.flashMode == FlashMode.OPTICAL_FLASH_SIM
    val fonts = LocalProCcdFonts.current

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HardwareSliderRow(
            label = "BLOOM / HALATION",
            valueText = "${(optics.bloomIntensity * 100f).roundToInt()}%",
            value = optics.bloomIntensity,
            valueRange = 0f..1f,
            onValueChange = { onRecipeChange(GalleryRecipeReducer.updateBloom(recipe, it)) }
        )
        HardwareSliderRow(
            label = "FILM GRAIN BOOST",
            valueText = "${(grainBoost * 100f).roundToInt()}%",
            value = grainBoost,
            valueRange = 0f..1f,
            onValueChange = onGrainBoostChange,
            accentColor = ProCcdPalette.LedEmerald
        )
        HardwareSliderRow(
            label = "LIGHT LEAKS",
            valueText = "${(optics.lightLeakIntensity * 100f).roundToInt()}%",
            value = optics.lightLeakIntensity,
            valueRange = 0f..1f,
            onValueChange = { onRecipeChange(GalleryRecipeReducer.updateLightLeak(recipe, it)) }
        )
        HardwareSliderRow(
            label = "DUST & SCRATCHES",
            valueText = "${(optics.dustIntensity * 100f).roundToInt()}%",
            value = optics.dustIntensity,
            valueRange = 0f..1f,
            onValueChange = { onRecipeChange(GalleryRecipeReducer.updateDust(recipe, it)) },
            accentColor = ProCcdPalette.LedCyan
        )
        HardwareSliderRow(
            label = "VIGNETTE",
            valueText = "${(optics.vignetteIntensity * 100f).roundToInt()}%",
            value = optics.vignetteIntensity,
            valueRange = 0f..1f,
            onValueChange = { onRecipeChange(GalleryRecipeReducer.updateVignette(recipe, it)) }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onShuffleSeed,
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, ProCcdPalette.WarmAmber),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = null,
                    tint = ProCcdPalette.WarmAmber,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "SHUFFLE LEAK",
                    color = ProCcdPalette.WarmAmber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fonts.condensed
                )
            }

            OutlinedButton(
                onClick = onToggleFlash,
                modifier = Modifier.weight(1f),
                border =
                BorderStroke(
                    width = 1.dp,
                    color = if (flashSimOn) ProCcdPalette.LedEmerald else ProCcdPalette.BorderHighlight
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = if (flashSimOn) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                    contentDescription = null,
                    tint = if (flashSimOn) ProCcdPalette.LedEmerald else ProCcdPalette.TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (flashSimOn) "FLASH SIM: ON" else "FLASH SIM: OFF",
                    color = if (flashSimOn) ProCcdPalette.LedEmerald else ProCcdPalette.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fonts.condensed
                )
            }
        }
    }
}

@Composable
private fun FrameAndLensDeck(recipe: CaptureRecipe, onRecipeChange: (CaptureRecipe) -> Unit, onDialTick: () -> Unit) {
    val optics = recipe.opticalSettings

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HardwareSectionLabel(text = "LENS ATTACHMENTS (${LensAttachment.entries.size})")
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LensAttachment.entries.forEach { lens ->
                HardwareChip(
                    label = lensLabel(lens),
                    selected = optics.lensAttachment == lens,
                    onClick = {
                        onDialTick()
                        onRecipeChange(GalleryRecipeReducer.updateLensAttachment(recipe, lens))
                    }
                )
            }
        }

        HardwareSectionLabel(text = "FRAME BORDERS (${FrameBorderStyle.entries.size})")
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FrameBorderStyle.entries.forEach { frame ->
                HardwareChip(
                    label = frameLabel(frame),
                    selected = optics.frameStyle == frame,
                    onClick = {
                        onDialTick()
                        onRecipeChange(GalleryRecipeReducer.updateFrameStyle(recipe, frame))
                    },
                    accentColor = ProCcdPalette.LedCyan
                )
            }
        }

        HardwareSectionLabel(text = "EXPORT RESOLUTION")
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ResolutionPreset.entries.forEach { preset ->
                HardwareChip(
                    label = preset.label,
                    selected = recipe.resolutionPreset == preset,
                    onClick = {
                        onDialTick()
                        onRecipeChange(GalleryRecipeReducer.updateResolutionPreset(recipe, preset))
                    },
                    accentColor = ProCcdPalette.LedEmerald
                )
            }
        }
    }
}

@Composable
private fun DateStampDeck(
    recipe: CaptureRecipe,
    formattedStamp: String,
    onRecipeChange: (CaptureRecipe) -> Unit,
    onDialTick: () -> Unit
) {
    val stamp = recipe.stampConfig
    val fonts = LocalProCcdFonts.current

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "DATE STAMP",
                    color = ProCcdPalette.CreamInk,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fonts.condensed
                )
                Text(
                    text = if (stamp.enabled) "PREVIEW: $formattedStamp" else "DISABLED",
                    color = if (stamp.enabled) ProCcdPalette.WarmAmber else ProCcdPalette.TextMuted,
                    fontSize = 10.sp,
                    fontFamily = stampFontFamilyFor(stamp.fontStyle, fonts)
                )
            }
            Switch(
                checked = stamp.enabled,
                onCheckedChange = {
                    onDialTick()
                    onRecipeChange(GalleryRecipeReducer.updateStampEnabled(recipe, it))
                },
                colors =
                SwitchDefaults.colors(
                    checkedThumbColor = ProCcdPalette.VoidObsidian,
                    checkedTrackColor = ProCcdPalette.WarmAmber
                )
            )
        }

        if (stamp.enabled) {
            HardwareSectionLabel(text = "COLOR (${StampStyle.entries.size})")
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StampStyle.entries.forEach { style ->
                    val swatch = parseHexColorOrDefault(style.hexColor, ProCcdPalette.WarmAmber)
                    val selected = stamp.style == style
                    Box(
                        modifier =
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ProCcdPalette.VoidObsidian)
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) swatch else ProCcdPalette.BorderMetallic,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                onDialTick()
                                onRecipeChange(GalleryRecipeReducer.updateStampStyle(recipe, style))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier =
                            Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(swatch)
                        )
                    }
                }
            }

            HardwareSectionLabel(text = "FORMAT & ERA YEAR")
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StampFormat.entries.forEach { format ->
                    HardwareChip(
                        label = stampFormatLabel(format),
                        selected = stamp.format == format,
                        onClick = {
                            onDialTick()
                            onRecipeChange(GalleryRecipeReducer.updateStampFormat(recipe, format))
                        }
                    )
                }
                HardwareChip(
                    label = if (stamp.useEraYearOffset) "ERA YEAR: ON" else "ERA YEAR: OFF",
                    selected = stamp.useEraYearOffset,
                    onClick = {
                        onDialTick()
                        onRecipeChange(
                            GalleryRecipeReducer.updateStampEraYearOffset(recipe, !stamp.useEraYearOffset)
                        )
                    },
                    accentColor = ProCcdPalette.LedCyan
                )
            }

            HardwareSectionLabel(text = "FONT (${StampFontStyle.entries.size})")
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StampFontStyle.entries.forEach { fontStyle ->
                    HardwareChip(
                        label = stampFontLabel(fontStyle),
                        selected = stamp.fontStyle == fontStyle,
                        onClick = {
                            onDialTick()
                            onRecipeChange(GalleryRecipeReducer.updateStampFontStyle(recipe, fontStyle))
                        },
                        accentColor = ProCcdPalette.LedEmerald
                    )
                }
            }

            HardwareSectionLabel(text = "POSITION (${StampPosition.entries.size})")
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StampPosition.entries.forEach { pos ->
                    HardwareChip(
                        label = stampPositionLabel(pos),
                        selected = stamp.position == pos,
                        onClick = {
                            onDialTick()
                            onRecipeChange(GalleryRecipeReducer.updateStampPosition(recipe, pos))
                        }
                    )
                }
            }

            HardwareSliderRow(
                label = "STAMP GLOW",
                valueText = "${(stamp.glowStrength * 100f).roundToInt()}%",
                value = stamp.glowStrength,
                valueRange = 0f..1f,
                onValueChange = { onRecipeChange(GalleryRecipeReducer.updateStampGlow(recipe, it)) }
            )

            if (stamp.format == StampFormat.CUSTOM) {
                OutlinedTextField(
                    value = stamp.customText,
                    onValueChange = { onRecipeChange(GalleryRecipeReducer.updateStampCustomText(recipe, it)) },
                    label = { Text("Custom Stamp (A-Z, 0-9, ' . : - /)", fontSize = 11.sp) },
                    singleLine = true,
                    colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ProCcdPalette.WarmAmber,
                        unfocusedBorderColor = ProCcdPalette.BorderMetallic,
                        focusedTextColor = ProCcdPalette.CreamInk,
                        unfocusedTextColor = ProCcdPalette.CreamInk,
                        focusedLabelColor = ProCcdPalette.WarmAmber,
                        unfocusedLabelColor = ProCcdPalette.TextSecondary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun CropAndRotateDeck(
    recipe: CaptureRecipe,
    selectedCropPreset: StudioCropPreset,
    hapticsEnabled: Boolean,
    onSelectCropPreset: (StudioCropPreset) -> Unit,
    onRotate90Cw: () -> Unit,
    onDialTick: () -> Unit,
    onRecipeChange: (CaptureRecipe) -> Unit
) {
    val optics = recipe.opticalSettings
    val fonts = LocalProCcdFonts.current

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HardwareSectionLabel(text = "ASPECT RATIO PRESETS", modifier = Modifier.weight(1f))
            OutlinedButton(
                onClick = onRotate90Cw,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                border = BorderStroke(1.dp, ProCcdPalette.LedCyan),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.RotateRight,
                    contentDescription = null,
                    tint = ProCcdPalette.LedCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "ROTATE 90°",
                    color = ProCcdPalette.LedCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fonts.condensed
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StudioCropPreset.entries.forEach { preset ->
                HardwareChip(
                    label = preset.label,
                    selected = selectedCropPreset == preset,
                    onClick = { onSelectCropPreset(preset) }
                )
            }
        }

        NotchedRulerDial(
            label = "STRAIGHTEN (-15° .. +15°)",
            valueText = String.format(Locale.US, "%+.1f°", optics.straightenDegrees),
            value = optics.straightenDegrees,
            valueRange = GalleryRecipeReducer.MIN_STRAIGHTEN_DEG..GalleryRecipeReducer.MAX_STRAIGHTEN_DEG,
            stepSize = 0.5f,
            resetValue = 0f,
            hapticsEnabled = hapticsEnabled,
            accentColor = ProCcdPalette.WarmAmber,
            onTick = onDialTick,
            onValueChange = { onRecipeChange(GalleryRecipeReducer.updateStraightenDegrees(recipe, it)) }
        )
    }
}

internal fun lensLabel(lens: LensAttachment): String = when (lens) {
    LensAttachment.STANDARD -> "Standard"
    LensAttachment.FISHEYE -> "Fisheye"
    LensAttachment.STAR_4PT -> "4-pt Star"
    LensAttachment.STAR_6PT -> "6-pt Star"
    LensAttachment.SOFT_GLOW -> "Soft Glow"
    LensAttachment.PRISM -> "Prism"
    LensAttachment.KALEIDOSCOPE -> "Kaleidoscope"
    LensAttachment.GRID_2X -> "2x Grid"
    LensAttachment.GRID_4X -> "4x Grid"
}

internal fun frameLabel(frame: FrameBorderStyle): String = when (frame) {
    FrameBorderStyle.NONE -> "None"
    FrameBorderStyle.INSTANT_PRINT -> "Instant"
    FrameBorderStyle.INSTANT_WIDE -> "Wide Instant"
    FrameBorderStyle.POLAROID_SQUARE -> "Polaroid Sq"
    FrameBorderStyle.FILM_35MM -> "35mm Film"
    FrameBorderStyle.DV_TIMECODE -> "DV Timecode"
    FrameBorderStyle.CINE_16MM -> "16mm Cine"
    FrameBorderStyle.PHOTO_BOOTH_4STRIP -> "4-Strip Booth"
}

internal fun stampFormatLabel(format: StampFormat): String = when (format) {
    StampFormat.SHORT_YY_MM_DD -> "'YY MM DD"
    StampFormat.DOT_YYYY_MM_DD -> "YYYY.MM.DD"
    StampFormat.EURO_DD_MM_YY -> "DD.MM.YY"
    StampFormat.CUSTOM -> "Custom"
}

internal fun stampFontLabel(fontStyle: StampFontStyle): String = when (fontStyle) {
    StampFontStyle.DOT_MATRIX -> "Dot Matrix"
    StampFontStyle.SEVEN_SEGMENT -> "7-Segment"
    StampFontStyle.VCR_OSD -> "VCR OSD"
    StampFontStyle.PIXEL -> "Pixel"
    StampFontStyle.CLEAN_SANS -> "Clean Sans"
}

internal fun stampPositionLabel(position: StampPosition): String = when (position) {
    StampPosition.BOTTOM_RIGHT -> "Bottom Right"
    StampPosition.BOTTOM_LEFT -> "Bottom Left"
    StampPosition.TOP_RIGHT -> "Top Right"
    StampPosition.TOP_LEFT -> "Top Left"
}

private fun stampFontFamilyFor(fontStyle: StampFontStyle, fonts: ProCcdFontSet): FontFamily = when (fontStyle) {
    StampFontStyle.DOT_MATRIX -> fonts.dotMatrix
    StampFontStyle.SEVEN_SEGMENT -> fonts.led
    StampFontStyle.VCR_OSD -> fonts.vcrOsd
    StampFontStyle.PIXEL -> fonts.pixel
    StampFontStyle.CLEAN_SANS -> fonts.condensed
}

internal fun parseHexColorOrDefault(hex: String, fallback: Color): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(fallback)
