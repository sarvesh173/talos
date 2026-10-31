package com.sarvesh173.talos.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.network.TalosClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeDiffViewer(
    client: TalosClient,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val motion = LocalTalosMotion.current
    val clipboardManager = LocalClipboardManager.current
    val diffSummary by client.gitDiff.collectAsState()
    var selectedFileIndex by remember { mutableIntStateOf(0) }
    var showOnlyChanges by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        client.fetchGitDiff()
    }

    val currentDiff = diffSummary
    val files = currentDiff?.files ?: emptyList()
    val currentFile = files.getOrNull(selectedFileIndex) ?: files.firstOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Code & File Changes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (currentDiff != null) {
                                "branch: ${currentDiff.branch} • +${currentDiff.total_additions} -${currentDiff.total_deletions}"
                            } else {
                                "Inspecting git index..."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { client.fetchGitDiff() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Diff",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (currentFile != null) {
                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val fullDiff = currentFile.lines.joinToString("\n") { it.content }
                            clipboardManager.setText(AnnotatedString(fullDiff))
                            Toast.makeText(context, "File diff copied to clipboard!", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Diff",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        when {
            currentDiff == null -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        MetallicCircularLoader(
                            size = 44.dp,
                            strokeWidth = 3.5.dp,
                            accentColor = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Parsing git working tree diff...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            files.isEmpty() -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(StatusGreen.copy(alpha = 0.15f))
                                .border(1.dp, StatusGreen.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = StatusGreen,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Working Tree Clean",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "All recent code changes have been committed. Branch '${currentDiff.branch}' is synced with Git HEAD.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
            currentFile != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    // File Selector Tabs
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(files.indices.toList()) { index ->
                            val file = files[index]
                            val isSelected = index == selectedFileIndex
                            val scale by animateFloatAsState(
                                targetValue = if (isSelected) 1.02f else 1.0f,
                                animationSpec = spring(
                                    dampingRatio = motion.spatialFastDamping,
                                    stiffness = motion.spatialFastStiffness
                                ),
                                label = "fileTabScale"
                            )
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .scale(scale)
                                    .clickable {
                                        selectedFileIndex = index
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = file.filename,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "+${file.additions}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = StatusGreen
                                    )
                                    if (file.deletions > 0) {
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "-${file.deletions}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = StatusRed
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Diff Display Filter Row (All Lines vs Changes Only)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        FilterChip(
                            selected = !showOnlyChanges,
                            onClick = {
                                showOnlyChanges = false
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            label = { Text("All Lines (${currentFile.lines.size})", fontSize = 11.sp) }
                        )
                        val changeCount = currentFile.lines.count { it.line_type == "added" || it.line_type == "deleted" }
                        FilterChip(
                            selected = showOnlyChanges,
                            onClick = {
                                showOnlyChanges = true
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            label = { Text("Changes Only ($changeCount)", fontSize = 11.sp) }
                        )
                    }

                    // Path Indicator
                    Text(
                        text = currentFile.path,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )

                    // Syntax Diff Box
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(16.dp)
                    ) {
                        val horizontalScroll = rememberScrollState()
                        val displayedLines = if (showOnlyChanges) {
                            currentFile.lines.filter { it.line_type == "added" || it.line_type == "deleted" || it.line_type == "header" }
                        } else {
                            currentFile.lines
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 8.dp)
                                .horizontalScroll(horizontalScroll)
                        ) {
                            items(displayedLines) { line ->
                                val (bgColor, textColor) = when (line.line_type) {
                                    "added" -> StatusGreen.copy(alpha = 0.10f) to StatusGreen
                                    "deleted" -> StatusRed.copy(alpha = 0.10f) to StatusRed
                                    "header" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) to MaterialTheme.colorScheme.primary
                                    else -> Color.Transparent to MaterialTheme.colorScheme.onSurfaceVariant
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(bgColor)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    val numStr = when (line.line_type) {
                                        "added" -> "+${line.new_num ?: ""}".padEnd(5)
                                        "deleted" -> "-${line.old_num ?: ""}".padEnd(5)
                                        "header" -> "@@".padEnd(5)
                                        else -> "${line.new_num ?: ""}".padEnd(5)
                                    }
                                    Text(
                                        text = numStr,
                                        color = MaterialTheme.colorScheme.outline,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.width(36.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = line.content,
                                        color = textColor,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
