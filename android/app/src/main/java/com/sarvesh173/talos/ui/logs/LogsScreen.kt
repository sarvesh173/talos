package com.sarvesh173.talos.ui.logs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.ui.TalosIcons
import com.sarvesh173.talos.ui.applePressable
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Crash reports on the device.
 *
 * The point of this screen is the handoff: open a crash, copy it, send it. A log
 * that only exists on the phone is a log nobody can act on.
 *
 * Reading is off the main thread — a big logcat tail is not a main-thread read.
 */
@Composable
fun LogsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var files by remember { mutableStateOf(CrashLogger.list(context)) }
    var open by remember { mutableStateOf<File?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .applePressable(0.94f) { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = TalosIcons.TalosBackArrow,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Logs",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.25).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when (files.size) {
                            0 -> "No crashes recorded"
                            1 -> "1 crash report"
                            else -> "${files.size} crash reports"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (files.isNotEmpty()) {
                    Text(
                        text = "Clear all",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { confirmClear = true }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }

            if (files.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Nothing has crashed",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "If the app closes unexpectedly, the report lands here and you can send it.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, bottom = 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(files, key = { it.name }) { file ->
                        LogRow(file = file, onClick = { open = file })
                    }
                }
            }
        }
    }

    open?.let { file ->
        LogDetailSheet(
            file = file,
            onClose = { open = null },
            onDeleted = {
                open = null
                files = CrashLogger.list(context)
            }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Delete all crash reports?") },
            text = { Text("This cannot be undone. Send anything you still need first.") },
            confirmButton = {
                TextButton(onClick = {
                    CrashLogger.clear(context)
                    files = emptyList()
                    confirmClear = false
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun LogRow(file: File, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable { onClick() },
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name.removePrefix("crash-").removeSuffix(".txt"),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${file.length() / 1024} KB · ${when {
                        System.currentTimeMillis() - file.lastModified() < 60_000 -> "just now"
                        else -> SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
                    }}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "Open",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Full text, with copy and share.
 *
 * Selection is deliberately not used: the whole report is what gets sent, and
 * partial selection on a 400-line log is a way to lose the stack trace.
 */
@Composable
private fun LogDetailSheet(file: File, onClose: () -> Unit, onDeleted: () -> Unit) {
    val context = LocalContext.current
    var text by remember(file.name) { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(file.name) {
        text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            CrashLogger.read(context, file)
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Column {
                Text(
                    text = "Crash report",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(modifier = Modifier.height(360.dp).fillMaxWidth()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    // verticalScroll (not a nested LazyColumn): this sits inside an
                    // AlertDialog, which is not a lazy layout.
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(10.dp)
                    ) {
                        Text(
                            text = text ?: "Reading…",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                copy(context, file, text)
                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
            }) { Text("Copy") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { CrashLogger.share(context, file) }) { Text("Share") }
                TextButton(onClick = {
                    file.delete()
                    onDeleted()
                }) { Text("Delete") }
            }
        }
    )
}

private fun copy(context: Context, file: File, text: String?) {
    val body = text ?: CrashLogger.read(context, file)
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("TALOS ${file.name}", body))
}