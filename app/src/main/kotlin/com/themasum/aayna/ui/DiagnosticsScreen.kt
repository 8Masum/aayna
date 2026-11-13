package com.themasum.aayna.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.themasum.aayna.BuildConfig
import com.themasum.aayna.R
import com.themasum.aayna.core.camera.AaynaCameraController
import com.themasum.aayna.core.gl.OffscreenGlExporter
import com.themasum.aayna.core.model.CameraCatalog
import com.themasum.aayna.core.model.GlDiagnosticsInfo
import com.themasum.aayna.core.model.PassShaderStatus
import com.themasum.aayna.core.storage.AaynaLogger
import com.themasum.aayna.core.storage.AaynaPreferencesRepository
import com.themasum.aayna.ui.theme.AaynaColors
import kotlinx.coroutines.flow.StateFlow

@Composable
fun DiagnosticsScreen(
    preferencesRepository: AaynaPreferencesRepository,
    cameraController: AaynaCameraController,
    offscreenExporter: OffscreenGlExporter,
    glDiagnosticsFlow: StateFlow<Pair<GlDiagnosticsInfo?, List<PassShaderStatus>>>,
    logger: AaynaLogger,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val shareChooserTitle = stringResource(R.string.share_diagnostics_chooser)
    val activeProfileId by preferencesRepository.activeCameraIdFlow.collectAsStateWithLifecycle(
        initialValue = CameraCatalog.defaultCamera.id
    )
    val cameraCapability by cameraController.activeCapabilityInfo.collectAsStateWithLifecycle()
    val liveGlDiagnostics by glDiagnosticsFlow.collectAsStateWithLifecycle()

    val (resolvedGlInfo, resolvedShaders) =
        remember(liveGlDiagnostics, offscreenExporter) {
            val (liveInfo, liveShaders) = liveGlDiagnostics
            if (liveInfo != null) {
                liveInfo to liveShaders
            } else {
                offscreenExporter.getDiagnostics()
            }
        }

    val report =
        remember(activeProfileId, cameraCapability, resolvedGlInfo, resolvedShaders) {
            DiagnosticsReportBuilder.build(
                appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                deviceModel = Build.MODEL.orEmpty().ifBlank { "Unknown" },
                androidSdk = Build.VERSION.SDK_INT,
                activeCameraProfileId = activeProfileId,
                cameraCapability = cameraCapability,
                glInfo = resolvedGlInfo,
                shaderStatuses = resolvedShaders
            )
        }

    var logText by remember { mutableStateOf(logger.readAllLogs()) }
    var actionFeedback by remember { mutableStateOf<String?>(null) }

    val fullPayload =
        remember(report, logText) {
            buildString {
                appendLine(report.toShareableText())
                appendLine("[Rolling App Log]")
                append(logText.ifBlank { "(No log entries recorded)" })
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
            title = "CAMERA2 & GL DIAGNOSTICS",
            onBack = onBack
        )

        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DiagnosticActionButton(
                    label = "COPY REPORT",
                    accentColor = AaynaColors.AmberLed,
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Aayna Diagnostics", fullPayload))
                        actionFeedback = "Copied diagnostics & log to clipboard."
                    },
                    modifier = Modifier.weight(1f)
                )
                DiagnosticActionButton(
                    label = "SHARE LOG",
                    accentColor = AaynaColors.CyanLed,
                    onClick = {
                        val sendIntent =
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Project Aayna Diagnostics")
                                putExtra(Intent.EXTRA_TEXT, fullPayload)
                            }
                        val chooser =
                            Intent.createChooser(
                                sendIntent,
                                shareChooserTitle
                            )
                        context.startActivity(chooser)
                    },
                    modifier = Modifier.weight(1f)
                )
                DiagnosticActionButton(
                    label = "CLEAR LOG",
                    accentColor = AaynaColors.RedLed,
                    onClick = {
                        logger.clear()
                        logText = logger.readAllLogs()
                        actionFeedback = "Cleared rolling log."
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            actionFeedback?.let { status ->
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelMedium,
                    color = AaynaColors.EmeraldLcd
                )
            }

            ConsoleSection(title = "BUILD & DEVICE") {
                DiagnosticKeyValue("App Version", report.appVersion)
                DiagnosticKeyValue("Device Model", "${report.deviceModel} (API ${report.androidSdk})")
                DiagnosticKeyValue("Active Profile", report.activeCameraProfileId)
            }

            ConsoleSection(title = "CAMERA2 CAPABILITY") {
                val cap = report.cameraCapability
                DiagnosticKeyValue("Camera ID", cap.cameraId)
                DiagnosticKeyValue("Hardware Level", cap.hardwareLevel)
                DiagnosticKeyValue("Manual Sensor", cap.hasManualSensor.toString())
                DiagnosticKeyValue("ISO Range", cap.isoRange)
                DiagnosticKeyValue("Exposure Range (ns)", cap.exposureRangeNs)
                DiagnosticKeyValue("Edge Mode Applied", cap.edgeModeApplied)
                DiagnosticKeyValue("Noise Reduction", cap.noiseReductionApplied)
            }

            ConsoleSection(title = "OPENGL ES PIPELINE") {
                val gl = report.glInfo
                DiagnosticKeyValue("Vendor", gl.glVendor)
                DiagnosticKeyValue("Renderer", gl.glRenderer)
                DiagnosticKeyValue("GL Version", gl.glVersion)
                DiagnosticKeyValue("Max Texture Size", gl.maxTextureSize.toString())
                DiagnosticKeyValue("Target FBO", gl.targetFboSize)
            }

            ConsoleSection(title = "SHADER PASS STATUS") {
                if (report.shaderStatuses.isEmpty()) {
                    Text(
                        text = "No shader passes reported yet. Open Viewfinder or render a frame.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AaynaColors.MutedText
                    )
                } else {
                    report.shaderStatuses.forEach { pass ->
                        ShaderStatusRow(pass = pass)
                    }
                }
            }

            ConsoleSection(title = "ROLLING APP LOG") {
                Box(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF0B0C0E))
                        .border(1.dp, AaynaColors.MetallicBorder, RoundedCornerShape(6.dp))
                        .horizontalScroll(rememberScrollState())
                        .padding(10.dp)
                ) {
                    Text(
                        text = logText.ifBlank { "(No log entries recorded)" },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = AaynaColors.SilverText
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticActionButton(
    label: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.7f)),
        colors =
        ButtonDefaults.outlinedButtonColors(
            containerColor = AaynaColors.CharcoalElevated,
            contentColor = accentColor
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1
        )
    }
}

@Composable
private fun DiagnosticKeyValue(key: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.bodySmall,
            color = AaynaColors.MutedText,
            modifier = Modifier.weight(0.44f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = AaynaColors.SilverText,
            modifier = Modifier.weight(0.56f)
        )
    }
}

@Composable
private fun ShaderStatusRow(pass: PassShaderStatus) {
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(AaynaColors.CharcoalBg)
            .border(1.dp, AaynaColors.MetallicBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = pass.passName,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = AaynaColors.SilverText
            )
            Text(
                text = if (pass.compiled) "OK" else "FAIL",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (pass.compiled) AaynaColors.EmeraldLcd else AaynaColors.RedLed
            )
        }
        if (!pass.compiled && pass.errorLog.isNotBlank()) {
            Text(
                text = pass.errorLog,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = AaynaColors.RedLed
            )
        }
    }
}
