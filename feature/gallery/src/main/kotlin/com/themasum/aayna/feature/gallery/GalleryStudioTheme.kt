package com.themasum.aayna.feature.gallery

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

internal object ProCcdPalette {
    val VoidObsidian = Color(0xFF191818)
    val HardwareSlate = Color(0xFF242222)
    val CreamInk = Color(0xFFF2EDE4)
    val WarmAmber = Color(0xFFFFB347)

    val BgCharcoal = VoidObsidian
    val SurfaceDark = HardwareSlate
    val SurfaceElevated = Color(0xFF302D2D)
    val SurfaceRecessed = Color(0xFF121111)
    val BorderMetallic = Color(0xFF3E3A3A)
    val BorderHighlight = Color(0xFF5A5454)
    val TextPrimary = CreamInk
    val TextSecondary = Color(0xFFB7B0A5)
    val TextMuted = Color(0xFF817A70)
    val LedAmber = WarmAmber
    val LedCyan = Color(0xFF4CD4E8)
    val LedEmerald = Color(0xFF39FFAE)
    val LedRed = Color(0xFFFF453A)

    val MetallicBezelBrush =
        Brush.verticalGradient(
            colors = listOf(Color(0xFF322F2F), HardwareSlate)
        )
}

internal data class ProCcdFontSet(
    val led: FontFamily = FontFamily.Monospace,
    val dotMatrix: FontFamily = FontFamily.Monospace,
    val vcrOsd: FontFamily = FontFamily.Monospace,
    val pixel: FontFamily = FontFamily.Monospace,
    val condensed: FontFamily = FontFamily.Monospace
)

internal val LocalProCcdFonts = compositionLocalOf { ProCcdFontSet() }

@Composable
internal fun LedStatusPill(
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    filledDot: Boolean = true
) {
    val fonts = LocalProCcdFonts.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = accentColor.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (filledDot) {
                Box(
                    modifier =
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
            }
            Text(
                text = label,
                color = accentColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = fonts.led,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun HardwareChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = ProCcdPalette.WarmAmber
) {
    val fonts = LocalProCcdFonts.current
    val bgColor = if (selected) accentColor.copy(alpha = 0.18f) else ProCcdPalette.SurfaceElevated
    val borderColor = if (selected) accentColor else ProCcdPalette.BorderMetallic
    val textColor = if (selected) accentColor else ProCcdPalette.TextSecondary

    Box(
        modifier =
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontFamily = fonts.condensed,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun HardwareSliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = ProCcdPalette.WarmAmber
) {
    val fonts = LocalProCcdFonts.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = ProCcdPalette.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = fonts.condensed
            )
            Text(
                text = valueText,
                color = accentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = fonts.led
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors =
            SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = ProCcdPalette.BorderMetallic
            ),
            modifier = Modifier.height(28.dp)
        )
    }
}

@Composable
internal fun HardwareSectionLabel(text: String, modifier: Modifier = Modifier) {
    val fonts = LocalProCcdFonts.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier =
            Modifier
                .width(3.dp)
                .height(12.dp)
                .background(ProCcdPalette.WarmAmber, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            color = ProCcdPalette.TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = fonts.condensed
        )
    }
}

/**
 * Tactile ProCCD notched ruler dial for precision scrubbing (EV, Straighten, Tone, etc.).
 * Double-tap resets to [resetValue]; horizontal drag fires [onTick] on every quantized notch.
 */
@Composable
internal fun NotchedRulerDial(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    stepSize: Float,
    resetValue: Float = 0f,
    hapticsEnabled: Boolean = true,
    accentColor: Color = ProCcdPalette.WarmAmber,
    onTick: () -> Unit,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val fonts = LocalProCcdFonts.current
    val haptics = LocalHapticFeedback.current
    val latestValue by rememberUpdatedState(value)
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnTick by rememberUpdatedState(onTick)
    val latestHapticsEnabled by rememberUpdatedState(hapticsEnabled)

    val safeStep = stepSize.coerceAtLeast(0.001f)
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(safeStep)
    val clampedValue = value.coerceIn(valueRange.start, valueRange.endInclusive)
    val currentNotch = ((clampedValue - valueRange.start) / safeStep).roundToInt()

    var dragAccumulatedValue by remember { mutableFloatStateOf(clampedValue) }
    var lastEmittedNotch by remember { mutableIntStateOf(currentNotch) }

    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ProCcdPalette.SurfaceRecessed)
            .border(1.dp, ProCcdPalette.BorderMetallic, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = ProCcdPalette.TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = fonts.condensed
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = valueText,
                    color = accentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fonts.led
                )
                if (abs(clampedValue - resetValue) > safeStep * 0.4f) {
                    Text(
                        text = "RESET",
                        color = ProCcdPalette.TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fonts.condensed,
                        modifier =
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(ProCcdPalette.SurfaceElevated)
                            .clickable {
                                latestOnTick()
                                if (latestHapticsEnabled) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                latestOnValueChange(resetValue.coerceIn(valueRange.start, valueRange.endInclusive))
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Canvas(
            modifier =
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .pointerInput(valueRange, safeStep) {
                    detectTapGestures(
                        onDoubleTap = {
                            latestOnTick()
                            if (latestHapticsEnabled) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            latestOnValueChange(resetValue.coerceIn(valueRange.start, valueRange.endInclusive))
                        }
                    )
                }
                .pointerInput(valueRange, safeStep) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragAccumulatedValue = latestValue.coerceIn(valueRange.start, valueRange.endInclusive)
                            lastEmittedNotch = ((dragAccumulatedValue - valueRange.start) / safeStep).roundToInt()
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            val widthPx = size.width.coerceAtLeast(1).toFloat()
                            val deltaVal = -(dragAmount / widthPx) * span * 1.15f
                            dragAccumulatedValue =
                                (dragAccumulatedValue + deltaVal).coerceIn(
                                    valueRange.start,
                                    valueRange.endInclusive
                                )
                            val notch = ((dragAccumulatedValue - valueRange.start) / safeStep).roundToInt()
                            val snapped =
                                (valueRange.start + notch * safeStep).coerceIn(
                                    valueRange.start,
                                    valueRange.endInclusive
                                )
                            if (notch != lastEmittedNotch) {
                                lastEmittedNotch = notch
                                latestOnTick()
                                if (latestHapticsEnabled) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                latestOnValueChange(snapped)
                            }
                        }
                    )
                }
        ) {
            val w = size.width
            val h = size.height
            val centerX = w * 0.5f
            val totalTicks = (span / safeStep).roundToInt().coerceIn(8, 120)
            val tickSpacingPx = w / 22f
            val normalizedPos = (clampedValue - valueRange.start) / span
            val activeTickFloat = normalizedPos * totalTicks

            for (i in 0..totalTicks) {
                val dx = (i - activeTickFloat) * tickSpacingPx
                val x = centerX + dx
                if (x < 0f || x > w) continue
                val isMajor = i % 5 == 0
                val isZeroTick = abs((valueRange.start + i * safeStep) - resetValue) < safeStep * 0.4f
                val tickHeight =
                    when {
                        isZeroTick -> h * 0.78f
                        isMajor -> h * 0.62f
                        else -> h * 0.36f
                    }
                val distRatio = (abs(dx) / (w * 0.5f)).coerceIn(0f, 1f)
                val alpha = (1f - distRatio * 0.65f).coerceIn(0.2f, 1f)
                val tickColor =
                    when {
                        isZeroTick -> ProCcdPalette.LedCyan.copy(alpha = alpha)
                        isMajor -> ProCcdPalette.CreamInk.copy(alpha = alpha)
                        else -> ProCcdPalette.TextMuted.copy(alpha = alpha)
                    }
                drawLine(
                    color = tickColor,
                    start = Offset(x, h - tickHeight),
                    end = Offset(x, h),
                    strokeWidth = if (isZeroTick || isMajor) 2.dp.toPx() else 1.dp.toPx()
                )
            }

            // Center amber indicator needle
            drawLine(
                color = accentColor,
                start = Offset(centerX, 0f),
                end = Offset(centerX, h),
                strokeWidth = 2.5.dp.toPx()
            )
            drawCircle(
                color = accentColor,
                radius = 3.5.dp.toPx(),
                center = Offset(centerX, 4.dp.toPx())
            )
        }
    }
}
