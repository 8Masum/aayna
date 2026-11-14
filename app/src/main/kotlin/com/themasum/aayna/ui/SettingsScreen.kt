package com.themasum.aayna.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.themasum.aayna.BuildConfig
import com.themasum.aayna.core.model.CaptureStorageMode
import com.themasum.aayna.core.model.DateStampFormatter
import com.themasum.aayna.core.model.ResolutionPreset
import com.themasum.aayna.core.model.StampFontStyle
import com.themasum.aayna.core.model.StampFormat
import com.themasum.aayna.core.model.StampPosition
import com.themasum.aayna.core.model.StampStyle
import com.themasum.aayna.core.model.UserSettingsSnapshot
import com.themasum.aayna.core.storage.AaynaPreferencesRepository
import com.themasum.aayna.core.storage.AaynaSoundEngine
import com.themasum.aayna.ui.theme.AaynaColors
import com.themasum.aayna.ui.theme.AaynaFonts
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val rollCapacities = listOf(24, 36)

@Composable
fun SettingsScreen(
    preferencesRepository: AaynaPreferencesRepository,
    soundEngine: AaynaSoundEngine,
    onBack: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by preferencesRepository.settingsFlow.collectAsStateWithLifecycle(
        initialValue = UserSettingsSnapshot()
    )
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    fun feedbackTick() {
        soundEngine.playDialTick(settings.sfxEnabled)
        if (settings.hapticsEnabled) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    Column(
        modifier =
        modifier
            .fillMaxSize()
            .background(AaynaColors.CharcoalBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        ConsoleTopBar(
            title = "CONSOLE SETTINGS",
            onBack = {
                feedbackTick()
                onBack()
            }
        )

        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ConsoleSection(title = "CAPTURE STORAGE MODE") {
                CaptureStorageMode.entries.forEach { mode ->
                    StorageModeCard(
                        title = SettingsLabelFormatter.storageModeTitle(mode),
                        subtitle = SettingsLabelFormatter.storageModeSubtitle(mode),
                        selected = settings.storageMode == mode,
                        onClick = {
                            feedbackTick()
                            scope.launch { preferencesRepository.setStorageMode(mode) }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text =
                    if (settings.storageMode == CaptureStorageMode.ANALOG_DEVELOPING_RITUAL) {
                        "FILM ROLL CAPACITY"
                    } else {
                        "FILM ROLL CAPACITY (ANALOG RITUAL)"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = AaynaColors.MutedText
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rollCapacities.forEach { capacity ->
                        HardwareChip(
                            label = "$capacity EXP",
                            selected = settings.rollCapacity == capacity,
                            onClick = {
                                feedbackTick()
                                scope.launch { preferencesRepository.setRollCapacity(capacity) }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                HorizontalDivider(color = AaynaColors.MetallicBorder)

                ConsoleSwitchRow(
                    title = "Dual-Save Clean Negative",
                    subtitle = "Save clean pre-grade original alongside stylized shot.",
                    checked = settings.dualSaveEnabled,
                    onCheckedChange = { enabled ->
                        feedbackTick()
                        scope.launch { preferencesRepository.setDualSaveEnabled(enabled) }
                    }
                )
            }

            ConsoleSection(title = "CAMERA & EXPORT PIPELINE") {
                Text(
                    text = "OUTPUT RESOLUTION",
                    style = MaterialTheme.typography.labelMedium,
                    color = AaynaColors.MutedText
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ResolutionPreset.entries.forEach { preset ->
                        HardwareChip(
                            label = SettingsLabelFormatter.resolutionPresetLabel(preset),
                            selected = settings.resolutionPreset == preset,
                            onClick = {
                                feedbackTick()
                                scope.launch { preferencesRepository.setResolutionPreset(preset) }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "JPEG EXPORT QUALITY",
                            style = MaterialTheme.typography.labelMedium,
                            color = AaynaColors.MutedText
                        )
                        Text(
                            text = SettingsLabelFormatter.jpegQualityLabel(settings.jpegQuality),
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = AaynaFonts.LedSevenSegment,
                            color = AaynaColors.AmberLed
                        )
                    }
                    val minQ = UserSettingsSnapshot.MIN_JPEG_QUALITY
                    val maxQ = UserSettingsSnapshot.MAX_JPEG_QUALITY
                    Slider(
                        value = settings.clampedJpegQuality().toFloat(),
                        onValueChange = { quality ->
                            scope.launch {
                                preferencesRepository.setJpegQuality(quality.roundToInt())
                            }
                        },
                        valueRange = minQ.toFloat()..maxQ.toFloat(),
                        steps = maxQ - minQ - 1,
                        colors =
                        SliderDefaults.colors(
                            thumbColor = AaynaColors.AmberLed,
                            activeTrackColor = AaynaColors.AmberLed,
                            inactiveTrackColor = AaynaColors.MetallicBorder
                        )
                    )
                }

                HorizontalDivider(color = AaynaColors.MetallicBorder)

                ConsoleSwitchRow(
                    title = "Front Camera Mirror",
                    subtitle = "Horizontally flip front-facing captures to match viewfinder preview.",
                    checked = settings.isFrontMirrorEnabled,
                    onCheckedChange = { enabled ->
                        feedbackTick()
                        scope.launch { preferencesRepository.setFrontMirrorEnabled(enabled) }
                    }
                )

                HorizontalDivider(color = AaynaColors.MetallicBorder)

                ConsoleSwitchRow(
                    title = "Hardware AE Compatibility Mode",
                    subtitle = "Disable manual AE_MODE_OFF override and clamp EV steps on quirky HALs.",
                    checked = settings.useHardwareAeCompatibility,
                    onCheckedChange = { enabled ->
                        feedbackTick()
                        scope.launch { preferencesRepository.setHardwareAeCompatibility(enabled) }
                    }
                )
            }

            ConsoleSection(title = "DATE STAMP") {
                val previewText =
                    remember(settings.stampFormat, settings.useEraYearOffset) {
                        DateStampFormatter.format(
                            date = LocalDate.now(),
                            format = settings.stampFormat,
                            useEraYearOffset = settings.useEraYearOffset,
                            eraYear = 2004,
                            customText = if (settings.stampFormat == StampFormat.CUSTOM) "AAYNA '04" else ""
                        )
                    }
                StampLedPreview(
                    text = previewText,
                    colorHex = settings.stampColor.hexColor,
                    fontFamily = AaynaFonts.forStampFontStyle(settings.stampFontStyle),
                    position = settings.stampPosition,
                    glowStrength = settings.stampGlow
                )

                Text(
                    text = "DATE FORMAT",
                    style = MaterialTheme.typography.labelMedium,
                    color = AaynaColors.MutedText
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StampFormat.entries.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { format ->
                                HardwareChip(
                                    label = SettingsLabelFormatter.stampFormatLabel(format),
                                    selected = settings.stampFormat == format,
                                    onClick = {
                                        feedbackTick()
                                        scope.launch { preferencesRepository.setStampFormat(format) }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                ConsoleSwitchRow(
                    title = "Era-Year Offset",
                    subtitle = "Use camera's nostalgic era year instead of today's real date.",
                    checked = settings.useEraYearOffset,
                    onCheckedChange = { enabled ->
                        feedbackTick()
                        scope.launch { preferencesRepository.setUseEraYearOffset(enabled) }
                    }
                )

                Text(
                    text = "STAMP FONT",
                    style = MaterialTheme.typography.labelMedium,
                    color = AaynaColors.MutedText
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StampFontStyle.entries.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { fontStyle ->
                                HardwareChip(
                                    label = SettingsLabelFormatter.stampFontLabel(fontStyle),
                                    selected = settings.stampFontStyle == fontStyle,
                                    fontFamily = AaynaFonts.forStampFontStyle(fontStyle),
                                    onClick = {
                                        feedbackTick()
                                        scope.launch { preferencesRepository.setStampFontStyle(fontStyle) }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowItems.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                Text(
                    text = "STAMP POSITION",
                    style = MaterialTheme.typography.labelMedium,
                    color = AaynaColors.MutedText
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StampPosition.entries.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { position ->
                                HardwareChip(
                                    label = SettingsLabelFormatter.stampPositionLabel(position),
                                    selected = settings.stampPosition == position,
                                    onClick = {
                                        feedbackTick()
                                        scope.launch { preferencesRepository.setStampPosition(position) }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "LED COLOR",
                    style = MaterialTheme.typography.labelMedium,
                    color = AaynaColors.MutedText
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StampStyle.entries.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { style ->
                                StampColorChip(
                                    label = SettingsLabelFormatter.stampColorLabel(style),
                                    dotColor = parseHexColor(style.hexColor),
                                    selected = settings.stampColor == style,
                                    onClick = {
                                        feedbackTick()
                                        scope.launch { preferencesRepository.setStampColor(style) }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowItems.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "GLOW INTENSITY",
                            style = MaterialTheme.typography.labelMedium,
                            color = AaynaColors.MutedText
                        )
                        Text(
                            text = "${(settings.stampGlow * 100f).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = AaynaFonts.LedSevenSegment,
                            color = AaynaColors.AmberLed
                        )
                    }
                    Slider(
                        value = settings.stampGlow,
                        onValueChange = { glow ->
                            scope.launch { preferencesRepository.setStampGlow(glow) }
                        },
                        valueRange = 0f..1f,
                        colors =
                        SliderDefaults.colors(
                            thumbColor = AaynaColors.AmberLed,
                            activeTrackColor = AaynaColors.AmberLed,
                            inactiveTrackColor = AaynaColors.MetallicBorder
                        )
                    )
                }
            }

            ConsoleSection(title = "TACTILE & VIEWFINDER") {
                ConsoleSwitchRow(
                    title = "Mechanical Haptics",
                    subtitle = "Detent vibration on shutter release and hardware dials.",
                    checked = settings.hapticsEnabled,
                    onCheckedChange = { enabled ->
                        soundEngine.playDialTick(settings.sfxEnabled)
                        scope.launch { preferencesRepository.setHapticsEnabled(enabled) }
                    }
                )
                HorizontalDivider(color = AaynaColors.MetallicBorder)
                ConsoleSwitchRow(
                    title = "Shutter & Dial SFX",
                    subtitle = "Authentic camera shutter clicks and film wind audio.",
                    checked = settings.sfxEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            soundEngine.playDialTick(true)
                        }
                        if (settings.hapticsEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        scope.launch { preferencesRepository.setSfxEnabled(enabled) }
                    }
                )
                HorizontalDivider(color = AaynaColors.MetallicBorder)
                ConsoleSwitchRow(
                    title = "Default Composition Grid",
                    subtitle = "Show 3x3 framing reticle on viewfinder startup.",
                    checked = settings.gridEnabled,
                    onCheckedChange = { enabled ->
                        feedbackTick()
                        scope.launch { preferencesRepository.setGridEnabled(enabled) }
                    }
                )
            }

            ConsoleSection(title = "SYSTEM") {
                OutlinedButton(
                    onClick = {
                        feedbackTick()
                        onOpenDiagnostics()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, AaynaColors.CyanLed.copy(alpha = 0.65f)),
                    colors =
                    ButtonDefaults.outlinedButtonColors(
                        containerColor = AaynaColors.CharcoalElevated,
                        contentColor = AaynaColors.CyanLed
                    )
                ) {
                    Text(
                        text = "ON-DEVICE CAMERA2, QUIRKS & GL DIAGNOSTICS",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }

                Text(
                    text = "AAYNA OPTICAL CONSOLE • v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = AaynaFonts.LedSevenSegment,
                    color = AaynaColors.MutedText
                )
            }
        }
    }
}

@Composable
internal fun ConsoleTopBar(title: String, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .background(AaynaColors.CharcoalSurface)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onBack,
                shape = RoundedCornerShape(6.dp),
                color = AaynaColors.CharcoalElevated,
                border = BorderStroke(1.dp, AaynaColors.MetallicBorder)
            ) {
                Text(
                    text = "< BACK",
                    style = MaterialTheme.typography.labelMedium,
                    color = AaynaColors.SilverText,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = AaynaColors.SilverText,
                letterSpacing = 1.2.sp
            )
        }
        HorizontalDivider(color = AaynaColors.MetallicBorder)
    }
}

@Composable
internal fun ConsoleSection(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = AaynaColors.CharcoalSurface,
        border = BorderStroke(1.dp, AaynaColors.MetallicBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = AaynaColors.AmberLed
            )
            content()
        }
    }
}

@Composable
private fun StorageModeCard(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) AaynaColors.AmberLed else AaynaColors.MetallicBorder
    val bgColor = if (selected) AaynaColors.CharcoalElevated else AaynaColors.CharcoalBg

    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier =
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (selected) AaynaColors.AmberLed else AaynaColors.MetallicBorder)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) AaynaColors.SilverText else AaynaColors.SilverText.copy(alpha = 0.85f)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = AaynaColors.MutedText
            )
        }
    }
}

@Composable
private fun HardwareChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontFamily: FontFamily = AaynaFonts.HardwareCondensed
) {
    val borderColor = if (selected) AaynaColors.AmberLed else AaynaColors.MetallicBorder
    val bgColor = if (selected) AaynaColors.CharcoalElevated else AaynaColors.CharcoalBg

    Box(
        modifier =
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = fontFamily,
            color = if (selected) AaynaColors.AmberLed else AaynaColors.SilverText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StampColorChip(
    label: String,
    dotColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) dotColor else AaynaColors.MetallicBorder
    val bgColor = if (selected) AaynaColors.CharcoalElevated else AaynaColors.CharcoalBg

    Row(
        modifier =
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier =
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) AaynaColors.SilverText else AaynaColors.MutedText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StampLedPreview(
    text: String,
    colorHex: String,
    fontFamily: FontFamily,
    position: StampPosition,
    glowStrength: Float
) {
    val ledColor = parseHexColor(colorHex)
    val alignment =
        when (position) {
            StampPosition.TOP_LEFT -> Alignment.TopStart
            StampPosition.TOP_RIGHT -> Alignment.TopEnd
            StampPosition.BOTTOM_LEFT -> Alignment.BottomStart
            StampPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
        }

    Box(
        modifier =
        Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(AaynaColors.CharcoalRecessed)
            .border(1.dp, AaynaColors.MetallicBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = alignment
    ) {
        Text(
            text = text,
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            letterSpacing = 2.sp,
            color = ledColor.copy(alpha = (0.72f + glowStrength * 0.28f).coerceIn(0f, 1f))
        )
    }
}

@Composable
private fun ConsoleSwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = AaynaColors.SilverText
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = AaynaColors.MutedText
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors =
            SwitchDefaults.colors(
                checkedThumbColor = AaynaColors.CharcoalBg,
                checkedTrackColor = AaynaColors.AmberLed,
                uncheckedThumbColor = AaynaColors.MutedText,
                uncheckedTrackColor = AaynaColors.CharcoalBg,
                uncheckedBorderColor = AaynaColors.MetallicBorder
            )
        )
    }
}

private fun parseHexColor(hex: String): Color = runCatching { Color(android.graphics.Color.parseColor(hex)) }
    .getOrDefault(AaynaColors.AmberLed)
