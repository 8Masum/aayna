package com.themasum.aayna.feature.viewfinder

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Flare
import androidx.compose.material.icons.rounded.FlashAuto
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.FlipCameraAndroid
import androidx.compose.material.icons.rounded.GridOff
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.themasum.aayna.core.model.CameraCatalog
import com.themasum.aayna.core.model.CameraProfile
import com.themasum.aayna.core.model.DateStampFormatter
import com.themasum.aayna.core.model.ExposureMath
import com.themasum.aayna.core.model.FlashMode
import com.themasum.aayna.core.model.FrameBorderStyle
import com.themasum.aayna.core.model.LensAttachment
import com.themasum.aayna.core.model.TimerDuration
import com.themasum.aayna.core.model.WhiteBalancePreset

@Composable
internal fun ViewfinderTopDeck(
    state: ViewfinderUiState,
    onCycleFlash: () -> Unit,
    onCycleAspect: () -> Unit,
    onCycleTimer: () -> Unit,
    onToggleGrid: () -> Unit,
    onToggleCameraShelf: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val skin = state.activeCamera.skin
    val accent = parseHexColor(skin.accentHex, Color(0xFF4CD4E8))
    val chipBg = Color(0xFF14161B).copy(alpha = 0.84f)
    val chipBorder = Color(0xFF2D313B)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier =
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(chipBg)
                .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                .clickable(onClick = onToggleCameraShelf)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier =
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Text(
                text = state.activeCamera.codeName,
                color = Color(0xFFF3F4F6),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (flashIcon, flashLabel) =
                when (state.opticalSettings.flashMode) {
                    FlashMode.OFF -> Icons.Rounded.FlashOff to "OFF"
                    FlashMode.AUTO -> Icons.Rounded.FlashAuto to "AUTO"
                    FlashMode.HARDWARE_ON -> Icons.Rounded.FlashOn to "ON"
                    FlashMode.OPTICAL_FLASH_SIM -> Icons.Rounded.Flare to "SIM"
                }
            DeckPillButton(
                label = flashLabel,
                active = state.opticalSettings.flashMode != FlashMode.OFF,
                accent = accent,
                bg = chipBg,
                border = chipBorder,
                onClick = onCycleFlash,
                leadingIcon = {
                    Icon(
                        imageVector = flashIcon,
                        contentDescription = "Flash mode",
                        tint = if (state.opticalSettings.flashMode != FlashMode.OFF) accent else Color(0xFFB5BAC4),
                        modifier = Modifier.size(14.dp)
                    )
                }
            )

            DeckPillButton(
                label = state.opticalSettings.aspectRatio.label,
                active = false,
                accent = accent,
                bg = chipBg,
                border = chipBorder,
                onClick = onCycleAspect
            )

            val timerLabel =
                when (state.timerDuration) {
                    TimerDuration.OFF -> "OFF"
                    TimerDuration.THREE_SECONDS -> "3s"
                    TimerDuration.TEN_SECONDS -> "10s"
                }
            DeckPillButton(
                label = timerLabel,
                active = state.timerDuration != TimerDuration.OFF,
                accent = accent,
                bg = chipBg,
                border = chipBorder,
                onClick = onCycleTimer,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Timer,
                        contentDescription = "Timer",
                        tint = if (state.timerDuration != TimerDuration.OFF) accent else Color(0xFFB5BAC4),
                        modifier = Modifier.size(14.dp)
                    )
                }
            )

            IconButton(
                onClick = onToggleGrid,
                modifier =
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(chipBg)
                    .border(
                        1.dp,
                        if (state.isGridVisible) accent.copy(alpha = 0.65f) else chipBorder,
                        RoundedCornerShape(8.dp)
                    )
            ) {
                Icon(
                    imageVector = if (state.isGridVisible) Icons.Rounded.GridOn else Icons.Rounded.GridOff,
                    contentDescription = "Toggle grid",
                    tint = if (state.isGridVisible) accent else Color(0xFFB5BAC4),
                    modifier = Modifier.size(16.dp)
                )
            }

            IconButton(
                onClick = onOpenSettings,
                modifier =
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(chipBg)
                    .border(1.dp, chipBorder, RoundedCornerShape(8.dp))
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Settings",
                    tint = Color(0xFFE2E5EC),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun DeckPillButton(
    label: String,
    active: Boolean,
    accent: Color,
    bg: Color,
    border: Color,
    onClick: () -> Unit,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    Row(
        modifier =
        Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, if (active) accent.copy(alpha = 0.65f) else border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (leadingIcon != null) {
            leadingIcon()
        }
        Text(
            text = label,
            color = if (active) accent else Color(0xFFDDE1E8),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
internal fun ViewfinderControlsDrawer(
    state: ViewfinderUiState,
    onSelectTab: (ControlTab) -> Unit,
    onStepEv: (Float) -> Unit,
    onSelectIso: (Int?) -> Unit,
    onSelectShutter: (Long?) -> Unit,
    onSelectWhiteBalance: (WhiteBalancePreset) -> Unit,
    onWarmthChange: (Float) -> Unit,
    onBloomChange: (Float) -> Unit,
    onSelectLens: (LensAttachment) -> Unit,
    onToggleLeaksAndDust: () -> Unit,
    onShuffleSeed: () -> Unit,
    onToggleFrame: () -> Unit,
    onToggleStamp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = parseHexColor(state.activeCamera.skin.accentHex, Color(0xFF4CD4E8))

    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF15171C).copy(alpha = 0.94f))
            .border(1.dp, Color(0xFF2D313B), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ControlTabButton(
                label = "EXPOSURE & WB",
                selected = state.activeControlTab == ControlTab.EXPOSURE,
                accent = accent,
                onClick = { onSelectTab(ControlTab.EXPOSURE) },
                modifier = Modifier.weight(1f)
            )
            ControlTabButton(
                label = "OPTICS & STAMP",
                selected = state.activeControlTab == ControlTab.OPTICS,
                accent = accent,
                onClick = { onSelectTab(ControlTab.OPTICS) },
                modifier = Modifier.weight(1f)
            )
        }

        if (state.activeControlTab == ControlTab.EXPOSURE) {
            ExposureControlsContent(
                state = state,
                accent = accent,
                onStepEv = onStepEv,
                onSelectIso = onSelectIso,
                onSelectShutter = onSelectShutter,
                onSelectWhiteBalance = onSelectWhiteBalance,
                onWarmthChange = onWarmthChange
            )
        } else {
            OpticsControlsContent(
                state = state,
                accent = accent,
                onBloomChange = onBloomChange,
                onSelectLens = onSelectLens,
                onToggleLeaksAndDust = onToggleLeaksAndDust,
                onShuffleSeed = onShuffleSeed,
                onToggleFrame = onToggleFrame,
                onToggleStamp = onToggleStamp
            )
        }
    }
}

@Composable
private fun ControlTabButton(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier =
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) accent.copy(alpha = 0.16f) else Color(0xFF1E2129))
            .border(
                1.dp,
                if (selected) accent else Color(0xFF2D313B),
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) accent else Color(0xFFB5BAC4),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ExposureControlsContent(
    state: ViewfinderUiState,
    accent: Color,
    onStepEv: (Float) -> Unit,
    onSelectIso: (Int?) -> Unit,
    onSelectShutter: (Long?) -> Unit,
    onSelectWhiteBalance: (WhiteBalancePreset) -> Unit,
    onWarmthChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "EV ${ViewfinderStateReducer.formatEvLabel(state.opticalSettings.evCompensation)}",
                color = Color(0xFFE5E7EB),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallStepButton(label = "-0.5", accent = accent, onClick = { onStepEv(-0.5f) })
                SmallStepButton(
                    label = "0",
                    accent = accent,
                    onClick = { onStepEv(-state.opticalSettings.evCompensation) }
                )
                SmallStepButton(label = "+0.5", accent = accent, onClick = { onStepEv(0.5f) })
            }
        }

        ControlChipRow(
            title = "ISO",
            items = ExposureMath.STANDARD_ISO_STEPS,
            selectedItem = state.opticalSettings.iso,
            labelOf = { it?.toString() ?: "AUTO" },
            accent = accent,
            onSelect = onSelectIso
        )

        ControlChipRow(
            title = "SHUTTER",
            items = ExposureMath.STANDARD_SHUTTER_PRESETS.map { it.exposureTimeNs },
            selectedItem = state.opticalSettings.shutterSpeedNs,
            labelOf = { ViewfinderStateReducer.formatShutterLabel(it) },
            accent = accent,
            onSelect = onSelectShutter
        )

        ControlChipRow(
            title = "WB",
            items = WhiteBalancePreset.entries,
            selectedItem = state.opticalSettings.whiteBalance,
            labelOf = { it.label.uppercase() },
            accent = accent,
            onSelect = onSelectWhiteBalance
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "WARMTH",
                color = Color(0xFF9CA3AF),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(56.dp)
            )
            Slider(
                value = state.opticalSettings.warmthShift,
                onValueChange = onWarmthChange,
                valueRange = -0.35f..0.35f,
                colors =
                SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    inactiveTrackColor = Color(0xFF2D313B)
                ),
                modifier = Modifier.weight(1f).height(24.dp)
            )
        }
    }
}

@Composable
private fun OpticsControlsContent(
    state: ViewfinderUiState,
    accent: Color,
    onBloomChange: (Float) -> Unit,
    onSelectLens: (LensAttachment) -> Unit,
    onToggleLeaksAndDust: () -> Unit,
    onShuffleSeed: () -> Unit,
    onToggleFrame: () -> Unit,
    onToggleStamp: () -> Unit
) {
    val leaksActive =
        state.opticalSettings.lightLeakIntensity > 0f || state.opticalSettings.dustIntensity > 0f
    val stampPreview =
        DateStampFormatter.format(
            epochMillis = System.currentTimeMillis(),
            format = state.stampConfig.format,
            useEraYearOffset = state.stampConfig.useEraYearOffset,
            eraYear = state.stampConfig.eraYear,
            customText = state.stampConfig.customText
        )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "BLOOM",
                color = Color(0xFF9CA3AF),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(48.dp)
            )
            Slider(
                value = state.opticalSettings.bloomIntensity,
                onValueChange = onBloomChange,
                valueRange = 0f..1f,
                colors =
                SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    inactiveTrackColor = Color(0xFF2D313B)
                ),
                modifier = Modifier.weight(1f).height(24.dp)
            )
        }

        ControlChipRow(
            title = "LENS",
            items = LensAttachment.entries,
            selectedItem = state.opticalSettings.lensAttachment,
            labelOf = {
                when (it) {
                    LensAttachment.STANDARD -> "STD"
                    LensAttachment.FISHEYE -> "FISHEYE"
                    LensAttachment.STAR_4PT -> "4-STAR"
                    LensAttachment.STAR_6PT -> "6-STAR"
                }
            },
            accent = accent,
            onSelect = onSelectLens
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToggleActionPill(
                label = if (leaksActive) "LEAKS ON" else "LEAKS OFF",
                active = leaksActive,
                accent = accent,
                onClick = onToggleLeaksAndDust,
                modifier = Modifier.weight(1f)
            )
            Row(
                modifier =
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E2129))
                    .border(1.dp, Color(0xFF2D313B), RoundedCornerShape(8.dp))
                    .clickable(onClick = onShuffleSeed)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = "Shuffle leak seed",
                    tint = accent,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "SEED",
                    color = Color(0xFFE5E7EB),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val frameLabel =
                when (state.opticalSettings.frameStyle) {
                    FrameBorderStyle.NONE -> "FRAME: OFF"
                    FrameBorderStyle.INSTANT_PRINT -> "FRAME: INSTANT"
                    FrameBorderStyle.FILM_35MM -> "FRAME: 35MM"
                }
            ToggleActionPill(
                label = frameLabel,
                active = state.opticalSettings.frameStyle != FrameBorderStyle.NONE,
                accent = accent,
                onClick = onToggleFrame,
                modifier = Modifier.weight(1f)
            )
            ToggleActionPill(
                label = if (state.stampConfig.enabled) "STAMP: $stampPreview" else "STAMP: OFF",
                active = state.stampConfig.enabled,
                accent = accent,
                onClick = onToggleStamp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SmallStepButton(label: String, accent: Color, onClick: () -> Unit) {
    Box(
        modifier =
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF1F222A))
            .border(1.dp, Color(0xFF313643), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ToggleActionPill(
    label: String,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier =
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) accent.copy(alpha = 0.16f) else Color(0xFF1E2129))
            .border(1.dp, if (active) accent else Color(0xFF2D313B), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (active) accent else Color(0xFF9CA3AF),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun <T> ControlChipRow(
    title: String,
    items: List<T>,
    selectedItem: T,
    labelOf: (T) -> String,
    accent: Color,
    onSelect: (T) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            color = Color(0xFF9CA3AF),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(48.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(items) { item ->
                val isSelected = item == selectedItem
                Box(
                    modifier =
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) accent.copy(alpha = 0.18f) else Color(0xFF1E2129))
                        .border(
                            1.dp,
                            if (isSelected) accent else Color(0xFF2D313B),
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { onSelect(item) }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = labelOf(item),
                        color = if (isSelected) accent else Color(0xFFD1D5DB),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
internal fun CameraShelfDrawer(
    activeCameraId: String,
    onSelectCamera: (CameraProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier =
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF14161B).copy(alpha = 0.94f))
            .border(1.dp, Color(0xFF2D313B), RoundedCornerShape(14.dp))
            .padding(vertical = 10.dp),
        contentPadding = PaddingValues(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(CameraCatalog.all, key = { it.id }) { profile ->
            val selected = profile.id == activeCameraId
            val accent = parseHexColor(profile.skin.accentHex, Color(0xFF4CD4E8))
            val labelColor = parseHexColor(profile.skin.labelHex, Color(0xFFE5E7EB))
            Column(
                modifier =
                Modifier
                    .width(114.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .cameraChassisSurface(profile.skin)
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) accent else Color(0xFF3A3F4B),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelectCamera(profile) }
                    .padding(8.dp),
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
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF101216))
                            .border(2.dp, accent.copy(alpha = 0.75f), CircleShape)
                    )
                    Box(
                        modifier =
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (selected) accent else Color.Transparent)
                    )
                }
                Text(
                    text = profile.codeName,
                    color = labelColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
                Text(
                    text = profile.subtitle,
                    color = labelColor.copy(alpha = 0.78f),
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun ViewfinderBottomDeck(
    state: ViewfinderUiState,
    onOpenGallery: () -> Unit,
    onToggleControls: () -> Unit,
    onShutterClick: () -> Unit,
    onToggleCameraShelf: () -> Unit,
    onFlipCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    val skin = state.activeCamera.skin
    val accent = parseHexColor(skin.accentHex, Color(0xFF4CD4E8))

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DeckActionSquare(
            label = "STUDIO",
            active = false,
            accent = accent,
            onClick = onOpenGallery
        ) {
            Icon(
                imageVector = Icons.Rounded.PhotoLibrary,
                contentDescription = "Open Gallery Studio",
                tint = Color(0xFFE5E7EB),
                modifier = Modifier.size(20.dp)
            )
        }

        DeckActionSquare(
            label = "DIALS",
            active = state.activePanel == ViewfinderPanel.CONTROLS,
            accent = accent,
            onClick = onToggleControls
        ) {
            Icon(
                imageVector = Icons.Rounded.Tune,
                contentDescription = "Toggle manual controls",
                tint = if (state.activePanel == ViewfinderPanel.CONTROLS) accent else Color(0xFFE5E7EB),
                modifier = Modifier.size(20.dp)
            )
        }

        MorphingShutterButton(
            skin = skin,
            isCapturing = state.isCapturing,
            countdownSeconds = state.countdownRemainingSeconds,
            onClick = onShutterClick
        )

        DeckActionSquare(
            label = "CAMS",
            active = state.activePanel == ViewfinderPanel.CAMERA_SHELF,
            accent = accent,
            onClick = onToggleCameraShelf
        ) {
            Icon(
                imageVector = Icons.Rounded.CameraAlt,
                contentDescription = "Toggle camera shelf",
                tint = if (state.activePanel == ViewfinderPanel.CAMERA_SHELF) accent else Color(0xFFE5E7EB),
                modifier = Modifier.size(20.dp)
            )
        }

        DeckActionSquare(
            label = if (state.isFrontCamera) "FRONT" else "REAR",
            active = state.isFrontCamera,
            accent = accent,
            onClick = onFlipCamera
        ) {
            Icon(
                imageVector = Icons.Rounded.FlipCameraAndroid,
                contentDescription = "Flip camera",
                tint = Color(0xFFE5E7EB),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun DeckActionSquare(
    label: String,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Column(
        modifier =
        Modifier
            .width(54.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF14161B).copy(alpha = 0.88f))
            .border(
                1.dp,
                if (active) accent else Color(0xFF2D313B),
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        icon()
        Text(
            text = label,
            color = if (active) accent else Color(0xFFB5BAC4),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
internal fun CameraPermissionCard(
    accent: Color,
    onRequestPermission: () -> Unit,
    onOpenGallery: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier =
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1A1C22))
            .border(1.dp, Color(0xFF2E333E), RoundedCornerShape(16.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.CameraAlt,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(36.dp)
        )
        Text(
            text = "CAMERA ACCESS REQUIRED",
            color = Color(0xFFF3F4F6),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "Aayna needs camera access to run the live OpenGL ES 3.0 optical viewfinder.",
            color = Color(0xFF9CA3AF),
            fontSize = 12.sp
        )
        Button(
            onClick = onRequestPermission,
            colors =
            ButtonDefaults.buttonColors(
                containerColor = accent,
                contentColor = Color(0xFF111317)
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text = "GRANT CAMERA ACCESS",
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
        }
        TextButton(onClick = onOpenGallery) {
            Text(
                text = "OPEN GALLERY STUDIO INSTEAD",
                color = Color(0xFFD1D5DB),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
internal fun DevelopingRitualOverlay(overlay: DevelopingOverlayState?, accent: Color) {
    AnimatedVisibility(
        visible = overlay != null,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        if (overlay != null) {
            Box(
                modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier =
                    Modifier
                        .width(260.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF181A20))
                        .border(1.dp, accent.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "${overlay.cameraCodeName} • WINDING FILM",
                        color = accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "EXP ${overlay.frameNumber.toString().padStart(2, '0')}/${overlay.capacity}",
                        color = Color(0xFFF3F4F6),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                    LinearProgressIndicator(
                        progress = {
                            (overlay.frameNumber.toFloat() / overlay.capacity.coerceAtLeast(1)).coerceIn(0f, 1f)
                        },
                        color = accent,
                        trackColor = Color(0xFF2D313B),
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                    )
                    Text(
                        text = "Developing analog frame...",
                        color = Color(0xFF9CA3AF),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
internal fun RollCompleteDialog(capacity: Int, accent: Color, onLoadNewRoll: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1C22),
        titleContentColor = Color(0xFFF3F4F6),
        textContentColor = Color(0xFFD1D5DB),
        title = {
            Text(
                text = "ROLL COMPLETE ($capacity/$capacity)",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        },
        text = {
            Text(
                text = "All $capacity exposures on this roll have been wound and saved. Load a fresh film roll?",
                fontSize = 13.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onLoadNewRoll,
                colors =
                ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = Color(0xFF111317)
                )
            ) {
                Text(
                    text = "LOAD NEW ROLL",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "LATER",
                    color = Color(0xFF9CA3AF),
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    )
}
