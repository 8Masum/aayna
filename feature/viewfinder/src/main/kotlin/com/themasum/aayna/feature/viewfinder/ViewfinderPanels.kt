package com.themasum.aayna.feature.viewfinder

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
