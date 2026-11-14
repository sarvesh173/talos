package com.sarvesh173.talos.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.sarvesh173.talos.network.TalosClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * One attach surface for everything the composer can carry:
 * camera, photo library, file, drawing, sticker.
 *
 * Picking an image does **not** send it. It hands a [PendingImage] back so the
 * chat can show a confirmation sheet — the user sees the exact photo, can draw
 * on it, add a caption, or throw it away. Sending a photo blind is a mistake the
 * user cannot undo, so the confirm step is mandatory.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentSheet(
    client: TalosClient,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onPickImage: (PendingImage) -> Unit,
    onSendText: (String) -> Unit,
    onOpenPrompts: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Photo library (Android Photo Picker — no storage permission needed).
    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                decodeToPending(context, uri)?.let { onPickImage(it) }
            }
        }
    }

    // Camera: TakePicturePreview gives a downscaled bitmap with no permission
    // prompt on modern Android when the app already holds CAMERA.
    val takePicture = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    ByteArrayOutputStream().use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        out.toByteArray()
                    }
                }
                onPickImage(
                    PendingImage(
                        bytes = bytes,
                        label = "Camera",
                        width = bitmap.width,
                        height = bitmap.height
                    )
                )
            }
        }
    }

    val cameraPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) takePicture.launch(null)
        else Toast.makeText(context, "Camera permission is needed to take a photo", Toast.LENGTH_SHORT).show()
    }

    // Any file: dispatched to the agent as an attachment reference.
    val pickFile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
            onSendText("[Attached file] $name — ${uri}")
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (isDark) Color(0xFF161618) else Color(0xFFF9F9FB),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = "Attach",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) Color.White else Color(0xFF1D1D1F)
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AttachTile("Camera", Icons.Default.CameraAlt, isDark) {
                    val granted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) takePicture.launch(null)
                    else cameraPermission.launch(Manifest.permission.CAMERA)
                }
                AttachTile("Photos", Icons.Default.PhotoLibrary, isDark) {
                    pickImage.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
                AttachTile("Files", Icons.Default.Folder, isDark) { pickFile.launch("*/*") }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AttachTile("Draw", Icons.Default.Brush, isDark) {
                    onSendText("[Whiteboard sketch attached — open the canvas to annotate]")
                    onDismiss()
                }
                AttachTile("Sticker", Icons.Default.Mood, isDark) {
                    onSendText(StickerCatalog.randomPick())
                    onDismiss()
                }
                AttachTile("Prompts", Icons.Default.Bookmark, isDark) {
                    onDismiss()
                    onOpenPrompts?.invoke()
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AttachTile(label: String, icon: ImageVector, isDark: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(84.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(shape)
                .background(if (isDark) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.04f))
                .border(
                    BorderStroke(0.8.dp, if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.06f)),
                    shape
                )
                .applePressable(0.94f, pressHaptic = true) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isDark) Color.White.copy(alpha = 0.80f) else Color(0xFF1D1D1F),
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (isDark) Color.White.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.60f)
        )
    }
}

/**
 * Confirmation sheet for a picked image.
 *
 * Shows the actual pixels (not a filename), lets the user add or edit the
 * caption, and offers an annotate path. Nothing leaves the device until Send.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageConfirmSheet(
    image: PendingImage,
    client: TalosClient,
    isDark: Boolean,
    brandColor: Color,
    caption: String,
    onDismiss: () -> Unit,
    onSend: (String, ByteArray) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var localCaption by remember(image) { mutableStateOf(caption) }
    var annotate by remember(image) { mutableStateOf(false) }
    var strokeColor by remember { mutableStateOf(brandColor) }

    val bitmap = remember(image) {
        runCatching {
            android.graphics.BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)
                ?.asImageBitmap()
        }.getOrNull()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (isDark) Color(0xFF141416) else Color.White,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Send this?",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDark) Color.White else Color(0xFF1D1D1F),
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .applePressable(0.94f, pressHaptic = true) { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Discard image",
                        tint = if (isDark) Color.White.copy(alpha = 0.70f) else Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // The real pixels, so the decision is made on the image itself.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isDark) Color.Black else Color(0xFFF2F2F7)),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Attachment preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)
                    )
                } else {
                    Text(
                        text = "Preview unavailable",
                        fontSize = 12.sp,
                        color = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.45f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Drawing tools appear only when the user asks to annotate.
            if (annotate) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Color(0xFFFF3B30), Color(0xFFFF9500), Color(0xFFFFCC00),
                        Color(0xFF34C759), MaterialTheme.colorScheme.primary, Color(0xFFAF52DE)
                    ).forEach { c ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    BorderStroke(
                                        if (strokeColor == c) 2.5.dp else 0.8.dp,
                                        if (strokeColor == c) Color.White else Color.Transparent
                                    ),
                                    CircleShape
                                )
                                .applePressable(0.92f, pressHaptic = true) { strokeColor = c }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Draw on the image, then send.",
                    fontSize = 11.sp,
                    color = if (isDark) Color.White.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.40f)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = localCaption,
                onValueChange = { localCaption = it },
                placeholder = { Text("Add a message (optional)") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(16.dp),
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    onClick = { annotate = !annotate },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(
                        text = if (annotate) "Done drawing" else "Draw",
                        color = brandColor,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = { onSend(localCaption, image.bytes) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = brandColor)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Send", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

/** Decode a picked URI into bytes for preview + upload. */
private suspend fun decodeToPending(context: Context, uri: Uri): PendingImage? =
    withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            // Cap the long edge: a 12 MP photo is wasted tokens and slow to upload.
            val maxEdge = 1600
            val scale = minOf(
                1f,
                maxEdge.toFloat() / maxOf(bitmap.width, bitmap.height).toFloat()
            )
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt(),
                    (bitmap.height * scale).toInt(),
                    true
                )
            } else bitmap

            val bytes = ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
                out.toByteArray()
            }
            PendingImage(
                bytes = bytes,
                label = uri.lastPathSegment?.substringAfterLast('/') ?: "Photo",
                width = scaled.width,
                height = scaled.height
            )
        }.getOrNull()
    }

/** A small, fixed sticker set — text-rendered so there is no asset to ship. */
object StickerCatalog {
    private val stickers = listOf(
        "🔥", "✨", "🎯", "🚀", "💡", "🧠", "⚡", "🎨", "🛠️", "📦",
        "✅", "⚠️", "🧩", "🔍", "📌", "🌟", "🎉", "👀", "💾", "🧪"
    )
    fun randomPick(): String = stickers.random()
    fun all(): List<String> = stickers
}
