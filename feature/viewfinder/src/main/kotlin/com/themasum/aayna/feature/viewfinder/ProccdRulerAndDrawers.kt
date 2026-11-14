package com.themasum.aayna.feature.viewfinder

import android.content.res.AssetManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.themasum.aayna.core.model.AspectRatioPreset
import com.themasum.aayna.core.model.CameraCatalog
import com.themasum.aayna.core.model.CameraCategory
import com.themasum.aayna.core.model.CameraProfile
import com.themasum.aayna.core.model.DateStampFormatter
import com.themasum.aayna.core.model.ExposureMath
import com.themasum.aayna.core.model.FrameBorderStyle
import com.themasum.aayna.core.model.LensAttachment
import com.themasum.aayna.core.model.SkinCatalog
import com.themasum.aayna.core.model.StampFontStyle
import com.themasum.aayna.core.model.StampFormat
import com.themasum.aayna.core.model.StampPosition
import com.themasum.aayna.core.model.StampStyle
import com.themasum.aayna.core.model.WhiteBalancePreset
import kotlin.math.roundToInt

@Composable
internal fun ProccdNotchedRuler(
    state: ViewfinderUiState,
    onSelectParameter: (RulerParameter) -> Unit,
    onValueChange: (RulerParameter, Float) -> Unit,
    onResetParameter: (RulerParameter) -> Unit,
    onNotchTick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = parseHexColor(state.activeSkin.accentHex, Color(0xFF4CD4E8))
    val activeParam = state.activeRulerParameter
    val spec = remember(activeParam) { ProccdRulerMath.specFor(activeParam) }
    val currentValue = ViewfinderStateReducer.currentRulerValue(state, activeParam)
    val currentNotch = ProccdRulerMath.valueToNotchIndex(spec, currentValue)

    var lastEmittedNotch by remember(activeParam) { mutableIntStateOf(currentNotch) }
    var dragAccumPx by remember(activeParam) { mutableFloatStateOf(0f) }

    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier =
                Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                RulerParameter.entries.forEach { param ->
                    val selected = param == activeParam
                    Box(
                        modifier =
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (selected) accent.copy(alpha = 0.22f) else Color(0xFF14161B).copy(alpha = 0.8f)
                            )
                            .border(
                                1.dp,
                                if (selected) accent else Color(0xFF2D313B),
                                RoundedCornerShape(6.dp)
                            )
                            .clickable {
                                onNotchTick()
                                onSelectParameter(param)
                            }
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = param.shortLabel,
                            color = if (selected) accent else Color(0xFFAEB4C0),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier =
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF14161B).copy(alpha = 0.85f))
                    .clickable {
                        onNotchTick()
                        onResetParameter(activeParam)
                    }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = ProccdRulerMath.formatValue(activeParam, currentValue),
                    color = accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Icon(
                    imageVector = Icons.Rounded.RestartAlt,
                    contentDescription = "Reset ${activeParam.shortLabel}",
                    tint = Color(0xFF9CA3AF),
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        // Tactile notched ruler strip (21 / 41 notches, center needle)
        Box(
            modifier =
            Modifier
                .fillMaxWidth()
                .height(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0E1015).copy(alpha = 0.88f))
                .border(1.dp, Color(0xFF2A2E39), RoundedCornerShape(8.dp))
                .pointerInput(activeParam, spec) {
                    detectTapGestures(
                        onTap = { tap ->
                            val center = size.width / 2f
                            val pxPerNotch = (size.width / 22f).coerceAtLeast(12f)
                            val deltaNotches = ((tap.x - center) / pxPerNotch).roundToInt()
                            if (deltaNotches != 0) {
                                val nextValue = ProccdRulerMath.stepByNotches(spec, currentValue, deltaNotches)
                                val nextIdx = ProccdRulerMath.valueToNotchIndex(spec, nextValue)
                                if (nextIdx != lastEmittedNotch) {
                                    lastEmittedNotch = nextIdx
                                    onNotchTick()
                                }
                                onValueChange(activeParam, nextValue)
                            }
                        }
                    )
                }
                .pointerInput(activeParam, spec) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragAccumPx = 0f
                            lastEmittedNotch = currentNotch
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val pxPerNotch = (size.width / 22f).coerceAtLeast(10f)
                            dragAccumPx += dragAmount
                            val notchSteps = (dragAccumPx / pxPerNotch).toInt()
                            if (notchSteps != 0) {
                                dragAccumPx -= notchSteps * pxPerNotch
                                val targetIdx =
                                    (lastEmittedNotch - notchSteps).coerceIn(0, spec.notchCount - 1)
                                if (targetIdx != lastEmittedNotch) {
                                    lastEmittedNotch = targetIdx
                                    onNotchTick()
                                    onValueChange(
                                        activeParam,
                                        ProccdRulerMath.notchIndexToValue(spec, targetIdx)
                                    )
                                }
                            }
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width / 2f
                val spacing = (size.width / 22f).coerceAtLeast(10f)
                for (i in 0 until spec.notchCount) {
                    val x = centerX + (i - currentNotch) * spacing
                    if (x < 4f || x > size.width - 4f) continue
                    val isMajor = i % spec.majorStepNotches == 0
                    val tickHeight = if (isMajor) size.height * 0.56f else size.height * 0.3f
                    val topY = (size.height - tickHeight) / 2f
                    val tickColor =
                        if (isMajor) Color.White.copy(alpha = 0.78f) else Color.White.copy(alpha = 0.34f)
                    drawLine(
                        color = tickColor,
                        start = Offset(x, topY),
                        end = Offset(x, topY + tickHeight),
                        strokeWidth = if (isMajor) 1.7.dp.toPx() else 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                // Fixed center tactile needle
                drawLine(
                    color = accent,
                    start = Offset(centerX, 2.dp.toPx()),
                    end = Offset(centerX, size.height - 2.dp.toPx()),
                    strokeWidth = 2.4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
internal fun ProccdOverlayDrawerContainer(
    title: String,
    accent: Color,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            .background(Color(0xFF13151A).copy(alpha = 0.96f))
            .border(
                1.dp,
                Color(0xFF2E323D),
                RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
            )
            IconButton(onClick = onClose, modifier = Modifier.size(26.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close drawer",
                    tint = Color(0xFFB5BAC4),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        content()
    }
}

@Composable
internal fun ProccdCameraShelfDrawer(
    state: ViewfinderUiState,
    manifest: ParsedSkinManifest,
    bitmapCache: SkinBitmapCache,
    assetManager: AssetManager,
    onSelectCamera: (CameraProfile) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onSelectCategory: (CameraCategory?) -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onOpenSampleSpecs: (CameraProfile) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = parseHexColor(state.activeSkin.accentHex, Color(0xFF4CD4E8))
    val filteredCameras =
        remember(state.cameraShelfCategory, state.cameraShelfFavoritesOnly, state.favoriteCameraIds) {
            when {
                state.cameraShelfFavoritesOnly ->
                    CameraCatalog.all.filter { it.id in state.favoriteCameraIds }
                state.cameraShelfCategory != null ->
                    CameraCatalog.byCategory(state.cameraShelfCategory)
                else -> CameraCatalog.all
            }
        }

    ProccdOverlayDrawerContainer(
        title = "CAMERA SHELF (${filteredCameras.size}/${CameraCatalog.all.size})",
        accent = accent,
        onClose = onClose,
        modifier = modifier
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ShelfFilterChip(
                label = "ALL",
                selected = !state.cameraShelfFavoritesOnly && state.cameraShelfCategory == null,
                accent = accent,
                onClick = { onSelectCategory(null) }
            )
            ShelfFilterChip(
                label = "FAVS (${state.favoriteCameraIds.size})",
                selected = state.cameraShelfFavoritesOnly,
                accent = accent,
                onClick = onToggleFavoritesOnly
            )
            CameraCategory.entries.forEach { category ->
                ShelfFilterChip(
                    label = category.label.uppercase(),
                    selected = !state.cameraShelfFavoritesOnly && state.cameraShelfCategory == category,
                    accent = accent,
                    onClick = { onSelectCategory(category) }
                )
            }
        }

        if (filteredCameras.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(110.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Tap the star on any camera to add it to Favorites",
                    color = Color(0xFF9CA3AF),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(filteredCameras, key = { it.id }) { profile ->
                    val selected = profile.id == state.activeCamera.id
                    val isFav = profile.id in state.favoriteCameraIds
                    val camAccent = parseHexColor(profile.skin.accentHex, accent)
                    val iconBitmap =
                        rememberSkinImageBitmap(
                            assetPath = manifest.iconPathForCamera(profile.id),
                            cache = bitmapCache,
                            assetManager = assetManager
                        )

                    Column(
                        modifier =
                        Modifier
                            .width(120.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) camAccent.copy(alpha = 0.14f) else Color(0xFF1A1D24))
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) camAccent else Color(0xFF2E333E),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectCamera(profile) }
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isFav) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                contentDescription = "Favorite",
                                tint = if (isFav) Color(0xFFFFD54F) else Color(0xFF8B92A1),
                                modifier =
                                Modifier
                                    .size(16.dp)
                                    .clickable { onToggleFavorite(profile.id) }
                            )
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = "Specs",
                                tint = Color(0xFF9CA3AF),
                                modifier =
                                Modifier
                                    .size(16.dp)
                                    .clickable { onOpenSampleSpecs(profile) }
                            )
                        }
                        Box(
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (iconBitmap != null) {
                                Image(
                                    bitmap = iconBitmap,
                                    contentDescription = profile.codeName,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.size(46.dp)
                                )
                            } else {
                                Box(
                                    modifier =
                                    Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(parseHexColor(profile.skin.bodyPrimaryHex))
                                        .border(2.dp, camAccent, CircleShape)
                                )
                            }
                        }
                        Text(
                            text = profile.codeName,
                            color = Color(0xFFF3F4F6),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = profile.subtitle,
                            color = Color(0xFF9CA3AF),
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelfFilterChip(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        modifier =
        Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) accent.copy(alpha = 0.2f) else Color(0xFF1C1F27))
            .border(1.dp, if (selected) accent else Color(0xFF2D313B), RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = if (selected) accent else Color(0xFFD1D5DB),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
internal fun ProccdSkinManagerSheet(
    state: ViewfinderUiState,
    manifest: ParsedSkinManifest,
    bitmapCache: SkinBitmapCache,
    assetManager: AssetManager,
    onSelectSkin: (String) -> Unit,
    onResetSkin: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = parseHexColor(state.activeSkin.accentHex, Color(0xFF4CD4E8))
    val hasCustomSkin = state.settings.selectedSkinByCamera.containsKey(state.activeCamera.id)

    ProccdOverlayDrawerContainer(
        title = "HARDWARE SKINS (${SkinCatalog.all.size}) • ${state.activeCamera.codeName}",
        accent = accent,
        onClose = onClose,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Active: ${state.activeSkin.displayName}",
                color = Color(0xFFD1D5DB),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            if (hasCustomSkin) {
                Text(
                    text = "RESET DEFAULT",
                    color = accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier =
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(accent.copy(alpha = 0.14f))
                        .clickable(onClick = onResetSkin)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SkinCatalog.all, key = { it.skinId }) { skinDef ->
                val selected = skinDef.skinId == state.activeSkinId
                val linked = skinDef.skinId in state.activeCamera.linkedSkinIds
                val entry = manifest.skinForId(skinDef.skinId)
                val chassisThumb =
                    rememberSkinImageBitmap(
                        assetPath = entry?.chassisAssetPath ?: skinDef.chassisBgAsset,
                        cache = bitmapCache,
                        assetManager = assetManager
                    )
                val shutterThumb =
                    rememberSkinImageBitmap(
                        assetPath = entry?.shutterAssetPath ?: skinDef.shutterButtonAsset,
                        cache = bitmapCache,
                        assetManager = assetManager
                    )
                val skinAccent = parseHexColor(skinDef.accentHex, accent)

                Column(
                    modifier =
                    Modifier
                        .width(104.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1A1D24))
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) skinAccent else Color(0xFF2D313B),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelectSkin(skinDef.skinId) }
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(parseHexColor(skinDef.bodyPrimaryHex)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (chassisThumb != null) {
                            Image(
                                bitmap = chassisThumb,
                                contentDescription = skinDef.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        if (shutterThumb != null) {
                            Image(
                                bitmap = shutterThumb,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        if (linked) {
                            Box(
                                modifier =
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(3.dp)
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(skinAccent)
                            )
                        }
                    }
                    Text(
                        text = skinDef.displayName,
                        color = if (selected) skinAccent else Color(0xFFE5E7EB),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
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
internal fun ProccdManualDrawer(
    state: ViewfinderUiState,
    onSelectTab: (ControlTab) -> Unit,
    onStepEv: (Float) -> Unit,
    onSelectIso: (Int?) -> Unit,
    onSelectShutter: (Long?) -> Unit,
    onSelectWhiteBalance: (WhiteBalancePreset) -> Unit,
    onSelectAspectRatio: (AspectRatioPreset) -> Unit,
    onHighlightsChange: (Float) -> Unit,
    onShadowsChange: (Float) -> Unit,
    onSharpenChange: (Float) -> Unit,
    onBeautyChange: (Float) -> Unit,
    onToggleLeaksAndDust: () -> Unit,
    onShuffleSeed: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = parseHexColor(state.activeSkin.accentHex, Color(0xFF4CD4E8))
    ProccdOverlayDrawerContainer(
        title = "MANUAL PRO",
        accent = accent,
        onClose = onClose,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DrawerTabPill(
                label = "EXPOSURE & WB",
                selected = state.activeControlTab == ControlTab.EXPOSURE,
                accent = accent,
                onClick = { onSelectTab(ControlTab.EXPOSURE) },
                modifier = Modifier.weight(1f)
            )
            DrawerTabPill(
                label = "TONE & GRUNGE",
                selected = state.activeControlTab == ControlTab.OPTICS,
                accent = accent,
                onClick = { onSelectTab(ControlTab.OPTICS) },
                modifier = Modifier.weight(1f)
            )
        }

        if (state.activeControlTab == ControlTab.EXPOSURE) {
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
                    SmallStepChip("-0.5", accent) { onStepEv(-0.5f) }
                    SmallStepChip("0.0", accent) { onStepEv(-state.opticalSettings.evCompensation) }
                    SmallStepChip("+0.5", accent) { onStepEv(0.5f) }
                }
            }
            DrawerChipRow(
                title = "ISO",
                items = ExposureMath.STANDARD_ISO_STEPS,
                selectedItem = state.opticalSettings.iso,
                labelOf = { it?.toString() ?: "AUTO" },
                accent = accent,
                onSelect = onSelectIso
            )
            DrawerChipRow(
                title = "SHUT",
                items = ExposureMath.STANDARD_SHUTTER_PRESETS.map { it.exposureTimeNs },
                selectedItem = state.opticalSettings.shutterSpeedNs,
                labelOf = { ViewfinderStateReducer.formatShutterLabel(it) },
                accent = accent,
                onSelect = onSelectShutter
            )
            DrawerChipRow(
                title = "WB",
                items = WhiteBalancePreset.entries,
                selectedItem = state.opticalSettings.whiteBalance,
                labelOf = { it.label.uppercase() },
                accent = accent,
                onSelect = onSelectWhiteBalance
            )
            DrawerChipRow(
                title = "RATIO",
                items = AspectRatioPreset.entries,
                selectedItem = state.opticalSettings.aspectRatio,
                labelOf = { it.label },
                accent = accent,
                onSelect = onSelectAspectRatio
            )
        } else {
            DrawerSliderRow("HIGH", state.opticalSettings.highlights, -1f..1f, accent, onHighlightsChange)
            DrawerSliderRow("SHAD", state.opticalSettings.shadows, -1f..1f, accent, onShadowsChange)
            DrawerSliderRow("SHRP", state.opticalSettings.sharpen, 0f..1f, accent, onSharpenChange)
            DrawerSliderRow("SOFT", state.opticalSettings.beautySoftness, 0f..1f, accent, onBeautyChange)
            val leaksActive =
                state.opticalSettings.lightLeakIntensity > 0f || state.opticalSettings.dustIntensity > 0f
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DrawerTabPill(
                    label = if (leaksActive) "LEAKS & DUST ON" else "LEAKS & DUST OFF",
                    selected = leaksActive,
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
                        contentDescription = "Shuffle seed",
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
        }
    }
}

@Composable
internal fun ProccdLensDrawer(
    state: ViewfinderUiState,
    onSelectLens: (LensAttachment) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = parseHexColor(state.activeSkin.accentHex, Color(0xFF4CD4E8))
    ProccdOverlayDrawerContainer(
        title = "OPTICAL LENS & FX (${LensAttachment.entries.size})",
        accent = accent,
        onClose = onClose,
        modifier = modifier
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(LensAttachment.entries) { lens ->
                val selected = lens == state.opticalSettings.lensAttachment
                val label =
                    when (lens) {
                        LensAttachment.STANDARD -> "STANDARD"
                        LensAttachment.FISHEYE -> "FISHEYE"
                        LensAttachment.STAR_4PT -> "STAR 4PT"
                        LensAttachment.STAR_6PT -> "STAR 6PT"
                        LensAttachment.SOFT_GLOW -> "SOFT GLOW"
                        LensAttachment.PRISM -> "PRISM"
                        LensAttachment.KALEIDOSCOPE -> "KALEIDO"
                        LensAttachment.GRID_2X -> "GRID 2X"
                        LensAttachment.GRID_4X -> "GRID 4X"
                    }
                Box(
                    modifier =
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) accent.copy(alpha = 0.2f) else Color(0xFF1C1F27))
                        .border(1.dp, if (selected) accent else Color(0xFF2D313B), RoundedCornerShape(10.dp))
                        .clickable { onSelectLens(lens) }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = label,
                        color = if (selected) accent else Color(0xFFE5E7EB),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
internal fun ProccdFrameDrawer(
    state: ViewfinderUiState,
    onSelectFrame: (FrameBorderStyle) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = parseHexColor(state.activeSkin.accentHex, Color(0xFF4CD4E8))
    val selectableFrames = remember { ViewfinderStateReducer.singlePhotoFrameStyles() }
    ProccdOverlayDrawerContainer(
        title = "FILM & PRINT FRAMES (${selectableFrames.size})",
        accent = accent,
        onClose = onClose,
        modifier = modifier
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(selectableFrames) { frame ->
                val selected = frame == state.opticalSettings.frameStyle
                val label =
                    when (frame) {
                        FrameBorderStyle.NONE -> "NONE"
                        FrameBorderStyle.INSTANT_PRINT -> "INSTANT"
                        FrameBorderStyle.INSTANT_WIDE -> "WIDE"
                        FrameBorderStyle.POLAROID_SQUARE -> "POLAROID"
                        FrameBorderStyle.FILM_35MM -> "35MM"
                        FrameBorderStyle.DV_TIMECODE -> "DV HUD"
                        FrameBorderStyle.CINE_16MM -> "16MM"
                        FrameBorderStyle.PHOTO_BOOTH_4STRIP -> "4-STRIP"
                    }
                Box(
                    modifier =
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) accent.copy(alpha = 0.2f) else Color(0xFF1C1F27))
                        .border(1.dp, if (selected) accent else Color(0xFF2D313B), RoundedCornerShape(10.dp))
                        .clickable { onSelectFrame(frame) }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = label,
                        color = if (selected) accent else Color(0xFFE5E7EB),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
internal fun ProccdStampDrawer(
    state: ViewfinderUiState,
    ledFontFamily: FontFamily,
    dotMatrixFontFamily: FontFamily,
    vcrOsdFontFamily: FontFamily,
    pixelFontFamily: FontFamily,
    onToggleStamp: () -> Unit,
    onSelectFont: (StampFontStyle) -> Unit,
    onSelectColor: (StampStyle) -> Unit,
    onSelectPosition: (StampPosition) -> Unit,
    onSelectFormat: (StampFormat) -> Unit,
    onCustomTextChange: (String) -> Unit,
    onToggleEraYear: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = parseHexColor(state.activeSkin.accentHex, Color(0xFF4CD4E8))
    val previewFont =
        when (state.stampConfig.fontStyle) {
            StampFontStyle.DOT_MATRIX -> dotMatrixFontFamily
            StampFontStyle.SEVEN_SEGMENT -> ledFontFamily
            StampFontStyle.VCR_OSD -> vcrOsdFontFamily
            StampFontStyle.PIXEL -> pixelFontFamily
            StampFontStyle.CLEAN_SANS -> FontFamily.SansSerif
        }
    val stampPreview =
        DateStampFormatter.format(
            epochMillis = System.currentTimeMillis(),
            format = state.stampConfig.format,
            useEraYearOffset = state.stampConfig.useEraYearOffset,
            eraYear = state.activeCamera.eraYearOffset,
            customText = state.stampConfig.customText
        )

    ProccdOverlayDrawerContainer(
        title = "DATE STAMP",
        accent = accent,
        onClose = onClose,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DrawerTabPill(
                label = if (state.stampConfig.enabled) "STAMP ON: $stampPreview" else "STAMP OFF",
                selected = state.stampConfig.enabled,
                accent = accent,
                onClick = onToggleStamp,
                modifier = Modifier.weight(1f)
            )
            val eraLabel =
                if (state.stampConfig.useEraYearOffset) {
                    "ERA '${state.activeCamera.eraYearOffset % 100}"
                } else {
                    "REAL YEAR"
                }
            DrawerTabPill(
                label = eraLabel,
                selected = state.stampConfig.useEraYearOffset,
                accent = accent,
                onClick = onToggleEraYear
            )
        }

        Text(
            text = stampPreview,
            color = parseHexColor(state.stampConfig.style.hexColor, accent),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = previewFont
        )

        DrawerChipRow(
            title = "FONT",
            items = StampFontStyle.entries,
            selectedItem = state.stampConfig.fontStyle,
            labelOf = {
                when (it) {
                    StampFontStyle.DOT_MATRIX -> "DOT"
                    StampFontStyle.SEVEN_SEGMENT -> "7-SEG"
                    StampFontStyle.VCR_OSD -> "VCR"
                    StampFontStyle.PIXEL -> "PIXEL"
                    StampFontStyle.CLEAN_SANS -> "SANS"
                }
            },
            accent = accent,
            onSelect = onSelectFont
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "COLOR",
                color = Color(0xFF9CA3AF),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(42.dp)
            )
            StampStyle.entries.forEach { colorStyle ->
                val selected = colorStyle == state.stampConfig.style
                Box(
                    modifier =
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(parseHexColor(colorStyle.hexColor))
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) Color.White else Color(0xFF3A3F4B),
                            shape = CircleShape
                        )
                        .clickable { onSelectColor(colorStyle) }
                )
            }
        }

        DrawerChipRow(
            title = "POS",
            items = StampPosition.entries,
            selectedItem = state.stampConfig.position,
            labelOf = {
                when (it) {
                    StampPosition.BOTTOM_RIGHT -> "BOT-R"
                    StampPosition.BOTTOM_LEFT -> "BOT-L"
                    StampPosition.TOP_RIGHT -> "TOP-R"
                    StampPosition.TOP_LEFT -> "TOP-L"
                }
            },
            accent = accent,
            onSelect = onSelectPosition
        )

        DrawerChipRow(
            title = "FMT",
            items = StampFormat.entries,
            selectedItem = state.stampConfig.format,
            labelOf = {
                when (it) {
                    StampFormat.SHORT_YY_MM_DD -> "'YY MM DD"
                    StampFormat.DOT_YYYY_MM_DD -> "YYYY.MM.DD"
                    StampFormat.EURO_DD_MM_YY -> "DD.MM.YY"
                    StampFormat.CUSTOM -> "CUSTOM"
                }
            },
            accent = accent,
            onSelect = onSelectFormat
        )

        if (state.stampConfig.format == StampFormat.CUSTOM) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "TEXT",
                    color = Color(0xFF9CA3AF),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(42.dp)
                )
                Box(
                    modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E2129))
                        .border(1.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (state.stampConfig.customText.isEmpty()) {
                        Text(
                            text = "AAYNA '04 (A-Z, 0-9, ' . : - /)",
                            color = Color(0xFF6B7280),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = state.stampConfig.customText,
                        onValueChange = onCustomTextChange,
                        singleLine = true,
                        textStyle =
                        TextStyle(
                            color = Color(0xFFF3F4F6),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        ),
                        cursorBrush = SolidColor(accent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
internal fun SampleSpecsDialog(
    profile: CameraProfile,
    accent: Color,
    onSelectCamera: (CameraProfile) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF181A20),
        titleContentColor = Color(0xFFF3F4F6),
        textContentColor = Color(0xFFD1D5DB),
        title = {
            Text(
                text = "${profile.codeName} • ${profile.category.label}",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = profile.subtitle, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (profile.sampleDescription.isNotBlank()) {
                    Text(text = profile.sampleDescription, fontSize = 12.sp)
                }
                Text(
                    text =
                    "Era: ${profile.eraYearOffset} • Aspect: ${profile.defaultAspect.label}\n" +
                        "Grain: ${(profile.grain.lumaStrength * 100).roundToInt()}% • " +
                        "Bloom: ${(profile.bloom.intensity * 100).roundToInt()}%\n" +
                        "Video: ${if (profile.supportsVideo) "Yes" else "Still Only"} • " +
                        "D3D Burst: ${if (profile.isD3dBurst) "4-Shot GIF" else "No"}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF9CA3AF)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSelectCamera(profile)
                onDismiss()
            }) {
                Text(
                    text = "LOAD CAMERA",
                    color = accent,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "CLOSE",
                    color = Color(0xFF9CA3AF),
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    )
}

@Composable
private fun DrawerTabPill(
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
            .background(if (selected) accent.copy(alpha = 0.18f) else Color(0xFF1E2129))
            .border(1.dp, if (selected) accent else Color(0xFF2D313B), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) accent else Color(0xFFD1D5DB),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SmallStepChip(label: String, accent: Color, onClick: () -> Unit) {
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
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun DrawerSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    accent: Color,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            color = Color(0xFF9CA3AF),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(42.dp)
        )
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
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

@Composable
private fun <T> DrawerChipRow(
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
            modifier = Modifier.width(42.dp)
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
                        .background(if (isSelected) accent.copy(alpha = 0.2f) else Color(0xFF1E2129))
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
