package com.themasum.aayna.feature.gallery

import android.graphics.Bitmap
import android.widget.VideoView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesomeMotion
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GifBox
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.themasum.aayna.core.gl.OffscreenGlExporter
import com.themasum.aayna.core.gl.VideoTranscodeConfig
import com.themasum.aayna.core.model.CameraCatalog
import com.themasum.aayna.core.model.CaptureRecipe
import com.themasum.aayna.core.model.DateStampFormatter
import com.themasum.aayna.core.model.ExposureMath
import com.themasum.aayna.core.model.FrameBorderStyle
import com.themasum.aayna.core.model.MediaType
import com.themasum.aayna.core.storage.MediaStoreExporter
import com.themasum.aayna.core.storage.ThumbnailMemoryCache
import com.themasum.aayna.core.storage.db.FilmRollEntity
import com.themasum.aayna.core.storage.db.NegativeEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val cardTimestampFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yy.MM.dd HH:mm", Locale.US)

@Composable
internal fun WorksGalleryGrid(
    negatives: List<NegativeEntity>,
    activeTab: GalleryFilterTab,
    thumbnailCache: ThumbnailMemoryCache?,
    isSelectionMode: Boolean,
    selectedIds: Set<String>,
    onToggleSelect: (String) -> Unit,
    onEnterSelection: (String) -> Unit,
    onOpenNegative: (NegativeEntity) -> Unit,
    onSoftDeleteNegative: (NegativeEntity) -> Unit,
    onRestoreNegative: (NegativeEntity) -> Unit,
    onPurgeExpiredTrash: () -> Unit,
    onEmptyTrash: () -> Unit,
    onImportPhotoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingDelete by remember { mutableStateOf<NegativeEntity?>(null) }
    var showEmptyTrashConfirm by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        if (activeTab == GalleryFilterTab.TRASH) {
            TrashActionBar(
                trashCount = negatives.size,
                onPurgeExpired = onPurgeExpiredTrash,
                onEmptyTrash = { showEmptyTrashConfirm = true }
            )
        }

        if (negatives.isEmpty()) {
            val (emptyTitle, emptySub, ctaLabel) =
                when (activeTab) {
                    GalleryFilterTab.TRASH ->
                        Triple(
                            "TRASH IS EMPTY",
                            "Soft-deleted works stay recoverable for 30 days before auto-purge.",
                            "IMPORT PHOTO"
                        )
                    GalleryFilterTab.VIDEO ->
                        Triple(
                            "NO VIDEO WORKS YET",
                            "Record a DV or VHS clip in Camcorder mode to view or share offline.",
                            "IMPORT PHOTO"
                        )
                    GalleryFilterTab.D3D ->
                        Triple(
                            "NO D3D WIGGLEGRAMS YET",
                            "Shoot a D3D burst or import 2-4 shots to assemble a looping GIF wigglegram.",
                            "IMPORT 2-4 SHOTS"
                        )
                    GalleryFilterTab.INSTANT ->
                        Triple(
                            "NO INSTANT WORKS YET",
                            "Shoot in Instant Digicam mode or import a photo to grade non-destructively.",
                            "IMPORT PHOTO"
                        )
                    else ->
                        Triple(
                            "NO WORKS IN DARKROOM",
                            "Shoot in Aayna or import any photo to grade non-destructively in the Studio.",
                            "IMPORT PHOTO"
                        )
                }
            StudioEmptyState(
                title = emptyTitle,
                subtitle = emptySub,
                actionLabel = ctaLabel,
                onAction = onImportPhotoClick,
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 156.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(negatives, key = { it.recipeId }) { entity ->
                    NegativeLabCard(
                        entity = entity,
                        thumbnailCache = thumbnailCache,
                        isTrashTab = activeTab == GalleryFilterTab.TRASH,
                        isSelectionMode = isSelectionMode,
                        isSelected = selectedIds.contains(entity.recipeId),
                        onClick = {
                            if (isSelectionMode) {
                                onToggleSelect(entity.recipeId)
                            } else {
                                onOpenNegative(entity)
                            }
                        },
                        onLongClick = { onEnterSelection(entity.recipeId) },
                        onActionClick = {
                            if (activeTab == GalleryFilterTab.TRASH) {
                                onRestoreNegative(entity)
                            } else {
                                pendingDelete = entity
                            }
                        }
                    )
                }
            }
        }
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = ProCcdPalette.HardwareSlate,
            titleContentColor = ProCcdPalette.CreamInk,
            textContentColor = ProCcdPalette.TextSecondary,
            title = {
                Text(
                    text = "Move to Trash?",
                    fontFamily = LocalProCcdFonts.current.condensed,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This negative moves to Trash and can be restored within 30 days."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onSoftDeleteNegative(toDelete)
                    }
                ) {
                    Text(
                        "MOVE TO TRASH",
                        color = ProCcdPalette.LedRed,
                        fontFamily = LocalProCcdFonts.current.condensed
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(
                        "CANCEL",
                        color = ProCcdPalette.TextSecondary,
                        fontFamily = LocalProCcdFonts.current.condensed
                    )
                }
            }
        )
    }

    if (showEmptyTrashConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashConfirm = false },
            containerColor = ProCcdPalette.HardwareSlate,
            titleContentColor = ProCcdPalette.CreamInk,
            textContentColor = ProCcdPalette.TextSecondary,
            title = {
                Text(
                    text = "Empty Trash Permanently?",
                    fontFamily = LocalProCcdFonts.current.condensed,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text("All ${negatives.size} trashed item(s) and their clean negatives will be permanently deleted.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEmptyTrashConfirm = false
                        onEmptyTrash()
                    }
                ) {
                    Text(
                        "DELETE ALL",
                        color = ProCcdPalette.LedRed,
                        fontFamily = LocalProCcdFonts.current.condensed
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyTrashConfirm = false }) {
                    Text(
                        "CANCEL",
                        color = ProCcdPalette.TextSecondary,
                        fontFamily = LocalProCcdFonts.current.condensed
                    )
                }
            }
        )
    }
}

@Composable
private fun TrashActionBar(trashCount: Int, onPurgeExpired: () -> Unit, onEmptyTrash: () -> Unit) {
    val fonts = LocalProCcdFonts.current
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .background(ProCcdPalette.HardwareSlate)
            .border(1.dp, ProCcdPalette.BorderMetallic)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$trashCount IN TRASH (30D RETENTION)",
            color = ProCcdPalette.TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = fonts.led
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HardwareChip(
                label = "PURGE EXPIRED",
                selected = false,
                onClick = onPurgeExpired,
                accentColor = ProCcdPalette.WarmAmber
            )
            if (trashCount > 0) {
                HardwareChip(
                    label = "EMPTY TRASH",
                    selected = true,
                    onClick = onEmptyTrash,
                    accentColor = ProCcdPalette.LedRed
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NegativeLabCard(
    entity: NegativeEntity,
    thumbnailCache: ThumbnailMemoryCache?,
    isTrashTab: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onActionClick: () -> Unit
) {
    val fonts = LocalProCcdFonts.current
    val profile = remember(entity.cameraId) { CameraCatalog.findByIdOrDefault(entity.cameraId) }
    val formattedTime = remember(entity.capturedAtEpochMs) { formatEpochTimestamp(entity.capturedAtEpochMs) }
    val mediaType = remember(entity.mediaType) { entity.resolvedMediaType() }

    val thumbBitmap by produceState<Bitmap?>(
        initialValue = null,
        entity.thumbnailPath,
        entity.recipeJson
    ) {
        value =
            GalleryImageDecoder.loadThumbnailCached(
                path = entity.thumbnailPath,
                thumbnailCache = thumbnailCache
            )
    }

    val borderColor =
        when {
            isSelected -> ProCcdPalette.WarmAmber
            isTrashTab -> ProCcdPalette.LedRed.copy(alpha = 0.45f)
            else -> ProCcdPalette.BorderMetallic
        }

    Surface(
        modifier =
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(10.dp),
        color = ProCcdPalette.HardwareSlate,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor)
    ) {
        Column {
            Box(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .background(ProCcdPalette.SurfaceRecessed),
                contentAlignment = Alignment.Center
            ) {
                if (thumbBitmap != null) {
                    Image(
                        bitmap = thumbBitmap!!.asImageBitmap(),
                        contentDescription = profile.codeName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = profile.codeName,
                        color = ProCcdPalette.TextMuted,
                        fontSize = 12.sp,
                        fontFamily = fonts.led
                    )
                }

                Row(
                    modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    LedStatusPill(label = profile.codeName, accentColor = ProCcdPalette.LedCyan)
                    if (entity.frameNumber != null) {
                        LedStatusPill(
                            label = "#${entity.frameNumber.toString().padStart(2, '0')}",
                            accentColor = ProCcdPalette.WarmAmber,
                            filledDot = false
                        )
                    }
                }

                Row(
                    modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    when (mediaType) {
                        MediaType.VIDEO ->
                            LedStatusPill(
                                label = "VID ${DateStampFormatter.formatTimecode(entity.videoDurationMs)}",
                                accentColor = ProCcdPalette.LedRed
                            )
                        MediaType.D3D_BURST ->
                            LedStatusPill(
                                label = "D3D ${entity.burstFrameCount}F",
                                accentColor = ProCcdPalette.WarmAmber
                            )
                        MediaType.STILL ->
                            if (entity.exportedUri != null) {
                                LedStatusPill(label = "EXPORTED", accentColor = ProCcdPalette.LedEmerald)
                            }
                    }
                }

                if (isSelectionMode) {
                    Icon(
                        imageVector =
                        if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = "Select",
                        tint = if (isSelected) ProCcdPalette.WarmAmber else ProCcdPalette.CreamInk,
                        modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(ProCcdPalette.VoidObsidian.copy(alpha = 0.72f))
                    )
                } else {
                    IconButton(
                        onClick = onActionClick,
                        modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(ProCcdPalette.VoidObsidian.copy(alpha = 0.72f))
                    ) {
                        Icon(
                            imageVector =
                            if (isTrashTab) Icons.Rounded.RestoreFromTrash else Icons.Rounded.Delete,
                            contentDescription = if (isTrashTab) "Restore" else "Move to Trash",
                            tint = if (isTrashTab) ProCcdPalette.LedEmerald else ProCcdPalette.TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Column(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = formattedTime,
                    color = ProCcdPalette.TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = fonts.led,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (entity.caption.isNotBlank()) {
                    Text(
                        text = entity.caption,
                        color = ProCcdPalette.CreamInk,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
internal fun BatchSelectionToolbar(
    selectedCount: Int,
    isTrashTab: Boolean,
    isBatchBusy: Boolean,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onBatchExport: () -> Unit,
    onBatchSoftDelete: () -> Unit,
    onBatchRestore: () -> Unit,
    onBatchPermanentDelete: () -> Unit
) {
    val fonts = LocalProCcdFonts.current
    Surface(
        color = ProCcdPalette.HardwareSlate,
        border = BorderStroke(1.dp, ProCcdPalette.WarmAmber),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LedStatusPill(label = "$selectedCount SEL", accentColor = ProCcdPalette.WarmAmber)
                HardwareChip(label = "ALL", selected = false, onClick = onSelectAll)
                HardwareChip(label = "CANCEL", selected = false, onClick = onClearSelection)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isBatchBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = ProCcdPalette.WarmAmber
                    )
                } else if (isTrashTab) {
                    HardwareChip(
                        label = "RESTORE",
                        selected = true,
                        onClick = onBatchRestore,
                        accentColor = ProCcdPalette.LedEmerald
                    )
                    HardwareChip(
                        label = "PURGE",
                        selected = true,
                        onClick = onBatchPermanentDelete,
                        accentColor = ProCcdPalette.LedRed
                    )
                } else {
                    HardwareChip(
                        label = "EXPORT",
                        selected = true,
                        onClick = onBatchExport,
                        accentColor = ProCcdPalette.WarmAmber
                    )
                    HardwareChip(
                        label = "TRASH",
                        selected = true,
                        onClick = onBatchSoftDelete,
                        accentColor = ProCcdPalette.LedRed
                    )
                }
            }
        }
    }
}

@Composable
internal fun FilmRollsView(
    rolls: List<FilmRollEntity>,
    selectedRoll: FilmRollEntity?,
    selectedRollFrames: List<NegativeEntity>,
    thumbnailCache: ThumbnailMemoryCache?,
    isSelectionMode: Boolean,
    selectedIds: Set<String>,
    onToggleSelect: (String) -> Unit,
    onEnterSelection: (String) -> Unit,
    onSelectRoll: (FilmRollEntity?) -> Unit,
    onLoadNewRoll: () -> Unit,
    onOpenFrame: (NegativeEntity) -> Unit,
    onSoftDeleteFrame: (NegativeEntity) -> Unit,
    onImportPhotoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fonts = LocalProCcdFonts.current
    if (selectedRoll != null) {
        ContactSheetView(
            roll = selectedRoll,
            frames = selectedRollFrames,
            thumbnailCache = thumbnailCache,
            isSelectionMode = isSelectionMode,
            selectedIds = selectedIds,
            onToggleSelect = onToggleSelect,
            onEnterSelection = onEnterSelection,
            onBackToRolls = { onSelectRoll(null) },
            onOpenFrame = onOpenFrame,
            onSoftDeleteFrame = onSoftDeleteFrame,
            onImportPhotoClick = onImportPhotoClick,
            modifier = modifier
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .background(ProCcdPalette.HardwareSlate)
                .border(1.dp, ProCcdPalette.BorderMetallic)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ANALOG FILM ROLLS",
                    color = ProCcdPalette.CreamInk,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fonts.condensed
                )
                Text(
                    text = "${rolls.size} roll(s) logged",
                    color = ProCcdPalette.TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = fonts.led
                )
            }
            Button(
                onClick = onLoadNewRoll,
                colors =
                ButtonDefaults.buttonColors(
                    containerColor = ProCcdPalette.WarmAmber,
                    contentColor = ProCcdPalette.VoidObsidian
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "LOAD NEW ROLL",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fonts.condensed
                )
            }
        }

        if (rolls.isEmpty()) {
            StudioEmptyState(
                title = "NO FILM ROLLS YET",
                subtitle = "Load a new film roll or capture in Analog Developing Ritual mode to track exposures.",
                actionLabel = "LOAD NEW ROLL",
                onAction = onLoadNewRoll,
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(rolls, key = { it.rollId }) { roll ->
                    FilmRollCard(
                        roll = roll,
                        onClick = { onSelectRoll(roll) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilmRollCard(roll: FilmRollEntity, onClick: () -> Unit) {
    val fonts = LocalProCcdFonts.current
    val profile = remember(roll.cameraId) { CameraCatalog.findByIdOrDefault(roll.cameraId) }
    val progress =
        if (roll.capacity > 0) {
            (roll.shotsTaken.toFloat() / roll.capacity.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    val createdLabel = remember(roll.createdAtEpochMs) { formatEpochTimestamp(roll.createdAtEpochMs) }

    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = ProCcdPalette.HardwareSlate,
        border =
        BorderStroke(
            width = 1.dp,
            color =
            if (roll.isActive) {
                ProCcdPalette.LedEmerald.copy(alpha = 0.6f)
            } else {
                ProCcdPalette.BorderMetallic
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LedStatusPill(label = profile.codeName, accentColor = ProCcdPalette.LedCyan)
                    Text(
                        text = "${roll.shotsTaken} / ${roll.capacity} EXP",
                        color = ProCcdPalette.CreamInk,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fonts.led
                    )
                }
                LedStatusPill(
                    label = if (roll.isActive) "ACTIVE" else "DEVELOPED",
                    accentColor = if (roll.isActive) ProCcdPalette.LedEmerald else ProCcdPalette.TextSecondary
                )
            }

            LinearProgressIndicator(
                progress = { progress },
                color = if (roll.isActive) ProCcdPalette.LedEmerald else ProCcdPalette.WarmAmber,
                trackColor = ProCcdPalette.SurfaceRecessed,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "LOADED $createdLabel",
                    color = ProCcdPalette.TextMuted,
                    fontSize = 10.sp,
                    fontFamily = fonts.led
                )
                Text(
                    text = "CONTACT SHEET >",
                    color = ProCcdPalette.WarmAmber,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fonts.condensed
                )
            }
        }
    }
}

@Composable
private fun ContactSheetView(
    roll: FilmRollEntity,
    frames: List<NegativeEntity>,
    thumbnailCache: ThumbnailMemoryCache?,
    isSelectionMode: Boolean,
    selectedIds: Set<String>,
    onToggleSelect: (String) -> Unit,
    onEnterSelection: (String) -> Unit,
    onBackToRolls: () -> Unit,
    onOpenFrame: (NegativeEntity) -> Unit,
    onSoftDeleteFrame: (NegativeEntity) -> Unit,
    onImportPhotoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fonts = LocalProCcdFonts.current
    val profile = remember(roll.cameraId) { CameraCatalog.findByIdOrDefault(roll.cameraId) }
    Column(modifier = modifier.fillMaxSize()) {
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
                IconButton(onClick = onBackToRolls) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back to Rolls",
                        tint = ProCcdPalette.CreamInk
                    )
                }
                Column {
                    Text(
                        text = "CONTACT SHEET • ${profile.codeName}",
                        color = ProCcdPalette.CreamInk,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fonts.condensed
                    )
                    Text(
                        text = "${roll.shotsTaken}/${roll.capacity} EXPOSURES • ${frames.size} FRAMES",
                        color = ProCcdPalette.TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = fonts.led
                    )
                }
            }
            LedStatusPill(
                label = if (roll.isActive) "ACTIVE" else "ARCHIVED",
                accentColor = if (roll.isActive) ProCcdPalette.LedEmerald else ProCcdPalette.TextSecondary
            )
        }

        if (frames.isEmpty()) {
            StudioEmptyState(
                title = "NO FRAMES IN THIS ROLL",
                subtitle = "Exposures captured on this roll will appear here as a contact sheet.",
                actionLabel = "IMPORT PHOTO",
                onAction = onImportPhotoClick,
                modifier = Modifier.weight(1f)
            )
        } else {
            WorksGalleryGrid(
                negatives = frames,
                activeTab = GalleryFilterTab.ALL,
                thumbnailCache = thumbnailCache,
                isSelectionMode = isSelectionMode,
                selectedIds = selectedIds,
                onToggleSelect = onToggleSelect,
                onEnterSelection = onEnterSelection,
                onOpenNegative = onOpenFrame,
                onSoftDeleteNegative = onSoftDeleteFrame,
                onRestoreNegative = {},
                onPurgeExpiredTrash = {},
                onEmptyTrash = {},
                onImportPhotoClick = onImportPhotoClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun PhotoDetailScreen(
    entity: NegativeEntity,
    offscreenExporter: OffscreenGlExporter,
    onBack: () -> Unit,
    onOpenInStudio: () -> Unit,
    onSaveCaption: (String) -> Unit,
    onExportJpeg: () -> Unit,
    onSoftDelete: () -> Unit,
    isBusy: Boolean
) {
    val fonts = LocalProCcdFonts.current
    val recipe = remember(entity.recipeJson) { entity.toCaptureRecipe() }
    val profile = remember(recipe.cameraId) { CameraCatalog.findByIdOrDefault(recipe.cameraId) }
    var captionText by remember(entity.recipeId, entity.caption) { mutableStateOf(entity.caption) }
    var isHoldingClean by remember { mutableStateOf(false) }

    val cleanBitmap by produceState<Bitmap?>(initialValue = null, entity.cleanImagePath) {
        value =
            withContext(Dispatchers.IO) {
                GalleryImageDecoder.decodeFileSafely(entity.cleanImagePath, maxEdgePx = 1280)
            }
    }

    val displayCleanBitmap =
        remember(cleanBitmap, recipe.opticalSettings.rotationDegrees) {
            val src = cleanBitmap ?: return@remember null
            if (recipe.opticalSettings.rotationDegrees == 0) {
                src
            } else {
                GalleryImageDecoder.rotateBitmap(src, recipe.opticalSettings.rotationDegrees)
            }
        }

    val gradedBitmap by produceState<Bitmap?>(initialValue = null, cleanBitmap, recipe) {
        val src = cleanBitmap ?: return@produceState
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
        value =
            runCatching {
                offscreenExporter.renderBitmap(
                    sourceBitmap = src,
                    profile = profile,
                    recipe = recipe,
                    stampText = stampText,
                    fallbackGains = params.fallbackGains,
                    maxEdgeOverride = 1280,
                    skipStamp = params.skipStamp
                )
            }.getOrNull()
    }

    val exifMeta by produceState(initialValue = GalleryExifMetadata(), entity.cleanImagePath) {
        value = GalleryImageDecoder.readExifMetadata(entity.cleanImagePath)
    }

    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .background(ProCcdPalette.VoidObsidian)
    ) {
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
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = ProCcdPalette.CreamInk
                    )
                }
                LedStatusPill(label = profile.codeName, accentColor = ProCcdPalette.LedCyan)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onExportJpeg,
                    enabled = !isBusy,
                    border = BorderStroke(1.dp, ProCcdPalette.BorderHighlight),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = null,
                        tint = ProCcdPalette.WarmAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "EXPORT",
                        color = ProCcdPalette.CreamInk,
                        fontSize = 11.sp,
                        fontFamily = fonts.condensed
                    )
                }
                Button(
                    onClick = onOpenInStudio,
                    colors =
                    ButtonDefaults.buttonColors(
                        containerColor = ProCcdPalette.WarmAmber,
                        contentColor = ProCcdPalette.VoidObsidian
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "STUDIO",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fonts.condensed
                    )
                }
            }
        }

        Column(
            modifier =
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .height(290.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ProCcdPalette.SurfaceRecessed)
                    .border(1.dp, ProCcdPalette.BorderMetallic, RoundedCornerShape(10.dp))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isHoldingClean = true
                                tryAwaitRelease()
                                isHoldingClean = false
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                val active = if (isHoldingClean) displayCleanBitmap else (gradedBitmap ?: displayCleanBitmap)
                if (active != null) {
                    Image(
                        bitmap = active.asImageBitmap(),
                        contentDescription = profile.codeName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(6.dp)
                    )
                } else {
                    CircularProgressIndicator(color = ProCcdPalette.WarmAmber)
                }
                Box(modifier = Modifier.align(Alignment.TopStart).padding(10.dp)) {
                    LedStatusPill(
                        label = if (isHoldingClean) "CLEAN NEGATIVE" else "HOLD IMAGE = COMPARE CLEAN",
                        accentColor = if (isHoldingClean) ProCcdPalette.LedRed else ProCcdPalette.TextSecondary,
                        filledDot = isHoldingClean
                    )
                }
            }

            Surface(
                color = ProCcdPalette.HardwareSlate,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, ProCcdPalette.BorderMetallic)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HardwareSectionLabel(text = "CAPTION / DARKROOM NOTES")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = captionText,
                            onValueChange = { captionText = it.take(GalleryRecipeReducer.MAX_CAPTION_CHARS) },
                            placeholder = { Text("Add offline photo note...", fontSize = 12.sp) },
                            singleLine = true,
                            colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ProCcdPalette.WarmAmber,
                                unfocusedBorderColor = ProCcdPalette.BorderMetallic,
                                focusedTextColor = ProCcdPalette.CreamInk,
                                unfocusedTextColor = ProCcdPalette.CreamInk
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { onSaveCaption(captionText) },
                            colors =
                            ButtonDefaults.buttonColors(
                                containerColor = ProCcdPalette.WarmAmber,
                                contentColor = ProCcdPalette.VoidObsidian
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Save,
                                contentDescription = "Save Caption",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Surface(
                color = ProCcdPalette.HardwareSlate,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, ProCcdPalette.BorderMetallic)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HardwareSectionLabel(text = "EXIF & RECIPE INSPECTOR")
                    ExifSpecRow("CAMERA", "${profile.codeName} (${profile.eraYearOffset})")
                    ExifSpecRow("CAPTURED", formatEpochTimestamp(entity.capturedAtEpochMs))
                    ExifSpecRow("DIMENSIONS", "${exifMeta.widthPx} x ${exifMeta.heightPx} px")
                    ExifSpecRow("NEGATIVE SIZE", "${(exifMeta.fileSizeBytes / 1024L).coerceAtLeast(1L)} KB")
                    ExifSpecRow("RESOLUTION", recipe.resolutionPreset.label)
                    ExifSpecRow(
                        "OPTICS",
                        String.format(
                            Locale.US,
                            "EV %+.1f • %s • %s",
                            recipe.opticalSettings.evCompensation,
                            lensLabel(recipe.opticalSettings.lensAttachment),
                            frameLabel(recipe.opticalSettings.frameStyle)
                        )
                    )
                    val rollId = entity.rollId
                    if (rollId != null) {
                        ExifSpecRow("FILM ROLL", "${rollId.take(12)} • Frame #${entity.frameNumber ?: 1}")
                    }
                    if (exifMeta.make.isNotBlank() || exifMeta.model.isNotBlank()) {
                        ExifSpecRow("EXIF TAG", "${exifMeta.make} ${exifMeta.model}".trim())
                    }
                }
            }

            OutlinedButton(
                onClick = onSoftDelete,
                border = BorderStroke(1.dp, ProCcdPalette.LedRed),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = null,
                    tint = ProCcdPalette.LedRed,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "MOVE TO TRASH",
                    color = ProCcdPalette.LedRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fonts.condensed
                )
            }
        }
    }
}

@Composable
private fun ExifSpecRow(label: String, value: String) {
    val fonts = LocalProCcdFonts.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = ProCcdPalette.TextMuted,
            fontSize = 10.sp,
            fontFamily = fonts.led
        )
        Text(
            text = value,
            color = ProCcdPalette.CreamInk,
            fontSize = 10.sp,
            fontFamily = fonts.led,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun D3dWigglegramStudioScreen(
    sourceFrames: List<Bitmap>,
    recipe: CaptureRecipe,
    isSavedNegative: Boolean,
    isBusy: Boolean,
    offscreenExporter: OffscreenGlExporter,
    onRecipeChange: (CaptureRecipe) -> Unit,
    onSaveToWorks: (List<Bitmap>) -> Unit,
    onExportLoopingGif: (List<Bitmap>, Int, Boolean) -> Unit,
    onExportMp4: (Int, Boolean) -> Unit,
    onBack: () -> Unit
) {
    val fonts = LocalProCcdFonts.current
    val activeProfile = remember(recipe.cameraId) { CameraCatalog.findByIdOrDefault(recipe.cameraId) }
    var frameDelayMs by remember { mutableIntStateOf(GalleryD3dTimingMath.DEFAULT_FRAME_DELAY_MS) }
    var pingPong by remember { mutableStateOf(true) }
    var gradedFrames by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isRenderingFrames by remember { mutableStateOf(false) }
    var playIndex by remember { mutableIntStateOf(0) }

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

    LaunchedEffect(sourceFrames, recipe, formattedStamp) {
        if (sourceFrames.isEmpty()) return@LaunchedEffect
        isRenderingFrames = true
        val params =
            GalleryRecipeReducer.buildGlRenderParams(
                recipe = recipe,
                profile = activeProfile,
                stampText = formattedStamp
            )
        val rendered =
            sourceFrames.mapNotNull { frame ->
                runCatching {
                    offscreenExporter.renderBitmap(
                        sourceBitmap = frame,
                        profile = activeProfile,
                        recipe = recipe,
                        stampText = formattedStamp,
                        fallbackGains = params.fallbackGains,
                        maxEdgeOverride = 720,
                        skipStamp = params.skipStamp
                    )
                }.getOrNull()
            }
        gradedFrames = rendered
        isRenderingFrames = false
    }

    val activeFrames = gradedFrames.ifEmpty { sourceFrames }

    LaunchedEffect(activeFrames.size, frameDelayMs, pingPong) {
        val sequence = GalleryD3dTimingMath.computeSequenceIndices(activeFrames.size, pingPong)
        if (sequence.isEmpty()) return@LaunchedEffect
        var step = 0
        while (true) {
            playIndex = sequence[step % sequence.size]
            step++
            delay(GalleryD3dTimingMath.clampFrameDelayMs(frameDelayMs).toLong())
        }
    }

    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .background(ProCcdPalette.VoidObsidian)
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .background(ProCcdPalette.HardwareSlate)
                .border(1.dp, ProCcdPalette.BorderMetallic)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = ProCcdPalette.CreamInk
                    )
                }
                LedStatusPill(label = "D3D", accentColor = ProCcdPalette.WarmAmber)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = { onSaveToWorks(activeFrames) },
                    enabled = !isBusy && activeFrames.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 6.dp),
                    border = BorderStroke(1.dp, ProCcdPalette.BorderHighlight),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isSavedNegative) "SAVE" else "KEEP",
                        color = ProCcdPalette.CreamInk,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fonts.condensed
                    )
                }
                OutlinedButton(
                    onClick = { onExportLoopingGif(activeFrames, frameDelayMs, pingPong) },
                    enabled = !isBusy && activeFrames.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 6.dp),
                    border = BorderStroke(1.dp, ProCcdPalette.LedCyan),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        Icons.Rounded.GifBox,
                        contentDescription = null,
                        tint = ProCcdPalette.LedCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "EXPORT GIF",
                        color = ProCcdPalette.LedCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fonts.condensed
                    )
                }
                Button(
                    onClick = { onExportMp4(frameDelayMs, pingPong) },
                    enabled = !isBusy && sourceFrames.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 6.dp),
                    colors =
                    ButtonDefaults.buttonColors(
                        containerColor = ProCcdPalette.WarmAmber,
                        contentColor = ProCcdPalette.VoidObsidian
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Rounded.Videocam, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (isBusy) "ENCODING..." else "EXPORT MP4",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fonts.condensed
                    )
                }
            }
        }

        Box(
            modifier =
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(12.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ProCcdPalette.SurfaceRecessed)
                .border(1.dp, ProCcdPalette.BorderMetallic, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            val shown = activeFrames.getOrNull(playIndex.coerceIn(0, (activeFrames.size - 1).coerceAtLeast(0)))
            if (shown != null) {
                Image(
                    bitmap = shown.asImageBitmap(),
                    contentDescription = "D3D Wigglegram Preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(8.dp)
                )
            }
            Row(
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LedStatusPill(
                    label = "FRAME ${playIndex + 1}/${activeFrames.size.coerceAtLeast(1)}",
                    accentColor = ProCcdPalette.LedCyan
                )
                LedStatusPill(
                    label = if (pingPong) "PING-PONG" else "LOOP",
                    accentColor = ProCcdPalette.LedEmerald
                )
            }
            if (isRenderingFrames || isBusy) {
                CircularProgressIndicator(
                    color = ProCcdPalette.WarmAmber,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).size(20.dp)
                )
            }
        }

        Surface(
            color = ProCcdPalette.HardwareSlate,
            border = BorderStroke(1.dp, ProCcdPalette.BorderMetallic),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val minDelay = GalleryD3dTimingMath.MIN_FRAME_DELAY_MS.toFloat()
                val maxDelay = GalleryD3dTimingMath.MAX_FRAME_DELAY_MS.toFloat()
                HardwareSliderRow(
                    label = "FRAME DELAY SPEED",
                    valueText = "$frameDelayMs ms",
                    value = frameDelayMs.toFloat(),
                    valueRange = minDelay..maxDelay,
                    onValueChange = { frameDelayMs = it.roundToInt() }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "PING-PONG LOOP",
                            color = ProCcdPalette.CreamInk,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = fonts.condensed
                        )
                        Text(
                            text = "Reverses middle frames for seamless 3D wobble",
                            color = ProCcdPalette.TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                    Switch(
                        checked = pingPong,
                        onCheckedChange = { pingPong = it },
                        colors =
                        SwitchDefaults.colors(
                            checkedThumbColor = ProCcdPalette.VoidObsidian,
                            checkedTrackColor = ProCcdPalette.WarmAmber
                        )
                    )
                }

                HardwareSectionLabel(text = "FILM STOCK / CAMERA PROFILE")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(CameraCatalog.all, key = { it.id }) { profile ->
                        HardwareChip(
                            label = profile.codeName,
                            selected = profile.id == recipe.cameraId,
                            onClick = { onRecipeChange(GalleryRecipeReducer.swapCameraProfile(recipe, profile)) },
                            accentColor = ProCcdPalette.LedCyan
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun PhotoBoothStudioScreen(
    sourceFrames: List<Bitmap>,
    recipe: CaptureRecipe,
    isBusy: Boolean,
    offscreenExporter: OffscreenGlExporter,
    onRecipeChange: (CaptureRecipe) -> Unit,
    onSwitchToD3d: () -> Unit,
    onSaveStripToWorks: (Bitmap) -> Unit,
    onExportStripJpeg: (Bitmap) -> Unit,
    onBack: () -> Unit
) {
    val fonts = LocalProCcdFonts.current
    val activeProfile = remember(recipe.cameraId) { CameraCatalog.findByIdOrDefault(recipe.cameraId) }
    var stripBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isRendering by remember { mutableStateOf(false) }

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

    LaunchedEffect(sourceFrames, recipe, formattedStamp) {
        if (sourceFrames.isEmpty()) return@LaunchedEffect
        isRendering = true
        val gains =
            ExposureMath.computeShaderFallbackGains(
                hasManualSensor = false,
                ev = recipe.opticalSettings.evCompensation,
                iso = recipe.opticalSettings.iso,
                shutterNs = recipe.opticalSettings.shutterSpeedNs
            )
        stripBitmap =
            runCatching {
                offscreenExporter.renderPhotoBooth4Strip(
                    frames = sourceFrames,
                    profile = activeProfile,
                    recipe = recipe.copy(
                        opticalSettings =
                        recipe.opticalSettings.copy(
                            frameStyle = FrameBorderStyle.PHOTO_BOOTH_4STRIP
                        )
                    ),
                    stampText = formattedStamp,
                    fallbackGains = gains,
                    outputSizePx = 1024
                )
            }.getOrNull()
        isRendering = false
    }

    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .background(ProCcdPalette.VoidObsidian)
    ) {
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
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = ProCcdPalette.CreamInk
                    )
                }
                LedStatusPill(label = "BOOTH 4-STRIP", accentColor = ProCcdPalette.LedCyan)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HardwareChip(
                    label = "TO D3D",
                    selected = false,
                    onClick = onSwitchToD3d,
                    accentColor = ProCcdPalette.WarmAmber
                )
                OutlinedButton(
                    onClick = { stripBitmap?.let(onSaveStripToWorks) },
                    enabled = !isBusy && stripBitmap != null,
                    border = BorderStroke(1.dp, ProCcdPalette.BorderHighlight),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("SAVE", color = ProCcdPalette.CreamInk, fontSize = 11.sp, fontFamily = fonts.condensed)
                }
                Button(
                    onClick = { stripBitmap?.let(onExportStripJpeg) },
                    enabled = !isBusy && stripBitmap != null,
                    colors =
                    ButtonDefaults.buttonColors(
                        containerColor = ProCcdPalette.WarmAmber,
                        contentColor = ProCcdPalette.VoidObsidian
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isBusy) "SAVING..." else "EXPORT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fonts.condensed
                    )
                }
            }
        }

        Box(
            modifier =
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(12.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ProCcdPalette.SurfaceRecessed)
                .border(1.dp, ProCcdPalette.BorderMetallic, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            val preview = stripBitmap
            if (preview != null) {
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = "Photo Booth 4-Strip",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(8.dp)
                )
            }
            if (isRendering || isBusy) {
                CircularProgressIndicator(color = ProCcdPalette.WarmAmber)
            }
        }

        Surface(
            color = ProCcdPalette.HardwareSlate,
            border = BorderStroke(1.dp, ProCcdPalette.BorderMetallic),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HardwareSectionLabel(text = "BOOTH CAMERA LUT (${sourceFrames.size} SHOTS)")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(CameraCatalog.all, key = { it.id }) { profile ->
                        HardwareChip(
                            label = profile.codeName,
                            selected = profile.id == recipe.cameraId,
                            onClick = { onRecipeChange(GalleryRecipeReducer.swapCameraProfile(recipe, profile)) },
                            accentColor = ProCcdPalette.WarmAmber
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun VideoDetailScreen(
    entity: NegativeEntity,
    thumbnailCache: ThumbnailMemoryCache?,
    isBusy: Boolean,
    exportProgress: Float?,
    onBack: () -> Unit,
    onPlayOffline: () -> Unit,
    onShareOffline: () -> Unit,
    onExportEditedMp4: (editedRecipe: CaptureRecipe, trimStartMs: Long, trimEndMs: Long) -> Unit,
    onSaveCaption: (String) -> Unit,
    onSoftDelete: () -> Unit
) {
    val context = LocalContext.current
    val fonts = LocalProCcdFonts.current
    val initialRecipe = remember(entity.recipeId, entity.recipeJson) { entity.toCaptureRecipe() }
    var editRecipe by remember(entity.recipeId, entity.recipeJson) { mutableStateOf(initialRecipe) }
    val activeProfile = remember(editRecipe.cameraId) { CameraCatalog.findByIdOrDefault(editRecipe.cameraId) }
    var captionText by remember(entity.recipeId, entity.caption) { mutableStateOf(entity.caption) }
    val thumbBitmap by produceState<Bitmap?>(initialValue = null, entity.thumbnailPath) {
        value = GalleryImageDecoder.loadThumbnailCached(entity.thumbnailPath, thumbnailCache)
    }

    val rawVideoUri = entity.mediaUri ?: entity.exportedUri
    val resolvedVideoUri =
        remember(rawVideoUri, context) {
            rawVideoUri?.let { MediaStoreExporter.resolveShareableVideoUri(context, it) }
        }

    var videoViewRef by remember(entity.recipeId) { mutableStateOf<VideoView?>(null) }
    var loadedUriKey by remember(entity.recipeId) { mutableStateOf<String?>(null) }
    var isPrepared by remember(resolvedVideoUri) { mutableStateOf(false) }
    var isPlaying by remember(resolvedVideoUri) { mutableStateOf(false) }
    var hasPlaybackError by remember(resolvedVideoUri) { mutableStateOf(false) }
    var durationMs by remember(entity.recipeId) { mutableLongStateOf(entity.videoDurationMs.coerceAtLeast(0L)) }
    var currentPositionMs by remember(entity.recipeId) { mutableLongStateOf(0L) }
    var trimStartMs by remember(entity.recipeId) { mutableLongStateOf(0L) }
    var trimEndMs by remember(entity.recipeId) { mutableLongStateOf(entity.videoDurationMs.coerceAtLeast(0L)) }

    val videoFilterProfiles =
        remember {
            val priorityIds = listOf("original", "dcr", "v80", "ccd801", "z30", "grf")
            val priority = priorityIds.mapNotNull { CameraCatalog.findById(it) }
            (priority + CameraCatalog.all).distinctBy { it.id }
        }
    val videoFrameStyles =
        remember {
            listOf(
                FrameBorderStyle.NONE,
                FrameBorderStyle.DV_TIMECODE,
                FrameBorderStyle.CINE_16MM,
                FrameBorderStyle.FILM_35MM,
                FrameBorderStyle.INSTANT_PRINT
            )
        }

    LaunchedEffect(videoViewRef, resolvedVideoUri) {
        val view = videoViewRef ?: return@LaunchedEffect
        val uri = resolvedVideoUri ?: return@LaunchedEffect
        val key = uri.toString()
        if (loadedUriKey != key) {
            loadedUriKey = key
            isPrepared = false
            isPlaying = false
            hasPlaybackError = false
            view.setVideoURI(uri)
        }
    }

    LaunchedEffect(isPlaying, trimStartMs, trimEndMs) {
        while (isPlaying) {
            val view = videoViewRef
            if (view != null) {
                val pos = view.currentPosition.toLong().coerceAtLeast(0L)
                currentPositionMs = pos
                if (trimEndMs > 0L && pos >= trimEndMs) {
                    view.pause()
                    view.seekTo(trimStartMs.toInt())
                    currentPositionMs = trimStartMs
                    isPlaying = false
                }
            }
            delay(100L)
        }
    }

    DisposableEffect(entity.recipeId) {
        onDispose {
            videoViewRef?.stopPlayback()
            videoViewRef = null
        }
    }

    val minSpanMs = VideoTranscodeConfig.MIN_TRIM_DURATION_MS
    val canTrim = durationMs > minSpanMs
    val validTrimEnd =
        if (durationMs > 0L) {
            trimEndMs.coerceIn((trimStartMs + minSpanMs).coerceAtMost(durationMs), durationMs)
        } else {
            Long.MAX_VALUE
        }
    val canExportMp4 =
        !isBusy &&
            resolvedVideoUri != null &&
            !hasPlaybackError &&
            (durationMs == 0L || (validTrimEnd - trimStartMs) >= minSpanMs)

    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .background(ProCcdPalette.VoidObsidian)
    ) {
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
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = ProCcdPalette.CreamInk
                    )
                }
                LedStatusPill(label = "VIDEO • ${activeProfile.codeName}", accentColor = ProCcdPalette.LedRed)
            }
            LedStatusPill(
                label = "${DateStampFormatter.formatTimecode(
                    currentPositionMs
                )} / ${DateStampFormatter.formatTimecode(durationMs)}",
                accentColor = ProCcdPalette.WarmAmber
            )
        }

        Column(
            modifier =
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ProCcdPalette.SurfaceRecessed)
                    .border(1.dp, ProCcdPalette.BorderMetallic, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (resolvedVideoUri != null && !hasPlaybackError) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setOnPreparedListener { mp ->
                                    isPrepared = true
                                    hasPlaybackError = false
                                    val actualDuration = mp.duration.toLong().coerceAtLeast(0L)
                                    if (actualDuration > 0L) {
                                        durationMs = actualDuration
                                        if (trimEndMs <= 0L || trimEndMs > actualDuration) {
                                            trimEndMs = actualDuration
                                        }
                                        trimStartMs =
                                            trimStartMs.coerceIn(
                                                0L,
                                                (trimEndMs - minSpanMs).coerceAtLeast(0L)
                                            )
                                    }
                                    seekTo(trimStartMs.coerceAtLeast(1L).toInt())
                                }
                                setOnCompletionListener {
                                    isPlaying = false
                                    currentPositionMs = trimStartMs
                                    seekTo(trimStartMs.toInt())
                                }
                                setOnErrorListener { _, _, _ ->
                                    hasPlaybackError = true
                                    isPlaying = false
                                    true
                                }
                            }
                        },
                        update = { view ->
                            if (videoViewRef !== view) {
                                videoViewRef = view
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    if (thumbBitmap != null) {
                        Image(
                            bitmap = thumbBitmap!!.asImageBitmap(),
                            contentDescription = activeProfile.codeName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Videocam,
                            contentDescription = null,
                            tint = ProCcdPalette.TextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    Box(modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp)) {
                        LedStatusPill(
                            label = if (rawVideoUri.isNullOrBlank()) "NO VIDEO URI" else "PREVIEW UNAVAILABLE",
                            accentColor = ProCcdPalette.LedRed
                        )
                    }
                }

                if (resolvedVideoUri != null && !hasPlaybackError) {
                    Surface(
                        shape = CircleShape,
                        color = ProCcdPalette.VoidObsidian.copy(alpha = 0.72f),
                        border = BorderStroke(1.5.dp, ProCcdPalette.WarmAmber),
                        modifier =
                        Modifier
                            .align(Alignment.Center)
                            .clickable(enabled = isPrepared && !isBusy) {
                                val view = videoViewRef ?: return@clickable
                                if (isPlaying) {
                                    view.pause()
                                    isPlaying = false
                                } else {
                                    if (currentPositionMs < trimStartMs ||
                                        (trimEndMs > 0L && currentPositionMs >= trimEndMs)
                                    ) {
                                        view.seekTo(trimStartMs.toInt())
                                        currentPositionMs = trimStartMs
                                    }
                                    view.start()
                                    isPlaying = true
                                }
                            }
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause Video" else "Play Video",
                            tint = ProCcdPalette.WarmAmber,
                            modifier = Modifier.padding(12.dp).size(30.dp)
                        )
                    }
                }
            }

            Surface(
                color = ProCcdPalette.HardwareSlate,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, ProCcdPalette.BorderMetallic)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HardwareSectionLabel(text = "TRIM, GRADE & BORDER RE-EXPORT")

                    if (canTrim) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TRIM RANGE",
                                color = ProCcdPalette.TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = fonts.condensed
                            )
                            Text(
                                text =
                                "${DateStampFormatter.formatTimecode(trimStartMs)} -> " +
                                    "${DateStampFormatter.formatTimecode(validTrimEnd)} " +
                                    "(${DateStampFormatter.formatTimecode(validTrimEnd - trimStartMs)})",
                                color = ProCcdPalette.WarmAmber,
                                fontSize = 10.sp,
                                fontFamily = fonts.led
                            )
                        }
                        RangeSlider(
                            value = trimStartMs.toFloat()..validTrimEnd.toFloat(),
                            onValueChange = { range ->
                                val maxDur = durationMs.toFloat()
                                val rawStart = range.start.toLong().coerceIn(
                                    0L,
                                    (durationMs - minSpanMs).coerceAtLeast(0L)
                                )
                                val rawEnd = range.endInclusive.toLong().coerceIn(rawStart + minSpanMs, maxDur.toLong())
                                trimStartMs = rawStart
                                trimEndMs = rawEnd
                                if (!isPlaying && isPrepared) {
                                    videoViewRef?.seekTo(rawStart.toInt())
                                    currentPositionMs = rawStart
                                }
                            },
                            valueRange = 0f..durationMs.toFloat(),
                            colors =
                            SliderDefaults.colors(
                                thumbColor = ProCcdPalette.WarmAmber,
                                activeTrackColor = ProCcdPalette.WarmAmber,
                                inactiveTrackColor = ProCcdPalette.BorderMetallic
                            )
                        )
                    }

                    HardwareSectionLabel(text = "CAMERA FILTER (${videoFilterProfiles.size})")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(videoFilterProfiles, key = { it.id }) { camProfile ->
                            HardwareChip(
                                label = camProfile.codeName,
                                selected = editRecipe.cameraId == camProfile.id,
                                onClick = {
                                    editRecipe = GalleryRecipeReducer.swapCameraProfile(editRecipe, camProfile)
                                },
                                accentColor = ProCcdPalette.WarmAmber
                            )
                        }
                    }

                    HardwareSectionLabel(text = "FRAME BORDER (${videoFrameStyles.size})")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(videoFrameStyles, key = { it.name }) { frame ->
                            HardwareChip(
                                label = frameLabel(frame),
                                selected = editRecipe.opticalSettings.frameStyle == frame,
                                onClick = {
                                    editRecipe = GalleryRecipeReducer.updateFrameStyle(editRecipe, frame)
                                },
                                accentColor = ProCcdPalette.LedCyan
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
                                text = "DATE / TIMECODE STAMP",
                                color = ProCcdPalette.CreamInk,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = fonts.condensed
                            )
                            val stampSubtitle =
                                if (editRecipe.stampConfig.enabled) {
                                    "Burn stamp into exported MP4"
                                } else {
                                    "Stamp disabled"
                                }
                            Text(
                                text = stampSubtitle,
                                color = ProCcdPalette.TextSecondary,
                                fontSize = 10.sp
                            )
                        }
                        Switch(
                            checked = editRecipe.stampConfig.enabled,
                            onCheckedChange = { enabled ->
                                editRecipe = GalleryRecipeReducer.updateStampEnabled(editRecipe, enabled)
                            },
                            colors =
                            SwitchDefaults.colors(
                                checkedThumbColor = ProCcdPalette.VoidObsidian,
                                checkedTrackColor = ProCcdPalette.WarmAmber
                            )
                        )
                    }

                    if (isBusy && exportProgress != null) {
                        val pct = (exportProgress.coerceIn(0f, 1f) * 100f).roundToInt()
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            LinearProgressIndicator(
                                progress = { exportProgress.coerceIn(0f, 1f) },
                                color = ProCcdPalette.WarmAmber,
                                trackColor = ProCcdPalette.SurfaceRecessed,
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                            )
                            Text(
                                text = "TRANSCODING MP4 • $pct%",
                                color = ProCcdPalette.WarmAmber,
                                fontSize = 10.sp,
                                fontFamily = fonts.led
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (isPlaying) {
                                videoViewRef?.pause()
                                isPlaying = false
                            }
                            onExportEditedMp4(editRecipe, trimStartMs, validTrimEnd)
                        },
                        enabled = canExportMp4,
                        colors =
                        ButtonDefaults.buttonColors(
                            containerColor = ProCcdPalette.WarmAmber,
                            contentColor = ProCcdPalette.VoidObsidian
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isBusy) "TRANSCODING MP4..." else "EXPORT MP4",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = fonts.condensed
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onPlayOffline,
                    border = BorderStroke(1.dp, ProCcdPalette.WarmAmber),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = ProCcdPalette.WarmAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "OPEN IN PLAYER",
                        color = ProCcdPalette.WarmAmber,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fonts.condensed
                    )
                }
                OutlinedButton(
                    onClick = onShareOffline,
                    border = BorderStroke(1.dp, ProCcdPalette.LedCyan),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Rounded.Share,
                        contentDescription = null,
                        tint = ProCcdPalette.LedCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("SHARE CLIP", color = ProCcdPalette.LedCyan, fontSize = 11.sp, fontFamily = fonts.condensed)
                }
            }

            Surface(
                color = ProCcdPalette.HardwareSlate,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, ProCcdPalette.BorderMetallic)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HardwareSectionLabel(text = "VIDEO METADATA & NOTES")
                    ExifSpecRow("CAMERA", "${activeProfile.codeName} (${activeProfile.subtitle})")
                    ExifSpecRow("DURATION", DateStampFormatter.formatTimecode(durationMs))
                    ExifSpecRow("RECORDED", formatEpochTimestamp(entity.capturedAtEpochMs))
                    ExifSpecRow("MEDIASTORE URI", entity.mediaUri?.takeLast(36) ?: "Not linked")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = captionText,
                            onValueChange = { captionText = it.take(GalleryRecipeReducer.MAX_CAPTION_CHARS) },
                            placeholder = { Text("Add clip note...", fontSize = 12.sp) },
                            singleLine = true,
                            colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ProCcdPalette.WarmAmber,
                                unfocusedBorderColor = ProCcdPalette.BorderMetallic,
                                focusedTextColor = ProCcdPalette.CreamInk,
                                unfocusedTextColor = ProCcdPalette.CreamInk
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { onSaveCaption(captionText) },
                            colors =
                            ButtonDefaults.buttonColors(
                                containerColor = ProCcdPalette.WarmAmber,
                                contentColor = ProCcdPalette.VoidObsidian
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Rounded.Save, contentDescription = "Save", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = onSoftDelete,
                border = BorderStroke(1.dp, ProCcdPalette.LedRed),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = null, tint = ProCcdPalette.LedRed)
                Spacer(modifier = Modifier.width(6.dp))
                Text("MOVE TO TRASH", color = ProCcdPalette.LedRed, fontSize = 11.sp, fontFamily = fonts.condensed)
            }
        }
    }
}

@Composable
internal fun ImportModeDialog(onDismiss: () -> Unit, onPickSinglePhoto: () -> Unit, onPickMultiPhoto: () -> Unit) {
    val fonts = LocalProCcdFonts.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ProCcdPalette.HardwareSlate,
        titleContentColor = ProCcdPalette.CreamInk,
        textContentColor = ProCcdPalette.TextSecondary,
        title = {
            Text(
                text = "IMPORT TO DARKROOM",
                fontFamily = fonts.condensed,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ProCcdPalette.VoidObsidian,
                    border = BorderStroke(1.dp, ProCcdPalette.WarmAmber),
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onPickSinglePhoto)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, tint = ProCcdPalette.WarmAmber)
                        Column {
                            Text(
                                "SINGLE PHOTO -> STUDIO",
                                color = ProCcdPalette.CreamInk,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = fonts.condensed
                            )
                            Text(
                                "Open 1 photo in the real-time 6-tab ProCCD Photo Studio.",
                                color = ProCcdPalette.TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ProCcdPalette.VoidObsidian,
                    border = BorderStroke(1.dp, ProCcdPalette.LedCyan),
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onPickMultiPhoto)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Rounded.AutoAwesomeMotion,
                            contentDescription = null,
                            tint = ProCcdPalette.LedCyan
                        )
                        Column {
                            Text(
                                "2-4 SHOTS -> BOOTH / D3D",
                                color = ProCcdPalette.CreamInk,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = fonts.condensed
                            )
                            Text(
                                "Assemble 4-strip Photo Booth print or D3D Wigglegram GIF.",
                                color = ProCcdPalette.TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = ProCcdPalette.TextSecondary, fontFamily = fonts.condensed)
            }
        }
    )
}

@Composable
internal fun MultiShotChoiceDialog(
    shotCount: Int,
    onDismiss: () -> Unit,
    onChooseBooth: () -> Unit,
    onChooseD3d: () -> Unit
) {
    val fonts = LocalProCcdFonts.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ProCcdPalette.HardwareSlate,
        titleContentColor = ProCcdPalette.CreamInk,
        textContentColor = ProCcdPalette.TextSecondary,
        title = {
            Text(
                text = "$shotCount SHOTS IMPORTED",
                fontFamily = fonts.condensed,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ProCcdPalette.VoidObsidian,
                    border = BorderStroke(1.dp, ProCcdPalette.LedCyan),
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onChooseBooth)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.ViewAgenda, contentDescription = null, tint = ProCcdPalette.LedCyan)
                        Column {
                            Text(
                                "PHOTO BOOTH 4-STRIP",
                                color = ProCcdPalette.CreamInk,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = fonts.condensed
                            )
                            Text(
                                "Composite shots into classic 4-panel booth strip print.",
                                color = ProCcdPalette.TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ProCcdPalette.VoidObsidian,
                    border = BorderStroke(1.dp, ProCcdPalette.WarmAmber),
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onChooseD3d)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.GifBox, contentDescription = null, tint = ProCcdPalette.WarmAmber)
                        Column {
                            Text(
                                "D3D WIGGLEGRAM STUDIO",
                                color = ProCcdPalette.CreamInk,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = fonts.condensed
                            )
                            Text(
                                "Animate shots into a looping ping-pong 3D GIF.",
                                color = ProCcdPalette.TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = ProCcdPalette.TextSecondary, fontFamily = fonts.condensed)
            }
        }
    )
}

@Composable
private fun StudioEmptyState(
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fonts = LocalProCcdFonts.current
    Box(
        modifier = modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = ProCcdPalette.HardwareSlate,
            border = BorderStroke(1.dp, ProCcdPalette.BorderMetallic)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Collections,
                    contentDescription = null,
                    tint = ProCcdPalette.WarmAmber,
                    modifier = Modifier.size(34.dp)
                )
                Text(
                    text = title,
                    color = ProCcdPalette.CreamInk,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fonts.condensed
                )
                Text(
                    text = subtitle,
                    color = ProCcdPalette.TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onAction,
                    border = BorderStroke(1.dp, ProCcdPalette.WarmAmber),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AddPhotoAlternate,
                        contentDescription = null,
                        tint = ProCcdPalette.WarmAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = actionLabel,
                        color = ProCcdPalette.WarmAmber,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fonts.condensed
                    )
                }
            }
        }
    }
}

internal fun formatEpochTimestamp(epochMs: Long): String = Instant
    .ofEpochMilli(epochMs)
    .atZone(ZoneId.systemDefault())
    .format(cardTimestampFormatter)
