package com.themasum.aayna.feature.viewfinder

import android.graphics.Color as AndroidColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.themasum.aayna.core.model.CameraSkinSpec
import com.themasum.aayna.core.model.ChassisMaterial
import com.themasum.aayna.core.model.LcdTheme
import com.themasum.aayna.core.model.ShutterStyle
import com.themasum.aayna.core.model.SkinAccentMotif
import com.themasum.aayna.core.model.WhiteBalancePreset
import kotlin.math.cos
import kotlin.math.sin

internal fun parseHexColor(hex: String, fallback: Color = Color(0xFF1C1E24)): Color = runCatching {
    Color(AndroidColor.parseColor(hex))
}.getOrDefault(fallback)

internal fun Modifier.cameraChassisSurface(skin: CameraSkinSpec): Modifier = this.drawWithCache {
    val primary = parseHexColor(skin.bodyPrimaryHex, Color(0xFF1C1E24))
    val secondary = parseHexColor(skin.bodySecondaryHex, Color(0xFF2B2F38))
    val accent = parseHexColor(skin.accentHex, Color(0xFF4CD4E8))

    onDrawBehind {
        drawRect(color = primary)

        when (skin.chassisMaterial) {
            ChassisMaterial.BRUSHED_SILVER,
            ChassisMaterial.CHAMPAGNE_TITANIUM,
            ChassisMaterial.BRASS_RANGEFINDER,
            ChassisMaterial.STUDIO_MONOCHROME -> drawBrushedSilver(primary, secondary)

            ChassisMaterial.ANODIZED_GUNMETAL,
            ChassisMaterial.CHARCOAL_METAL,
            ChassisMaterial.COBALT_METALLIC,
            ChassisMaterial.CINE_ANODIZED,
            ChassisMaterial.SLR_PRISM_GRIP,
            ChassisMaterial.PHOTOBOOTH_BRONZE,
            ChassisMaterial.HANDHELD_ANODIZED -> drawAnodizedGunmetal(primary, secondary)

            ChassisMaterial.RIBBED_PLASTIC,
            ChassisMaterial.Y2K_TRANSLUCENT,
            ChassisMaterial.PEARLESCENT_Y2K,
            ChassisMaterial.VHS_GRAPHITE -> drawRibbedPlastic(primary, secondary)

            ChassisMaterial.WEATHERED_GRUNGE_SLATE -> drawWeatheredGrunge(primary, secondary)

            ChassisMaterial.PEBBLE_LEATHERETTE,
            ChassisMaterial.MATTE_OBSIDIAN,
            ChassisMaterial.COGNAC_LEATHER,
            ChassisMaterial.OLIVE_LEATHER -> drawPebbleLeatherette(primary, secondary)

            ChassisMaterial.WARM_CREAM_INSTANT,
            ChassisMaterial.PASTEL_POLYMER -> drawWarmCreamInstant(primary, secondary)
        }

        drawAccentMotif(skin.accentMotif, secondary, accent)
    }
}

private fun DrawScope.drawBrushedSilver(primary: Color, secondary: Color) {
    drawRect(
        brush =
        Brush.verticalGradient(
            colors =
            listOf(
                primary.copy(alpha = 0.95f),
                Color.White.copy(alpha = 0.08f),
                primary.copy(alpha = 0.92f),
                secondary.copy(alpha = 0.25f)
            )
        )
    )
    val step = 6.dp.toPx()
    var y = 0f
    var idx = 0
    while (y < size.height) {
        val alpha = if (idx % 3 == 0) 0.09f else 0.04f
        val lineColor = if (idx % 2 == 0) Color.White.copy(alpha = alpha) else Color.Black.copy(alpha = alpha)
        drawLine(
            color = lineColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1.dp.toPx()
        )
        y += step
        idx++
    }
}

private fun DrawScope.drawAnodizedGunmetal(primary: Color, secondary: Color) {
    drawRect(
        brush =
        Brush.linearGradient(
            colors = listOf(primary, secondary.copy(alpha = 0.7f), primary),
            start = Offset.Zero,
            end = Offset(size.width, size.height)
        )
    )
    val cell = 12.dp.toPx()
    val cols = (size.width / cell).toInt().coerceAtMost(48)
    val rows = (size.height / cell).toInt().coerceAtMost(72)
    val dotRadius = 0.8.dp.toPx()
    for (r in 0..rows) {
        for (c in 0..cols) {
            val hash = ((r * 73856093) xor (c * 19349663)) and 0xFF
            if (hash < 90) {
                val alpha = 0.03f + (hash / 90f) * 0.05f
                drawCircle(
                    color = if (hash % 2 == 0) Color.White.copy(alpha = alpha) else Color.Black.copy(alpha = alpha),
                    radius = dotRadius,
                    center = Offset(c * cell + (hash % 5), r * cell + (hash % 4))
                )
            }
        }
    }
}

private fun DrawScope.drawRibbedPlastic(primary: Color, secondary: Color) {
    drawRect(color = primary)
    val bandHeight = size.height * 0.14f
    drawRect(
        color = secondary.copy(alpha = 0.92f),
        topLeft = Offset(0f, size.height * 0.08f),
        size = Size(size.width, bandHeight)
    )
    val ribStep = 7.dp.toPx()
    val gripWidth = size.width * 0.16f
    var x = 6.dp.toPx()
    while (x < gripWidth) {
        drawLine(
            color = Color.Black.copy(alpha = 0.35f),
            start = Offset(x, size.height * 0.26f),
            end = Offset(x, size.height * 0.82f),
            strokeWidth = 2.dp.toPx()
        )
        drawLine(
            color = Color.White.copy(alpha = 0.07f),
            start = Offset(x + 2.dp.toPx(), size.height * 0.26f),
            end = Offset(x + 2.dp.toPx(), size.height * 0.82f),
            strokeWidth = 1.dp.toPx()
        )
        x += ribStep
    }
}

private fun DrawScope.drawWeatheredGrunge(primary: Color, secondary: Color) {
    drawRect(
        brush =
        Brush.verticalGradient(
            colors = listOf(primary, secondary, primary.copy(alpha = 0.9f))
        )
    )
    val scuffColor = Color.White.copy(alpha = 0.07f)
    val darkScuff = Color.Black.copy(alpha = 0.18f)
    for (i in 0..14) {
        val startX = ((i * 97) % 100) / 100f * size.width
        val startY = ((i * 53) % 100) / 100f * size.height
        val len = (24 + (i * 11) % 48).dp.toPx()
        drawLine(
            color = if (i % 2 == 0) scuffColor else darkScuff,
            start = Offset(startX, startY),
            end = Offset(startX + len * 0.7f, startY - len * 0.35f),
            strokeWidth = 1.2.dp.toPx()
        )
    }
}

private fun DrawScope.drawPebbleLeatherette(primary: Color, secondary: Color) {
    drawRect(color = primary)
    val cell = 9.dp.toPx()
    val cols = (size.width / cell).toInt().coerceAtMost(56)
    val rows = (size.height / cell).toInt().coerceAtMost(84)
    val pebbleRadius = 2.2.dp.toPx()
    for (r in 0..rows) {
        for (c in 0..cols) {
            val offsetX = if (r % 2 == 0) 0f else cell * 0.5f
            val cx = c * cell + offsetX
            val cy = r * cell
            val shade = (((r * 31 + c * 17) % 5) * 0.015f)
            drawCircle(
                color = Color.White.copy(alpha = 0.03f + shade),
                radius = pebbleRadius,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.25f),
                radius = pebbleRadius,
                center = Offset(cx, cy),
                style = Stroke(width = 0.6.dp.toPx())
            )
        }
    }
    drawRect(
        color = secondary.copy(alpha = 0.18f),
        topLeft = Offset(0f, size.height - 6.dp.toPx()),
        size = Size(size.width, 6.dp.toPx())
    )
}

private fun DrawScope.drawWarmCreamInstant(primary: Color, secondary: Color) {
    drawRect(
        brush =
        Brush.verticalGradient(
            colors = listOf(primary, primary.copy(alpha = 0.96f), secondary.copy(alpha = 0.55f))
        )
    )
    val slotWidth = size.width * 0.62f
    val slotHeight = 7.dp.toPx()
    val slotLeft = (size.width - slotWidth) / 2f
    val slotTop = size.height - 14.dp.toPx()
    drawRoundRect(
        color = Color(0xFF1A1815),
        topLeft = Offset(slotLeft, slotTop),
        size = Size(slotWidth, slotHeight),
        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
    )
}

private fun DrawScope.drawAccentMotif(motif: SkinAccentMotif, secondary: Color, accent: Color) {
    when (motif) {
        SkinAccentMotif.NONE -> Unit

        SkinAccentMotif.CHARCOAL_GRIP -> {
            val gripW = 14.dp.toPx()
            drawRoundRect(
                color = secondary.copy(alpha = 0.92f),
                topLeft = Offset(size.width - gripW - 6.dp.toPx(), size.height * 0.22f),
                size = Size(gripW, size.height * 0.44f),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
            )
        }

        SkinAccentMotif.TITANIUM_BEZEL,
        SkinAccentMotif.BRASS_ENGRAVING -> {
            drawRect(
                color = accent.copy(alpha = 0.22f),
                topLeft = Offset(0f, 0f),
                size = Size(size.width, 3.dp.toPx())
            )
        }

        SkinAccentMotif.RED_ADVANCE_WHEEL,
        SkinAccentMotif.KNURLED_DIAL_RING -> {
            val wheelRadius = 18.dp.toPx()
            val center = Offset(size.width - 14.dp.toPx(), 28.dp.toPx())
            drawCircle(color = accent, radius = wheelRadius, center = center)
            for (angleDeg in 0 until 360 step 30) {
                val rad = Math.toRadians(angleDeg.toDouble())
                val inner = wheelRadius * 0.65f
                drawLine(
                    color = Color.Black.copy(alpha = 0.35f),
                    start =
                    Offset(
                        center.x + (cos(rad) * inner).toFloat(),
                        center.y + (sin(rad) * inner).toFloat()
                    ),
                    end =
                    Offset(
                        center.x + (cos(rad) * wheelRadius).toFloat(),
                        center.y + (sin(rad) * wheelRadius).toFloat()
                    ),
                    strokeWidth = 1.5.dp.toPx()
                )
            }
        }

        SkinAccentMotif.RED_TAPE -> {
            val tapePath =
                Path().apply {
                    moveTo(size.width - 62.dp.toPx(), 0f)
                    lineTo(size.width - 34.dp.toPx(), 0f)
                    lineTo(size.width, 34.dp.toPx())
                    lineTo(size.width, 62.dp.toPx())
                    close()
                }
            drawPath(path = tapePath, color = accent.copy(alpha = 0.88f))
        }

        SkinAccentMotif.SATIN_SILVER_TOP_PLATE -> {
            val topPlateH = 68.dp.toPx()
            drawRect(
                brush =
                Brush.verticalGradient(
                    colors = listOf(secondary, secondary.copy(alpha = 0.85f))
                ),
                topLeft = Offset.Zero,
                size = Size(size.width, topPlateH)
            )
            drawLine(
                color = Color.Black.copy(alpha = 0.45f),
                start = Offset(0f, topPlateH),
                end = Offset(size.width, topPlateH),
                strokeWidth = 1.5.dp.toPx()
            )
        }

        SkinAccentMotif.TRICOLOR_STRIPE,
        SkinAccentMotif.Y2K_RIBBON -> {
            val stripeY = size.height * 0.78f
            val stripeH = 3.dp.toPx()
            val colors = listOf(Color(0xFFE24B2C), Color(0xFFF2B632), Color(0xFF4FB3A5))
            colors.forEachIndexed { index, stripeColor ->
                drawRect(
                    color = stripeColor,
                    topLeft = Offset(0f, stripeY + index * stripeH),
                    size = Size(size.width, stripeH)
                )
            }
        }

        SkinAccentMotif.CINE_TALLY_LAMP -> {
            drawCircle(
                color = accent,
                radius = 4.dp.toPx(),
                center = Offset(size.width - 16.dp.toPx(), 16.dp.toPx())
            )
        }
    }
}

@Composable
internal fun HardwareLcdStrip(
    state: ViewfinderUiState,
    ledFontFamily: FontFamily = FontFamily.Monospace,
    dotMatrixFontFamily: FontFamily = FontFamily.Monospace,
    modifier: Modifier = Modifier
) {
    val skin = state.activeSkin
    val lcdBg = parseHexColor(skin.lcdBackgroundHex, Color(0xFF0B1E24))
    val lcdFg = parseHexColor(skin.lcdForegroundHex, Color(0xFF62F0FF))
    val borderColor = lcdFg.copy(alpha = 0.35f)
    val isoLabel = state.opticalSettings.iso?.toString() ?: "AUTO"
    val shutterLabel = ViewfinderStateReducer.formatShutterLabel(state.opticalSettings.shutterSpeedNs)
    val evLabel = ViewfinderStateReducer.formatEvLabel(state.opticalSettings.evCompensation)
    val wbLabel =
        when (state.opticalSettings.whiteBalance) {
            WhiteBalancePreset.AUTO -> "AWB"
            WhiteBalancePreset.DAYLIGHT -> "DAY"
            WhiteBalancePreset.CLOUDY -> "CLD"
            WhiteBalancePreset.TUNGSTEN -> "TNG"
            WhiteBalancePreset.FLUORESCENT -> "FLR"
        }
    val modeBadge = ViewfinderStateReducer.formatLcdModeBadge(state)
    val lcdFont =
        when (skin.lcdTheme) {
            LcdTheme.EMERALD_SEVEN_SEG,
            LcdTheme.AMBER_COUNTER,
            LcdTheme.CLASSIC_RED_LED -> ledFontFamily
            LcdTheme.CYAN_DOT_MATRIX,
            LcdTheme.WARM_MONOCHROME,
            LcdTheme.VCR_BLUE_OSD -> dotMatrixFontFamily
        }

    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(lcdBg)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        LcdMetric(label = "ISO", value = isoLabel, fg = lcdFg, font = lcdFont)
        LcdMetric(label = "SH", value = shutterLabel, fg = lcdFg, font = lcdFont)
        LcdMetric(label = "EV", value = evLabel, fg = lcdFg, font = lcdFont)
        LcdMetric(label = "WB", value = wbLabel, fg = lcdFg, font = lcdFont)
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier =
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(lcdFg.copy(alpha = 0.16f))
                    .border(1.dp, lcdFg.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = modeBadge,
                    color = lcdFg,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = lcdFont
                )
            }
            if (state.settings.dualSaveEnabled) {
                Box(
                    modifier =
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(lcdFg.copy(alpha = 0.24f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "DUAL",
                        color = lcdFg,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = lcdFont
                    )
                }
            }
        }
    }
}

@Composable
private fun LcdMetric(label: String, value: String, fg: Color, font: FontFamily) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = fg.copy(alpha = 0.62f),
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = font,
            lineHeight = 9.sp
        )
        Text(
            text = value,
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = font,
            lineHeight = 13.sp
        )
    }
}

@Composable
internal fun LayeredPngShutterButton(
    skin: CameraSkinSpec,
    shutterBitmap: ImageBitmap?,
    isCapturing: Boolean,
    isVideoMode: Boolean,
    isRecordingVideo: Boolean,
    countdownSeconds: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed || isCapturing) 0.89f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "layeredShutterScale"
    )
    val pulseTransition = rememberInfiniteTransition(label = "recPulse")
    val recPulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(animation = tween(550), repeatMode = RepeatMode.Reverse),
        label = "recPulseAlpha"
    )

    if (shutterBitmap != null) {
        Box(
            modifier =
            modifier
                .scale(scale)
                .size(74.dp)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = !isCapturing || isRecordingVideo,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = shutterBitmap,
                contentDescription = if (isRecordingVideo) "Stop Recording" else "Shutter",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            if (isRecordingVideo) {
                Box(
                    modifier =
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(0xFFE53935).copy(alpha = recPulseAlpha))
                        .border(1.5.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(7.dp))
                )
            } else if (isVideoMode) {
                Box(
                    modifier =
                    Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE53935))
                        .border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                )
            } else if (countdownSeconds != null) {
                Box(
                    modifier =
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.72f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = countdownSeconds.toString(),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    } else {
        MorphingShutterButton(
            skin = skin,
            isCapturing = isCapturing && !isRecordingVideo,
            isVideoMode = isVideoMode,
            isRecordingVideo = isRecordingVideo,
            countdownSeconds = countdownSeconds,
            onClick = onClick,
            modifier = modifier
        )
    }
}

@Composable
internal fun MorphingShutterButton(
    skin: CameraSkinSpec,
    isCapturing: Boolean,
    countdownSeconds: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isVideoMode: Boolean = false,
    isRecordingVideo: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed || isCapturing) 0.90f else 1f,
        animationSpec = tween(durationMillis = 90),
        label = "shutterScale"
    )

    val shutterColor = parseHexColor(skin.shutterHex, Color(0xFFE4E7EC))
    val accentColor = parseHexColor(skin.accentHex, Color(0xFF4CD4E8))
    val labelColor = parseHexColor(skin.labelHex, Color(0xFF1A1D22))

    val outerShape =
        when (skin.shutterStyle) {
            ShutterStyle.PILL_CHROME -> RoundedCornerShape(28.dp)
            ShutterStyle.CHUNKY_TOGGLE -> RoundedCornerShape(16.dp)
            ShutterStyle.PASTEL_MINT_BUTTON,
            ShutterStyle.PASTEL_BLUSH_BUTTON -> RoundedCornerShape(20.dp)
            ShutterStyle.RECESSED_METAL_RING,
            ShutterStyle.OVERSIZED_ORANGE,
            ShutterStyle.KNURLED_CLASSIC,
            ShutterStyle.BRUSHED_DOME,
            ShutterStyle.CAMCORDER_REC_BUTTON,
            ShutterStyle.BRASS_MECHANICAL -> CircleShape
        }

    val outerWidth = if (skin.shutterStyle == ShutterStyle.PILL_CHROME) 82.dp else 72.dp
    val outerHeight = if (skin.shutterStyle == ShutterStyle.PILL_CHROME) 64.dp else 72.dp
    val effectiveTint =
        when {
            isRecordingVideo -> Color(0xFFE53935)
            isVideoMode -> Color(0xFFE53935).copy(alpha = 0.85f)
            else -> shutterColor
        }

    Box(
        modifier =
        modifier
            .scale(scale)
            .width(outerWidth)
            .height(outerHeight)
            .clip(outerShape)
            .background(Color(0xFF111216))
            .border(2.5.dp, accentColor.copy(alpha = 0.55f), outerShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !isCapturing || isRecordingVideo,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        when (skin.shutterStyle) {
            ShutterStyle.PILL_CHROME -> {
                Box(
                    modifier =
                    Modifier
                        .width(66.dp)
                        .height(48.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.White, effectiveTint, Color(0xFF9BA1AC))
                            )
                        )
                        .border(1.5.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    ShutterCountdownOrDot(countdownSeconds, labelColor, isRecordingVideo)
                }
            }

            ShutterStyle.RECESSED_METAL_RING,
            ShutterStyle.CAMCORDER_REC_BUTTON -> {
                Box(
                    modifier =
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2B303A))
                        .border(2.dp, effectiveTint, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier =
                        Modifier
                            .size(38.dp)
                            .clip(if (isRecordingVideo) RoundedCornerShape(8.dp) else CircleShape)
                            .background(effectiveTint),
                        contentAlignment = Alignment.Center
                    ) {
                        ShutterCountdownOrDot(countdownSeconds, accentColor, isRecordingVideo)
                    }
                }
            }

            ShutterStyle.OVERSIZED_ORANGE -> {
                Box(
                    modifier =
                    Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(effectiveTint)
                        .border(2.dp, Color(0xFFFFB088), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    ShutterCountdownOrDot(countdownSeconds, Color(0xFF1A1412), isRecordingVideo)
                }
            }

            ShutterStyle.CHUNKY_TOGGLE -> {
                Box(
                    modifier =
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(effectiveTint)
                        .border(2.dp, Color(0xFFE8E4D8).copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    ShutterCountdownOrDot(countdownSeconds, Color(0xFFF5EFE6), isRecordingVideo)
                }
            }

            ShutterStyle.KNURLED_CLASSIC,
            ShutterStyle.BRASS_MECHANICAL -> {
                Box(
                    modifier =
                    Modifier
                        .size(60.dp)
                        .drawWithCache {
                            onDrawBehind {
                                val radius = size.minDimension / 2f
                                drawCircle(color = Color(0xFF2C2F36), radius = radius)
                                for (deg in 0 until 360 step 15) {
                                    val rad = Math.toRadians(deg.toDouble())
                                    drawLine(
                                        color = effectiveTint.copy(alpha = 0.75f),
                                        start =
                                        Offset(
                                            center.x + (cos(rad) * (radius - 5.dp.toPx())).toFloat(),
                                            center.y + (sin(rad) * (radius - 5.dp.toPx())).toFloat()
                                        ),
                                        end =
                                        Offset(
                                            center.x + (cos(rad) * radius).toFloat(),
                                            center.y + (sin(rad) * radius).toFloat()
                                        ),
                                        strokeWidth = 1.5.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier =
                        Modifier
                            .size(42.dp)
                            .clip(if (isRecordingVideo) RoundedCornerShape(8.dp) else CircleShape)
                            .background(effectiveTint)
                            .border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        ShutterCountdownOrDot(countdownSeconds, Color(0xFF191A1D), isRecordingVideo)
                    }
                }
            }

            ShutterStyle.PASTEL_MINT_BUTTON,
            ShutterStyle.PASTEL_BLUSH_BUTTON,
            ShutterStyle.BRUSHED_DOME -> {
                Box(
                    modifier =
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(effectiveTint)
                        .border(2.5.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    ShutterCountdownOrDot(countdownSeconds, Color(0xFF1D2B27), isRecordingVideo)
                }
            }
        }
    }
}

@Composable
private fun ShutterCountdownOrDot(countdownSeconds: Int?, tint: Color, isRecordingVideo: Boolean = false) {
    when {
        countdownSeconds != null -> {
            Text(
                text = countdownSeconds.toString(),
                color = tint,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
            )
        }
        isRecordingVideo -> {
            Box(
                modifier =
                Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White)
            )
        }
        else -> {
            Box(
                modifier =
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.75f))
            )
        }
    }
}
