package com.themasum.aayna.feature.viewfinder

import android.opengl.GLSurfaceView
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CropOriginal
import androidx.compose.material.icons.rounded.Flare
import androidx.compose.material.icons.rounded.FlashAuto
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.FlipCameraAndroid
import androidx.compose.material.icons.rounded.GridOff
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.themasum.aayna.core.gl.AaynaViewfinderGlRenderer
import com.themasum.aayna.core.model.DateStampFormatter
import com.themasum.aayna.core.model.FlashMode
import com.themasum.aayna.core.model.TimerDuration
import com.themasum.aayna.core.model.ViewfinderMaskType
import com.themasum.aayna.core.storage.ThumbnailMemoryCache
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ProccdChassisBackdrop(
    state: ViewfinderUiState,
    chassisBitmap: ImageBitmap?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier.cameraChassisSurface(state.activeSkin)) {
        if (chassisBitmap != null) {
            Image(
                bitmap = chassisBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Subtle dark vignette for crisp contrast over metallic/pastel chassis textures
            Box(
                modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (state.activeSkin.isLightBody) 0.14f else 0.24f))
            )
        }
        content()
    }
}

@Composable
internal fun ProccdCameraScreenContainer(
    state: ViewfinderUiState,
    proportions: SkinProportionsSpec,
    bezelBitmap: ImageBitmap?,
    glassSheenBitmap: ImageBitmap?,
    hasCameraPermission: Boolean,
    useSurfaceProcessorMode: Boolean,
    glRenderer: AaynaViewfinderGlRenderer,
    onPreviewViewReady: (PreviewView) -> Unit,
    onGlSurfaceViewReady: (GLSurfaceView) -> Unit,
    onViewfinderSizeChanged: (IntSize) -> Unit,
    onTapFocus: (Offset, IntSize) -> Unit,
    onHoldOriginalChanged: (Boolean) -> Unit,
    onSelectZoomRatio: (Float) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenGallery: () -> Unit,
    onToggleMinimalMode: () -> Unit,
    onToggleCameraShelf: () -> Unit,
    ledFontFamily: FontFamily,
    vcrOsdFontFamily: FontFamily,
    condensedFontFamily: FontFamily,
    containerWidth: Dp,
    containerHeight: Dp,
    modifier: Modifier = Modifier
) {
    val skin = state.activeSkin
    val accent = parseHexColor(skin.accentHex, Color(0xFF4CD4E8))
    val statusBarHeight = 30.dp
    val availableBezelHeight = (containerHeight - statusBarHeight - 4.dp).coerceAtLeast(160.dp)
    val bezelWidth = minOf(containerWidth, availableBezelHeight * proportions.bezelAspectRatio)
    val bezelHeight = bezelWidth / proportions.bezelAspectRatio

    val cutoutWidth = bezelWidth * proportions.cutoutWidthFraction
    val cutoutHeight = bezelHeight * proportions.cutoutHeightFraction
    val cutoutOffsetX = bezelWidth * proportions.cutoutLeftFraction
    val cutoutOffsetY = bezelHeight * proportions.cutoutTopFraction

    Column(
        modifier =
        modifier
            .width(containerWidth)
            .height(containerHeight),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ProccdOpticalStatusBar(
            state = state,
            accent = accent,
            ledFontFamily = ledFontFamily,
            condensedFontFamily = condensedFontFamily,
            onToggleCameraShelf = onToggleCameraShelf,
            onToggleMinimalMode = onToggleMinimalMode,
            modifier =
            Modifier
                .fillMaxWidth()
                .height(statusBarHeight)
                .padding(horizontal = 6.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier =
            Modifier
                .width(bezelWidth)
                .height(bezelHeight),
            contentAlignment = Alignment.TopStart
        ) {
            Box(
                modifier =
                Modifier
                    .offset(x = cutoutOffsetX, y = cutoutOffsetY)
                    .width(cutoutWidth)
                    .height(cutoutHeight)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black)
                    .onSizeChanged(onViewfinderSizeChanged)
                    .pointerInput(hasCameraPermission) {
                        if (!hasCameraPermission) return@pointerInput
                        detectTapGestures(
                            onPress = {
                                val start = System.currentTimeMillis()
                                val released = tryAwaitRelease()
                                val held = System.currentTimeMillis() - start >= 180L
                                if (released && held) {
                                    onHoldOriginalChanged(false)
                                }
                            },
                            onLongPress = {
                                onHoldOriginalChanged(true)
                            },
                            onTap = { offset ->
                                onTapFocus(offset, size)
                            }
                        )
                    }
            ) {
                if (hasCameraPermission) {
                    if (useSurfaceProcessorMode) {
                        AndroidView(
                            factory = { ctx ->
                                PreviewView(ctx).apply {
                                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                    onPreviewViewReady(this)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        AndroidView(
                            factory = { ctx ->
                                GLSurfaceView(ctx).apply {
                                    setEGLContextClientVersion(3)
                                    setRenderer(glRenderer)
                                    renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                                    onGlSurfaceViewReady(this)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    if (!state.isMinimalMode) {
                        OpticalViewfinderMaskOverlay(
                            state = state,
                            accent = accent,
                            vcrOsdFontFamily = vcrOsdFontFamily,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    if (state.isGridVisible) {
                        RuleOfThirdsGridOverlay(modifier = Modifier.fillMaxSize())
                    }

                    state.focusReticle?.let { reticle ->
                        FocusReticleOverlay(
                            reticle = reticle,
                            accent = accent,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    if (!state.isMinimalMode) {
                        ZoomRockerBar(
                            state = state,
                            accent = accent,
                            onSelectZoomRatio = onSelectZoomRatio,
                            modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 10.dp)
                        )
                    }
                } else {
                    CameraPermissionCard(
                        accent = accent,
                        onRequestPermission = onRequestPermission,
                        onOpenGallery = onOpenGallery,
                        modifier =
                        Modifier
                            .align(Alignment.Center)
                            .padding(14.dp)
                    )
                }

                if (state.isPreviewHoldOriginal) {
                    Box(
                        modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.78f))
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

                state.statusBannerMessage?.let { banner ->
                    Box(
                        modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF14161B).copy(alpha = 0.9f))
                            .border(1.dp, accent.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = banner,
                            color = Color(0xFFF3F4F6),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = state.showCaptureFlashPulse,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    val pulseColor =
                        if (state.opticalSettings.flashMode == FlashMode.OPTICAL_FLASH_SIM) {
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

                if (glassSheenBitmap != null) {
                    Image(
                        bitmap = glassSheenBitmap,
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        alpha = 0.24f,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            if (bezelBitmap != null) {
                Image(
                    bitmap = bezelBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier =
                    Modifier
                        .offset(x = cutoutOffsetX, y = cutoutOffsetY)
                        .width(cutoutWidth)
                        .height(cutoutHeight)
                        .border(3.dp, parseHexColor(skin.bodySecondaryHex), RoundedCornerShape(10.dp))
                )
            }
        }
    }
}

@Composable
private fun ProccdOpticalStatusBar(
    state: ViewfinderUiState,
    accent: Color,
    ledFontFamily: FontFamily,
    condensedFontFamily: FontFamily,
    onToggleCameraShelf: () -> Unit,
    onToggleMinimalMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val skin = state.activeSkin
    val ledColor =
        when {
            state.isRecordingVideo -> parseHexColor(skin.ledFlashHex, Color(0xFFFF453A))
            state.isCapturing -> parseHexColor(skin.ledFlashHex, Color(0xFFFF453A))
            else -> parseHexColor(skin.ledActiveHex, Color(0xFF39FFAE))
        }
    val ledPulseTransition = rememberInfiniteTransition(label = "ivLed")
    val ledAlpha by ledPulseTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(animation = tween(600), repeatMode = RepeatMode.Reverse),
        label = "ivLedAlpha"
    )
    val rollText = ViewfinderStateReducer.formatRollCounter(state.rollShotsTaken, state.rollCapacity)
    val flashText =
        when (state.opticalSettings.flashMode) {
            FlashMode.OFF -> "FL:OFF"
            FlashMode.AUTO -> "FL:AUTO"
            FlashMode.HARDWARE_ON -> "FL:ON"
            FlashMode.OPTICAL_FLASH_SIM -> "FL:SIM"
        }

    Row(
        modifier =
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0E1014).copy(alpha = 0.86f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.clickable(onClick = onToggleCameraShelf)
        ) {
            // iv_led glowing status indicator
            Box(
                modifier =
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(
                        ledColor.copy(
                            alpha = if (state.isRecordingVideo ||
                                state.isCapturing
                            ) {
                                ledAlpha
                            } else {
                                0.95f
                            }
                        )
                    )
                    .border(1.5.dp, ledColor.copy(alpha = 0.4f), CircleShape)
            )
            Text(
                text = state.activeCamera.codeName,
                color = Color(0xFFF3F4F6),
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = condensedFontFamily,
                maxLines = 1
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = rollText,
                color = parseHexColor(skin.lcdForegroundHex, accent),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = ledFontFamily
            )
            if (!state.isMinimalMode) {
                Text(
                    text = flashText,
                    color = Color(0xFFB5BAC4),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Icon(
                imageVector = if (state.isMinimalMode) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                contentDescription = "Minimal mode",
                tint = if (state.isMinimalMode) accent else Color(0xFFB5BAC4),
                modifier =
                Modifier
                    .size(16.dp)
                    .clickable(onClick = onToggleMinimalMode)
            )
        }
    }
}

@Composable
private fun ZoomRockerBar(
    state: ViewfinderUiState,
    accent: Color,
    onSelectZoomRatio: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val presets = state.zoomUiState.bounds.availablePresetRatios()
    val currentRatio = state.zoomUiState.zoomRatio
    Row(
        modifier =
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.62f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        presets.forEach { preset ->
            val selected = kotlin.math.abs(currentRatio - preset) < 0.18f
            val label =
                if (preset < 1f) {
                    String.format(Locale.US, "%.1fx", preset)
                } else {
                    "${preset.roundToInt()}x"
                }
            Box(
                modifier =
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) accent.copy(alpha = 0.25f) else Color.Transparent)
                    .border(
                        1.dp,
                        if (selected) accent else Color.Transparent,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelectZoomRatio(preset) }
                    .padding(horizontal = 7.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (selected) accent else Color(0xFFE2E5EC),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
internal fun OpticalViewfinderMaskOverlay(
    state: ViewfinderUiState,
    accent: Color,
    vcrOsdFontFamily: FontFamily,
    modifier: Modifier = Modifier
) {
    val maskType = state.activeCamera.viewfinderMaskType
    val blinkTransition = rememberInfiniteTransition(label = "hudBlink")
    val blinkAlpha by blinkTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(animation = tween(520), repeatMode = RepeatMode.Reverse),
        label = "hudBlinkAlpha"
    )

    Box(modifier = modifier) {
        when (maskType) {
            ViewfinderMaskType.STANDARD_TFT -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val corner = 14.dp.toPx()
                    val stroke = 1.4.dp.toPx()
                    val c = Color.White.copy(alpha = 0.35f)
                    val pad = 10.dp.toPx()
                    drawCornerTicks(pad, corner, stroke, c)
                }
            }

            ViewfinderMaskType.DISPOSABLE_PEEPHOLE -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(color = Color.Black.copy(alpha = 0.18f))
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val r = 22.dp.toPx()
                    drawCircle(
                        color = Color.White.copy(alpha = 0.72f),
                        radius = r,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                    val tick = 9.dp.toPx()
                    drawLine(
                        Color.White.copy(alpha = 0.8f),
                        Offset(cx - tick, cy),
                        Offset(cx + tick, cy),
                        1.4.dp.toPx()
                    )
                    drawLine(
                        Color.White.copy(alpha = 0.8f),
                        Offset(cx, cy - tick),
                        Offset(cx, cy + tick),
                        1.4.dp.toPx()
                    )
                    drawCornerTicks(18.dp.toPx(), 16.dp.toPx(), 1.5.dp.toPx(), Color.White.copy(alpha = 0.55f))
                }
            }

            ViewfinderMaskType.CAMCORDER_DV,
            ViewfinderMaskType.VHS_TAPE -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val step = 4.dp.toPx()
                    var y = 0f
                    while (y < size.height) {
                        drawLine(
                            color = Color.Black.copy(alpha = 0.14f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx()
                        )
                        y += step
                    }
                    drawCornerTicks(12.dp.toPx(), 18.dp.toPx(), 1.6.dp.toPx(), Color.White.copy(alpha = 0.7f))
                }
                Row(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier =
                            Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(
                                    if (state.isRecordingVideo) {
                                        Color(0xFFFF3B30).copy(alpha = blinkAlpha)
                                    } else {
                                        Color(0xFF39FFAE)
                                    }
                                )
                        )
                        Text(
                            text = if (state.isRecordingVideo) "REC" else "STBY",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = vcrOsdFontFamily
                        )
                    }
                    Text(
                        text = DateStampFormatter.formatTimecode(state.recordingElapsedMs),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = vcrOsdFontFamily
                    )
                }
                // Compact VU Audio Meter on bottom-left
                Row(
                    modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 14.dp, bottom = 38.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    val activeBars = if (state.isRecordingVideo) ((blinkAlpha * 6).roundToInt().coerceIn(2, 6)) else 2
                    for (i in 1..6) {
                        val barColor = if (i >= 5) Color(0xFFFF453A) else Color(0xFF39FFAE)
                        Box(
                            modifier =
                            Modifier
                                .width(3.dp)
                                .height((4 + i * 2).dp)
                                .background(if (i <= activeBars) barColor else Color.White.copy(alpha = 0.2f))
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (maskType == ViewfinderMaskType.VHS_TAPE) "VHS SP" else "DV SP",
                        color = Color.White.copy(alpha = 0.82f),
                        fontSize = 9.sp,
                        fontFamily = vcrOsdFontFamily
                    )
                }
            }

            ViewfinderMaskType.RANGEFINDER -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val insetX = size.width * 0.1f
                    val insetY = size.height * 0.1f
                    drawRect(
                        color = Color(0xFFFFE082).copy(alpha = 0.65f),
                        topLeft = Offset(insetX, insetY),
                        size = Size(size.width - insetX * 2f, size.height - insetY * 2f),
                        style = Stroke(width = 1.4.dp.toPx())
                    )
                    val patchW = 28.dp.toPx()
                    val patchH = 18.dp.toPx()
                    drawRect(
                        color = Color(0xFFFFE082).copy(alpha = 0.35f),
                        topLeft = Offset((size.width - patchW) / 2f, (size.height - patchH) / 2f),
                        size = Size(patchW, patchH)
                    )
                }
            }

            ViewfinderMaskType.SLR_OPTICAL -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val outerR = 26.dp.toPx()
                    val innerR = 14.dp.toPx()
                    drawCircle(
                        color = Color.White.copy(alpha = 0.65f),
                        radius = outerR,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.78f),
                        radius = innerR,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.78f),
                        start = Offset(cx - innerR, cy),
                        end = Offset(cx + innerR, cy),
                        strokeWidth = 1.1.dp.toPx()
                    )
                    // Right-side analog exposure meter needle
                    val meterX = size.width - 14.dp.toPx()
                    val meterTop = size.height * 0.32f
                    val meterBot = size.height * 0.68f
                    val meterMid = (meterTop + meterBot) / 2f
                    drawLine(
                        Color.White.copy(alpha = 0.6f),
                        Offset(meterX, meterTop),
                        Offset(meterX, meterBot),
                        1.5.dp.toPx()
                    )
                    val evRatio = (state.opticalSettings.evCompensation / 2f).coerceIn(-1f, 1f)
                    val needleY = meterMid - evRatio * ((meterBot - meterTop) / 2f)
                    drawCircle(color = accent, radius = 3.5.dp.toPx(), center = Offset(meterX, needleY))
                }
            }

            ViewfinderMaskType.INSTANT_SQUARE -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val side = minOf(size.width, size.height) * 0.88f
                    val left = (size.width - side) / 2f
                    val top = (size.height - side) / 2f
                    drawRect(
                        color = Color.White.copy(alpha = 0.65f),
                        topLeft = Offset(left, top),
                        size = Size(side, side),
                        style = Stroke(width = 1.4.dp.toPx())
                    )
                }
            }

            ViewfinderMaskType.INSTANT_WIDE -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width * 0.9f
                    val h = w * 0.68f
                    val left = (size.width - w) / 2f
                    val top = (size.height - h) / 2f
                    drawRect(
                        color = Color.White.copy(alpha = 0.65f),
                        topLeft = Offset(left, top),
                        size = Size(w, h),
                        style = Stroke(width = 1.4.dp.toPx())
                    )
                }
            }

            ViewfinderMaskType.MULTI_D3D -> {
                Row(
                    modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "D3D",
                        color = accent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    for (i in 1..4) {
                        Box(
                            modifier =
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (state.isCapturing) accent else Color.White.copy(alpha = 0.55f))
                        )
                    }
                }
            }

            ViewfinderMaskType.PHOTO_BOOTH -> {
                Row(
                    modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BOOTH",
                        color = accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    for (step in 1..4) {
                        val active = state.boothSequenceStep >= step
                        Box(
                            modifier =
                            Modifier
                                .size(width = 12.dp, height = 8.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (active) accent else Color.White.copy(alpha = 0.25f))
                        )
                    }
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCornerTicks(
    pad: Float,
    len: Float,
    stroke: Float,
    color: Color
) {
    // Top-left
    drawLine(color, Offset(pad, pad), Offset(pad + len, pad), stroke)
    drawLine(color, Offset(pad, pad), Offset(pad, pad + len), stroke)
    // Top-right
    drawLine(color, Offset(size.width - pad, pad), Offset(size.width - pad - len, pad), stroke)
    drawLine(color, Offset(size.width - pad, pad), Offset(size.width - pad, pad + len), stroke)
    // Bottom-left
    drawLine(color, Offset(pad, size.height - pad), Offset(pad + len, size.height - pad), stroke)
    drawLine(color, Offset(pad, size.height - pad), Offset(pad, size.height - pad - len), stroke)
    // Bottom-right
    drawLine(
        color,
        Offset(size.width - pad, size.height - pad),
        Offset(size.width - pad - len, size.height - pad),
        stroke
    )
    drawLine(
        color,
        Offset(size.width - pad, size.height - pad),
        Offset(size.width - pad, size.height - pad - len),
        stroke
    )
}

@Composable
internal fun ProccdLowerTactileDeck(
    state: ViewfinderUiState,
    shutterBitmap: ImageBitmap?,
    carouselIconBitmap: ImageBitmap?,
    thumbnailCache: ThumbnailMemoryCache?,
    onOpenGallery: () -> Unit,
    onShutterClick: () -> Unit,
    onToggleVideoMode: (Boolean) -> Unit,
    onOpenPanel: (ViewfinderPanel) -> Unit,
    onCycleFlash: () -> Unit,
    onCycleTimer: () -> Unit,
    onFlipCamera: () -> Unit,
    onToggleGrid: () -> Unit,
    onToggleMinimalMode: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val skin = state.activeSkin
    val accent = parseHexColor(skin.accentHex, Color(0xFF4CD4E8))

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!state.isMinimalMode) {
            AuxControlsStrip(
                state = state,
                accent = accent,
                onCycleFlash = onCycleFlash,
                onCycleTimer = onCycleTimer,
                onFlipCamera = onFlipCamera,
                onOpenPanel = onOpenPanel,
                onToggleGrid = onToggleGrid,
                onToggleMinimalMode = onToggleMinimalMode,
                onOpenSettings = onOpenSettings
            )
        }

        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: tilted -4° photo_card_view opening Gallery
            TiltedPhotoCardView(
                thumbnailPath = state.lastNegativeThumbnailPath,
                thumbnailModifiedMs = state.lastNegativeTimestampMs,
                thumbnailCache = thumbnailCache,
                accent = accent,
                onClick = onOpenGallery
            )

            // Center: layered shutter_<id>.png button
            LayeredPngShutterButton(
                skin = skin,
                shutterBitmap = shutterBitmap,
                isCapturing = state.isCapturing,
                isVideoMode = state.isVideoMode,
                isRecordingVideo = state.isRecordingVideo,
                countdownSeconds = state.countdownRemainingSeconds,
                onClick = onShutterClick
            )

            // Right: PHOTO / VIDEO toggle pill + Shelf/Skin buttons
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PhotoVideoTogglePill(
                    isVideoMode = state.isVideoMode,
                    supportsVideo = state.activeCamera.supportsVideo,
                    accent = accent,
                    onSelectVideo = onToggleVideoMode
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmallHardwareButton(
                        label = "SHELF",
                        active = state.activePanel == ViewfinderPanel.CAMERA_SHELF,
                        accent = accent,
                        onClick = { onOpenPanel(ViewfinderPanel.CAMERA_SHELF) }
                    ) {
                        if (carouselIconBitmap != null) {
                            Image(
                                bitmap = carouselIconBitmap,
                                contentDescription = "Camera Shelf",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.CameraAlt,
                                contentDescription = "Camera Shelf",
                                tint = accent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    SmallHardwareButton(
                        label = "SKIN",
                        active = state.activePanel == ViewfinderPanel.SKIN_MANAGER,
                        accent = accent,
                        onClick = { onOpenPanel(ViewfinderPanel.SKIN_MANAGER) }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Palette,
                            contentDescription = "Skins",
                            tint = if (state.activePanel == ViewfinderPanel.SKIN_MANAGER) accent else Color(0xFFE5E7EB),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AuxControlsStrip(
    state: ViewfinderUiState,
    accent: Color,
    onCycleFlash: () -> Unit,
    onCycleTimer: () -> Unit,
    onFlipCamera: () -> Unit,
    onOpenPanel: (ViewfinderPanel) -> Unit,
    onToggleGrid: () -> Unit,
    onToggleMinimalMode: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val flashIcon =
        when (state.opticalSettings.flashMode) {
            FlashMode.OFF -> Icons.Rounded.FlashOff
            FlashMode.AUTO -> Icons.Rounded.FlashAuto
            FlashMode.HARDWARE_ON -> Icons.Rounded.FlashOn
            FlashMode.OPTICAL_FLASH_SIM -> Icons.Rounded.Flare
        }
    val flashLabel =
        when (state.opticalSettings.flashMode) {
            FlashMode.OFF -> "OFF"
            FlashMode.AUTO -> "AUTO"
            FlashMode.HARDWARE_ON -> "ON"
            FlashMode.OPTICAL_FLASH_SIM -> "SIM"
        }
    val timerLabel =
        when (state.timerDuration) {
            TimerDuration.OFF -> "0s"
            TimerDuration.THREE_SECONDS -> "3s"
            TimerDuration.TEN_SECONDS -> "10s"
        }

    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AuxPill(
            label = flashLabel,
            active = state.opticalSettings.flashMode != FlashMode.OFF,
            accent = accent,
            icon = flashIcon,
            onClick = onCycleFlash
        )
        AuxPill(
            label = timerLabel,
            active = state.timerDuration != TimerDuration.OFF,
            accent = accent,
            icon = Icons.Rounded.Timer,
            onClick = onCycleTimer
        )
        AuxPill(
            label = if (state.isFrontCamera) "FRONT" else "REAR",
            active = state.isFrontCamera,
            accent = accent,
            icon = Icons.Rounded.FlipCameraAndroid,
            onClick = onFlipCamera
        )
        AuxPill(
            label = "MANUAL",
            active = state.activePanel == ViewfinderPanel.CONTROLS,
            accent = accent,
            icon = Icons.Rounded.Tune,
            onClick = { onOpenPanel(ViewfinderPanel.CONTROLS) }
        )
        AuxPill(
            label = "LENS",
            active = state.activePanel == ViewfinderPanel.LENS,
            accent = accent,
            icon = Icons.Rounded.AutoAwesome,
            onClick = { onOpenPanel(ViewfinderPanel.LENS) }
        )
        AuxPill(
            label = "FRAME",
            active = state.activePanel == ViewfinderPanel.FRAME,
            accent = accent,
            icon = Icons.Rounded.CropOriginal,
            onClick = { onOpenPanel(ViewfinderPanel.FRAME) }
        )
        AuxPill(
            label = "STAMP",
            active = state.activePanel == ViewfinderPanel.STAMP,
            accent = accent,
            icon = Icons.Rounded.Schedule,
            onClick = { onOpenPanel(ViewfinderPanel.STAMP) }
        )
        AuxPill(
            label = "GRID",
            active = state.isGridVisible,
            accent = accent,
            icon = if (state.isGridVisible) Icons.Rounded.GridOn else Icons.Rounded.GridOff,
            onClick = onToggleGrid
        )
        AuxPill(
            label = "MIN",
            active = state.isMinimalMode,
            accent = accent,
            icon = Icons.Rounded.VisibilityOff,
            onClick = onToggleMinimalMode
        )
        AuxPill(
            label = "SET",
            active = false,
            accent = accent,
            icon = Icons.Rounded.Settings,
            onClick = onOpenSettings
        )
    }
}

@Composable
private fun AuxPill(
    label: String,
    active: Boolean,
    accent: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier =
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) accent.copy(alpha = 0.2f) else Color(0xFF14161B).copy(alpha = 0.88f))
            .border(1.dp, if (active) accent else Color(0xFF2D313B), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) accent else Color(0xFFD1D5DB),
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = label,
            color = if (active) accent else Color(0xFFD1D5DB),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun PhotoVideoTogglePill(
    isVideoMode: Boolean,
    supportsVideo: Boolean,
    accent: Color,
    onSelectVideo: (Boolean) -> Unit
) {
    Row(
        modifier =
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF14161B).copy(alpha = 0.92f))
            .border(1.dp, Color(0xFF2D313B), RoundedCornerShape(12.dp))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        val photoSelected = !isVideoMode
        Box(
            modifier =
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (photoSelected) accent.copy(alpha = 0.22f) else Color.Transparent)
                .clickable { onSelectVideo(false) }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = "PHOTO",
                color = if (photoSelected) accent else Color(0xFF9CA3AF),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Box(
            modifier =
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (isVideoMode) Color(0xFFE53935).copy(alpha = 0.28f) else Color.Transparent)
                .alpha(if (supportsVideo) 1f else 0.45f)
                .clickable { onSelectVideo(true) }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = "VIDEO",
                color = if (isVideoMode) Color(0xFFFF6B6B) else Color(0xFF9CA3AF),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun SmallHardwareButton(
    label: String,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Column(
        modifier =
        Modifier
            .width(46.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Color(0xFF14161B).copy(alpha = 0.9f))
            .border(1.dp, if (active) accent else Color(0xFF2D313B), RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        icon()
        Text(
            text = label,
            color = if (active) accent else Color(0xFFB5BAC4),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
internal fun TiltedPhotoCardView(
    thumbnailPath: String?,
    thumbnailModifiedMs: Long,
    thumbnailCache: ThumbnailMemoryCache?,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bitmap by
        produceState<ImageBitmap?>(
            initialValue = null,
            key1 = thumbnailPath,
            key2 = thumbnailModifiedMs
        ) {
            value =
                if (thumbnailPath.isNullOrBlank()) {
                    null
                } else {
                    withContext(Dispatchers.IO) {
                        val file = File(thumbnailPath)
                        if (!file.exists()) return@withContext null
                        val cached = thumbnailCache?.get(file)
                        if (cached != null && !cached.isRecycled) {
                            cached.asImageBitmap()
                        } else {
                            val decoded = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                            if (decoded != null) {
                                thumbnailCache?.put(file, decoded)
                                decoded.asImageBitmap()
                            } else {
                                null
                            }
                        }
                    }
                }
        }

    Column(
        modifier =
        modifier
            .rotate(-4f)
            .shadow(6.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF3EFE6))
            .border(1.dp, Color.Black.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier =
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0xFF1A1C22)),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = "Open Gallery Studio",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.PhotoLibrary,
                    contentDescription = "Open Gallery Studio",
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = "ROLL",
            color = Color(0xFF2B2A27),
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
internal fun InstantEjectCardOverlay(
    card: EjectedPhotoCardState?,
    thumbnailCache: ThumbnailMemoryCache?,
    accent: Color,
    onOpenGallery: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = card != null,
        enter =
        slideInVertically(initialOffsetY = {
            it / 2
        }, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
            fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
        modifier = modifier
    ) {
        if (card != null) {
            Row(
                modifier =
                Modifier
                    .padding(16.dp)
                    .rotate(-4f)
                    .shadow(10.dp, RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF5F1E8))
                    .border(1.dp, accent, RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpenGallery)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TiltedPhotoCardView(
                    thumbnailPath = card.thumbnailPath,
                    thumbnailModifiedMs = card.triggeredAtMs,
                    thumbnailCache = thumbnailCache,
                    accent = accent,
                    onClick = onOpenGallery
                )
                Column {
                    Text(
                        text = card.cameraCodeName,
                        color = Color(0xFF1F1E1C),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = card.label,
                        color = Color(0xFF55524C),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
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
        val boxSize = 52.dp.toPx()
        val half = boxSize / 2f
        drawRect(
            color = accent.copy(alpha = 0.92f),
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
