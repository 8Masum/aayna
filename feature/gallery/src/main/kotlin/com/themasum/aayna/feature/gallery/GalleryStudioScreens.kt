package com.themasum.aayna.feature.gallery

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.themasum.aayna.core.model.CameraCatalog
import com.themasum.aayna.core.storage.db.FilmRollEntity
import com.themasum.aayna.core.storage.db.NegativeEntity
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val cardTimestampFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yy.MM.dd HH:mm", Locale.US)

@Composable
internal fun NegativesLabGrid(
    negatives: List<NegativeEntity>,
    onOpenNegative: (NegativeEntity) -> Unit,
    onDeleteNegative: (NegativeEntity) -> Unit,
    onImportPhotoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingDelete by remember { mutableStateOf<NegativeEntity?>(null) }

    if (negatives.isEmpty()) {
        StudioEmptyState(
            title = "NO NEGATIVES IN DARKROOM",
            subtitle = "Shoot in Non-Destructive Roll mode or import any gallery photo to grade non-destructively.",
            actionLabel = "IMPORT PHOTO",
            onAction = onImportPhotoClick,
            modifier = modifier
        )
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 156.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(negatives, key = { it.recipeId }) { entity ->
            NegativeLabCard(
                entity = entity,
                onClick = { onOpenNegative(entity) },
                onDeleteClick = { pendingDelete = entity }
            )
        }
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = ProCcdPalette.SurfaceDark,
            titleContentColor = ProCcdPalette.TextPrimary,
            textContentColor = ProCcdPalette.TextSecondary,
            title = {
                Text(
                    text = "Delete Negative?",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This permanently removes the clean negative and its non-destructive recipe from Aayna."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onDeleteNegative(toDelete)
                    }
                ) {
                    Text("DELETE", color = ProCcdPalette.LedRed, fontFamily = FontFamily.Monospace)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("CANCEL", color = ProCcdPalette.TextSecondary, fontFamily = FontFamily.Monospace)
                }
            }
        )
    }
}

@Composable
private fun NegativeLabCard(entity: NegativeEntity, onClick: () -> Unit, onDeleteClick: () -> Unit) {
    val profile = remember(entity.cameraId) { CameraCatalog.findByIdOrDefault(entity.cameraId) }
    val formattedTime = remember(entity.capturedAtEpochMs) { formatEpochTimestamp(entity.capturedAtEpochMs) }
    val thumbBitmap by produceState<Bitmap?>(initialValue = null, entity.thumbnailPath) {
        value =
            withContext(Dispatchers.IO) {
                val file = File(entity.thumbnailPath)
                if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
            }
    }

    Surface(
        modifier =
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = ProCcdPalette.SurfaceDark,
        border = BorderStroke(1.dp, ProCcdPalette.BorderMetallic)
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
                        fontFamily = FontFamily.Monospace
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
                            accentColor = ProCcdPalette.LedAmber,
                            filledDot = false
                        )
                    }
                }

                if (entity.exportedUri != null) {
                    Box(
                        modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                    ) {
                        LedStatusPill(label = "EXPORTED", accentColor = ProCcdPalette.LedEmerald)
                    }
                }

                IconButton(
                    onClick = onDeleteClick,
                    modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(ProCcdPalette.BgCharcoal.copy(alpha = 0.72f))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Delete Negative",
                        tint = ProCcdPalette.TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Row(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedTime,
                    color = ProCcdPalette.TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun FilmRollsView(
    rolls: List<FilmRollEntity>,
    selectedRoll: FilmRollEntity?,
    selectedRollFrames: List<NegativeEntity>,
    onSelectRoll: (FilmRollEntity?) -> Unit,
    onLoadNewRoll: () -> Unit,
    onOpenFrame: (NegativeEntity) -> Unit,
    onDeleteFrame: (NegativeEntity) -> Unit,
    onImportPhotoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selectedRoll != null) {
        ContactSheetView(
            roll = selectedRoll,
            frames = selectedRollFrames,
            onBackToRolls = { onSelectRoll(null) },
            onOpenFrame = onOpenFrame,
            onDeleteFrame = onDeleteFrame,
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
                .background(ProCcdPalette.SurfaceDark)
                .border(1.dp, ProCcdPalette.BorderMetallic)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ANALOG FILM ROLLS",
                    color = ProCcdPalette.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "${rolls.size} roll(s) logged",
                    color = ProCcdPalette.TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Button(
                onClick = onLoadNewRoll,
                colors =
                ButtonDefaults.buttonColors(
                    containerColor = ProCcdPalette.LedAmber,
                    contentColor = ProCcdPalette.BgCharcoal
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
                    fontFamily = FontFamily.Monospace
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
        color = ProCcdPalette.SurfaceDark,
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
                        color = ProCcdPalette.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                LedStatusPill(
                    label = if (roll.isActive) "ACTIVE" else "DEVELOPED",
                    accentColor = if (roll.isActive) ProCcdPalette.LedEmerald else ProCcdPalette.TextSecondary
                )
            }

            LinearProgressIndicator(
                progress = { progress },
                color = if (roll.isActive) ProCcdPalette.LedEmerald else ProCcdPalette.LedAmber,
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
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "TAP FOR CONTACT SHEET >",
                    color = ProCcdPalette.LedAmber,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun ContactSheetView(
    roll: FilmRollEntity,
    frames: List<NegativeEntity>,
    onBackToRolls: () -> Unit,
    onOpenFrame: (NegativeEntity) -> Unit,
    onDeleteFrame: (NegativeEntity) -> Unit,
    onImportPhotoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val profile = remember(roll.cameraId) { CameraCatalog.findByIdOrDefault(roll.cameraId) }
    Column(modifier = modifier.fillMaxSize()) {
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
                IconButton(onClick = onBackToRolls) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back to Rolls",
                        tint = ProCcdPalette.TextPrimary
                    )
                }
                Column {
                    Text(
                        text = "CONTACT SHEET • ${profile.codeName}",
                        color = ProCcdPalette.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "${roll.shotsTaken}/${roll.capacity} EXPOSURES • ${frames.size} FRAMES",
                        color = ProCcdPalette.TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
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
            NegativesLabGrid(
                negatives = frames,
                onOpenNegative = onOpenFrame,
                onDeleteNegative = onDeleteFrame,
                onImportPhotoClick = onImportPhotoClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StudioEmptyState(
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = ProCcdPalette.SurfaceDark,
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
                    tint = ProCcdPalette.LedAmber,
                    modifier = Modifier.size(34.dp)
                )
                Text(
                    text = title,
                    color = ProCcdPalette.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = subtitle,
                    color = ProCcdPalette.TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onAction,
                    border = BorderStroke(1.dp, ProCcdPalette.LedAmber),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AddPhotoAlternate,
                        contentDescription = null,
                        tint = ProCcdPalette.LedAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = actionLabel,
                        color = ProCcdPalette.LedAmber,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

private fun formatEpochTimestamp(epochMs: Long): String = Instant
    .ofEpochMilli(epochMs)
    .atZone(ZoneId.systemDefault())
    .format(cardTimestampFormatter)
