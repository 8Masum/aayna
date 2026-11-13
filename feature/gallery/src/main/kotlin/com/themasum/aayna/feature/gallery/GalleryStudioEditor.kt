package com.themasum.aayna.feature.gallery

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Shuffle
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.themasum.aayna.core.gl.OffscreenGlExporter
import com.themasum.aayna.core.model.AspectRatioPreset
import com.themasum.aayna.core.model.CameraCatalog
import com.themasum.aayna.core.model.CaptureRecipe
import com.themasum.aayna.core.model.DateStampFormatter
import com.themasum.aayna.core.model.ExposureMath
import com.themasum.aayna.core.model.FlashMode
import com.themasum.aayna.core.model.FrameBorderStyle
import com.themasum.aayna.core.model.LensAttachment
import com.themasum.aayna.core.model.ResolutionPreset
import com.themasum.aayna.core.model.StampFormat
import com.themasum.aayna.core.model.StampStyle
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
    onRecipeChange: (CaptureRecipe) -> Unit,
    onDialTick: () -> Unit,
    onUpdateNegative: () -> Unit,
    onExportToGallery: () -> Unit,
    onCloseEditor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val activeProfile = remember(recipe.cameraId) { CameraCatalog.findByIdOrDefault(recipe.cameraId) }

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

    LaunchedEffect(
        sourceBitmap,
        recipe,
        formattedStamp,
        splitCompareRatio,
        isHoldOriginal
    ) {
        delay(PREVIEW_DEBOUNCE_MS)
        isRenderingPreview = true
        previewError = null
        val fallbackGains =
            ExposureMath.computeShaderFallbackGains(
                hasManualSensor = false,
                ev = recipe.opticalSettings.evCompensation,
                iso = recipe.opticalSettings.iso,
                shutterNs = recipe.opticalSettings.shutterSpeedNs
            )
        runCatching {
            offscreenExporter.renderBitmap(
                sourceBitmap = sourceBitmap,
                profile = activeProfile,
                recipe = recipe,
                stampText = formattedStamp,
                fallbackGains = fallbackGains,
                maxEdgeOverride = PREVIEW_MAX_EDGE_PX,
                splitCompareRatio = splitCompareRatio,
                isHoldOriginal = isHoldOriginal
            )
        }.onSuccess { rendered ->
            previewBitmap = rendered
            isRenderingPreview = false
        }.onFailure { err ->
            previewError = err.message ?: "Preview render failed"
            isRenderingPreview = false
        }
    }

    Column(
        modifier =
        modifier
            .fillMaxSize()
            .background(ProCcdPalette.BgCharcoal)
    ) {
        EditorTopBar(
            cameraCodeName = activeProfile.codeName,
            isSavedNegative = isSavedNegative,
            isExporting = isExporting,
            showSplitBar = showSplitBar,
            onToggleSplitBar = {
                showSplitBar = !showSplitBar
                if (!showSplitBar) {
                    splitCompareRatio = 0f
                } else if (splitCompareRatio == 0f) {
                    splitCompareRatio = 0.5f
                }
            },
            onUpdateNegative = onUpdateNegative,
            onExportToGallery = onExportToGallery,
            onCloseEditor = onCloseEditor
        )

        EditorPreviewViewport(
            previewBitmap = previewBitmap,
            fallbackBitmap = sourceBitmap,
            isRenderingPreview = isRenderingPreview,
            isExporting = isExporting,
            isHoldOriginal = isHoldOriginal,
            splitCompareRatio = splitCompareRatio,
            previewError = previewError,
            onHoldChange = { held -> isHoldOriginal = held },
            modifier =
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )

        if (showSplitBar) {
            SplitCompareControlBar(
                splitCompareRatio = splitCompareRatio,
                onRatioChange = { splitCompareRatio = it },
                onReset = { splitCompareRatio = 0f }
            )
        }

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
                .height(232.dp)
                .background(ProCcdPalette.SurfaceDark)
                .border(
                    width = 1.dp,
                    color = ProCcdPalette.BorderMetallic
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            when (selectedTab) {
                StudioEditorTab.CAMERA ->
                    CameraSwapDeck(
                        recipe = recipe,
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
                StudioEditorTab.OPTICS ->
                    OpticsControlDeck(
                        recipe = recipe,
                        onRecipeChange = onRecipeChange
                    )
                StudioEditorTab.EFFECTS ->
                    EffectsControlDeck(
                        recipe = recipe,
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
                StudioEditorTab.STAMP ->
                    FrameAndStampDeck(
                        recipe = recipe,
                        formattedStamp = formattedStamp,
                        onRecipeChange = onRecipeChange,
                        onDialTick = onDialTick
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
    onToggleSplitBar: () -> Unit,
    onUpdateNegative: () -> Unit,
    onExportToGallery: () -> Unit,
    onCloseEditor: () -> Unit
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .background(ProCcdPalette.SurfaceDark)
            .border(1.dp, ProCcdPalette.BorderMetallic)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCloseEditor) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back to Studio",
                    tint = ProCcdPalette.TextPrimary
                )
            }
            LedStatusPill(label = cameraCodeName, accentColor = ProCcdPalette.LedCyan)
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(onClick = onToggleSplitBar) {
                Icon(
                    imageVector = Icons.Rounded.Compare,
                    contentDescription = "Split Compare",
                    tint = if (showSplitBar) ProCcdPalette.LedAmber else ProCcdPalette.TextSecondary
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isSavedNegative) {
                OutlinedButton(
                    onClick = onUpdateNegative,
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
                        text = "UPDATE",
                        color = ProCcdPalette.TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Button(
                onClick = onExportToGallery,
                enabled = !isExporting,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                colors =
                ButtonDefaults.buttonColors(
                    containerColor = ProCcdPalette.LedAmber,
                    contentColor = ProCcdPalette.BgCharcoal
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
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun EditorPreviewViewport(
    previewBitmap: Bitmap?,
    fallbackBitmap: Bitmap,
    isRenderingPreview: Boolean,
    isExporting: Boolean,
    isHoldOriginal: Boolean,
    splitCompareRatio: Float,
    previewError: String?,
    onHoldChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeBitmap = previewBitmap ?: fallbackBitmap
    Box(
        modifier =
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(ProCcdPalette.SurfaceRecessed)
            .border(1.dp, ProCcdPalette.BorderMetallic, RoundedCornerShape(10.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onHoldChange(true)
                        tryAwaitRelease()
                        onHoldChange(false)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = activeBitmap.asImageBitmap(),
            contentDescription = "Studio Preview",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(6.dp)
        )

        if (splitCompareRatio > 0f && !isHoldOriginal) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier =
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(splitCompareRatio.coerceIn(0.02f, 0.98f))
                ) {
                    Box(
                        modifier =
                        Modifier
                            .align(Alignment.CenterEnd)
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(ProCcdPalette.LedAmber)
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
                LedStatusPill(label = "ORIGINAL (HOLD)", accentColor = ProCcdPalette.LedRed)
            } else if (splitCompareRatio > 0f) {
                val pct = (splitCompareRatio * 100f).roundToInt()
                LedStatusPill(label = "SPLIT $pct%", accentColor = ProCcdPalette.LedAmber)
            } else {
                LedStatusPill(
                    label = "HOLD IMAGE = ORIGINAL",
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
                color = ProCcdPalette.BgCharcoal.copy(alpha = 0.85f),
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
                        color = ProCcdPalette.LedAmber
                    )
                    Text(
                        text = if (isExporting) "EXPORTING FULL RES" else "GPU RENDER",
                        color = ProCcdPalette.TextPrimary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
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
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun SplitCompareControlBar(splitCompareRatio: Float, onRatioChange: (Float) -> Unit, onReset: () -> Unit) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .background(ProCcdPalette.SurfaceElevated)
            .border(1.dp, ProCcdPalette.BorderMetallic)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "SPLIT",
            color = ProCcdPalette.LedAmber,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.width(8.dp))
        Slider(
            value = splitCompareRatio,
            onValueChange = onRatioChange,
            valueRange = 0f..1f,
            colors =
            SliderDefaults.colors(
                thumbColor = ProCcdPalette.LedAmber,
                activeTrackColor = ProCcdPalette.LedAmber,
                inactiveTrackColor = ProCcdPalette.BorderMetallic
            ),
            modifier = Modifier.weight(1f).height(28.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        HardwareChip(
            label = "OFF",
            selected = splitCompareRatio == 0f,
            onClick = onReset
        )
    }
}

@Composable
private fun EditorTabStrip(selectedTab: StudioEditorTab, onSelectTab: (StudioEditorTab) -> Unit) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .background(ProCcdPalette.BgCharcoal)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        StudioEditorTab.entries.forEach { tab ->
            val selected = tab == selectedTab
            Box(
                modifier =
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selected) ProCcdPalette.SurfaceElevated else ProCcdPalette.SurfaceDark
                    )
                    .border(
                        width = 1.dp,
                        color = if (selected) ProCcdPalette.LedAmber else ProCcdPalette.BorderMetallic,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelectTab(tab) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tab.label,
                    color = if (selected) ProCcdPalette.LedAmber else ProCcdPalette.TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun CameraSwapDeck(
    recipe: CaptureRecipe,
    onSelectProfile: (com.themasum.aayna.core.model.CameraProfile) -> Unit,
    onResetDefaults: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HardwareSectionLabel(text = "CAMERA BODY / LUT PROFILE", modifier = Modifier.weight(1f))
            HardwareChip(
                label = "RESET DEFAULTS",
                selected = false,
                onClick = onResetDefaults,
                accentColor = ProCcdPalette.LedCyan
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 6.dp)
        ) {
            items(CameraCatalog.all, key = { it.id }) { profile ->
                val selected = profile.id == recipe.cameraId
                val accent = parseHexColorOrDefault(profile.skin.accentHex, ProCcdPalette.LedAmber)
                val bodyPrimary = parseHexColorOrDefault(profile.skin.bodyPrimaryHex, ProCcdPalette.SurfaceElevated)
                Surface(
                    modifier =
                    Modifier
                        .width(124.dp)
                        .clickable { onSelectProfile(profile) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) ProCcdPalette.SurfaceElevated else ProCcdPalette.BgCharcoal,
                    border =
                    BorderStroke(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) ProCcdPalette.LedAmber else ProCcdPalette.BorderMetallic
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
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = profile.codeName,
                            color = if (selected) ProCcdPalette.LedAmber else ProCcdPalette.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = profile.subtitle,
                            color = ProCcdPalette.TextSecondary,
                            fontSize = 10.sp,
                            maxLines = 2,
                            lineHeight = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OpticsControlDeck(recipe: CaptureRecipe, onRecipeChange: (CaptureRecipe) -> Unit) {
    val optics = recipe.opticalSettings
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HardwareSliderRow(
            label = "EXPOSURE (EV)",
            valueText = String.format(Locale.US, "%+.2f EV", optics.evCompensation),
            value = optics.evCompensation,
            valueRange = GalleryRecipeReducer.MIN_EV..GalleryRecipeReducer.MAX_EV,
            onValueChange = { onRecipeChange(GalleryRecipeReducer.updateEv(recipe, it)) }
        )
        HardwareSliderRow(
            label = "WARMTH",
            valueText = String.format(Locale.US, "%+.2f", optics.warmthShift),
            value = optics.warmthShift,
            valueRange = GalleryRecipeReducer.MIN_WARMTH..GalleryRecipeReducer.MAX_WARMTH,
            onValueChange = { onRecipeChange(GalleryRecipeReducer.updateWarmth(recipe, it)) },
            accentColor = ProCcdPalette.LedCyan
        )
        HardwareSliderRow(
            label = "BLOOM",
            valueText = "${(optics.bloomIntensity * 100f).roundToInt()}%",
            value = optics.bloomIntensity,
            valueRange = 0f..1f,
            onValueChange = { onRecipeChange(GalleryRecipeReducer.updateBloom(recipe, it)) }
        )
        HardwareSliderRow(
            label = "VIGNETTE",
            valueText = "${(optics.vignetteIntensity * 100f).roundToInt()}%",
            value = optics.vignetteIntensity,
            valueRange = 0f..1f,
            onValueChange = { onRecipeChange(GalleryRecipeReducer.updateVignette(recipe, it)) },
            accentColor = ProCcdPalette.LedCyan
        )

        HardwareSectionLabel(text = "LENS ATTACHMENT")
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LensAttachment.entries.forEach { lens ->
                HardwareChip(
                    label = lensLabel(lens),
                    selected = optics.lensAttachment == lens,
                    onClick = { onRecipeChange(GalleryRecipeReducer.updateLensAttachment(recipe, lens)) }
                )
            }
        }
    }
}

@Composable
private fun EffectsControlDeck(
    recipe: CaptureRecipe,
    onRecipeChange: (CaptureRecipe) -> Unit,
    onShuffleSeed: () -> Unit,
    onToggleFlash: () -> Unit
) {
    val optics = recipe.opticalSettings
    val flashSimOn = optics.flashMode == FlashMode.OPTICAL_FLASH_SIM

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onShuffleSeed,
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, ProCcdPalette.LedAmber),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = null,
                    tint = ProCcdPalette.LedAmber,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "SHUFFLE LEAK/DUST",
                    color = ProCcdPalette.LedAmber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
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
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun FrameAndStampDeck(
    recipe: CaptureRecipe,
    formattedStamp: String,
    onRecipeChange: (CaptureRecipe) -> Unit,
    onDialTick: () -> Unit
) {
    val optics = recipe.opticalSettings
    val stamp = recipe.stampConfig

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HardwareSectionLabel(text = "ASPECT RATIO & EXPORT RESOLUTION")
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AspectRatioPreset.entries.forEach { aspect ->
                HardwareChip(
                    label = aspect.label,
                    selected = optics.aspectRatio == aspect,
                    onClick = {
                        onDialTick()
                        onRecipeChange(GalleryRecipeReducer.updateAspectRatio(recipe, aspect))
                    }
                )
            }
            ResolutionPreset.entries.forEach { preset ->
                HardwareChip(
                    label = preset.label,
                    selected = recipe.resolutionPreset == preset,
                    onClick = {
                        onDialTick()
                        onRecipeChange(GalleryRecipeReducer.updateResolutionPreset(recipe, preset))
                    },
                    accentColor = ProCcdPalette.LedCyan
                )
            }
        }

        HardwareSectionLabel(text = "FRAME BORDER")
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
                    }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "DATE STAMP",
                    color = ProCcdPalette.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = if (stamp.enabled) "PREVIEW: $formattedStamp" else "DISABLED",
                    color = if (stamp.enabled) ProCcdPalette.LedAmber else ProCcdPalette.TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Switch(
                checked = stamp.enabled,
                onCheckedChange = { onRecipeChange(GalleryRecipeReducer.updateStampEnabled(recipe, it)) },
                colors =
                SwitchDefaults.colors(
                    checkedThumbColor = ProCcdPalette.BgCharcoal,
                    checkedTrackColor = ProCcdPalette.LedAmber
                )
            )
        }

        if (stamp.enabled) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StampStyle.entries.forEach { style ->
                    val swatch = parseHexColorOrDefault(style.hexColor, ProCcdPalette.LedAmber)
                    val selected = stamp.style == style
                    Box(
                        modifier =
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ProCcdPalette.BgCharcoal)
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

            if (stamp.format == StampFormat.CUSTOM) {
                OutlinedTextField(
                    value = stamp.customText,
                    onValueChange = { onRecipeChange(GalleryRecipeReducer.updateStampCustomText(recipe, it)) },
                    label = { Text("Custom Stamp (A-Z, 0-9, ' . : - /)", fontSize = 11.sp) },
                    singleLine = true,
                    colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ProCcdPalette.LedAmber,
                        unfocusedBorderColor = ProCcdPalette.BorderMetallic,
                        focusedTextColor = ProCcdPalette.TextPrimary,
                        unfocusedTextColor = ProCcdPalette.TextPrimary,
                        focusedLabelColor = ProCcdPalette.LedAmber,
                        unfocusedLabelColor = ProCcdPalette.TextSecondary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun lensLabel(lens: LensAttachment): String = when (lens) {
    LensAttachment.STANDARD -> "Standard"
    LensAttachment.FISHEYE -> "Fisheye"
    LensAttachment.STAR_4PT -> "4-pt Star"
    LensAttachment.STAR_6PT -> "6-pt Star"
}

private fun frameLabel(frame: FrameBorderStyle): String = when (frame) {
    FrameBorderStyle.NONE -> "None"
    FrameBorderStyle.INSTANT_PRINT -> "Instant Print"
    FrameBorderStyle.FILM_35MM -> "35mm Film"
}

private fun stampFormatLabel(format: StampFormat): String = when (format) {
    StampFormat.SHORT_YY_MM_DD -> "'YY MM DD"
    StampFormat.DOT_YYYY_MM_DD -> "YYYY.MM.DD"
    StampFormat.EURO_DD_MM_YY -> "DD.MM.YY"
    StampFormat.CUSTOM -> "Custom"
}

private fun parseHexColorOrDefault(hex: String, fallback: Color): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(fallback)
