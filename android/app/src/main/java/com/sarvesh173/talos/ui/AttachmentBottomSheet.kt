package com.sarvesh173.talos.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.sarvesh173.talos.network.TalosClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentBottomSheet(
    client: TalosClient,
    onDismiss: () -> Unit,
    onSendText: (String) -> Unit
) {
    val context = LocalContext.current
    var showPermissionRationale by remember { mutableStateOf<String?>(null) }
    var showImageGenDialog by remember { mutableStateOf(false) }

    // (1) Camera Permission Launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            runCatching {
                context.startActivity(
                    Intent(MediaStore.ACTION_IMAGE_CAPTURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            onDismiss()
        } else {
            showPermissionRationale = "Camera access is needed to capture photos and context for TALOS agents."
        }
    }

    // (2) Audio Permission Launcher
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onSendText("/consensus [Voice Note Attached]")
            onDismiss()
        } else {
            showPermissionRationale = "Microphone access is needed for hands-free audio commands."
        }
    }

    // (3) Location Permission Launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onSendText("📍 Current Device Mesh Node: 100.75.107.79 (Tailscale Active)")
            onDismiss()
        } else {
            showPermissionRationale = "Location permission is needed to map mesh network proximity."
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Attach to Context",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "Feed live sensory or code context directly into your agent pipeline.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // Grid of Attachment Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AttachmentTile(
                    icon = Icons.Default.PhotoCamera,
                    title = "Camera",
                    color = PrimaryAccent
                ) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        runCatching {
                            context.startActivity(
                                Intent(MediaStore.ACTION_IMAGE_CAPTURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                        onDismiss()
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }

                AttachmentTile(
                    icon = Icons.Default.Mic,
                    title = "Voice Note",
                    color = MaterialTheme.colorScheme.tertiary
                ) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        onSendText("/consensus [Voice Note Attached]")
                        onDismiss()
                    } else {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }

                AttachmentTile(
                    icon = Icons.Default.Place,
                    title = "Location",
                    color = Color(0xFF60A5FA)
                ) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        onSendText("📍 Current Device Mesh Node: 100.75.107.79 (Tailscale Active)")
                        onDismiss()
                    } else {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                }

                AttachmentTile(
                    icon = Icons.Default.AutoAwesome,
                    title = "AI Image",
                    color = Color(0xFFA855F7)
                ) {
                    showImageGenDialog = true
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                AttachmentTile(
                    icon = Icons.Default.Code,
                    title = "Source Diff",
                    color = Color(0xFF34D399)
                ) {
                    onSendText("/status [Reviewing live git working tree]")
                    onDismiss()
                }

                Spacer(modifier = Modifier.width(24.dp))

                AttachmentTile(
                    icon = Icons.Default.Description,
                    title = "Document",
                    color = Color(0xFFF97316)
                ) {
                    onSendText("📄 Context attachment: FEATURE.md staged")
                    onDismiss()
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }

    // Permission Rationale Dialog
    if (showPermissionRationale != null) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = PrimaryAccent
                )
            },
            title = {
                Text(text = "Permission Required", style = MaterialTheme.typography.titleMedium)
            },
            text = {
                Text(text = showPermissionRationale ?: "", style = MaterialTheme.typography.bodyMedium)
            },
            confirmButton = {
                TextButton(onClick = { showPermissionRationale = null }) {
                    Text("OK", color = PrimaryAccent)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }

    // AI Image Generation Composer
    if (showImageGenDialog) {
        var imagePrompt by remember { mutableStateOf("") }
        var selectedStyle by remember { mutableStateOf("Cinematic") }
        val styles = listOf("Cinematic", "3D Render", "Pixel Art", "Anime")

        AlertDialog(
            onDismissRequest = { showImageGenDialog = false },
            icon = {
                Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFA855F7))
            },
            title = {
                Text(text = "Generate AI Image", style = MaterialTheme.typography.titleMedium)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Dispatches to Hermes multimodal generation endpoint.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = imagePrompt,
                        onValueChange = { imagePrompt = it },
                        placeholder = { Text("Describe the visual artifact...") },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        styles.forEach { style ->
                            val isSelected = selectedStyle == style
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedStyle = style },
                                label = { Text(style, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (imagePrompt.isNotBlank()) {
                            onSendText("/generate-image [Style: $selectedStyle] $imagePrompt")
                            showImageGenDialog = false
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
                ) {
                    Text("Generate", color = MaterialTheme.colorScheme.onPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImageGenDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }
}

@Composable
private fun AttachmentTile(
    icon: ImageVector,
    title: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(color.copy(alpha = 0.15f))
                .border(BorderStroke(1.dp, color.copy(alpha = 0.4f)), RoundedCornerShape(18.dp))
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}
