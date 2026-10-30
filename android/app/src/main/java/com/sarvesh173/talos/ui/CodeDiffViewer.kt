package com.sarvesh173.talos.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.network.TalosClient

data class FileDiffItem(
    val filename: String,
    val path: String,
    val additions: Int,
    val deletions: Int,
    val diffLines: List<DiffLine>
)

data class DiffLine(
    val type: DiffLineType,
    val oldLineNum: Int? = null,
    val newLineNum: Int? = null,
    val content: String
)

enum class DiffLineType {
    ADDED, DELETED, CONTEXT, HEADER
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeDiffViewer(
    client: TalosClient,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val sampleFiles = remember {
        listOf(
            FileDiffItem(
                filename = "MainActivity.kt",
                path = "android/app/src/main/java/com/sarvesh173/talos/MainActivity.kt",
                additions = 142,
                deletions = 48,
                diffLines = listOf(
                    DiffLine(DiffLineType.HEADER, content = "@@ -980,18 +980,24 @@ Floating Action Dial"),
                    DiffLine(DiffLineType.CONTEXT, 980, 980, "val isSendMode = inputText.isNotBlank()"),
                    DiffLine(DiffLineType.DELETED, 981, null, "- var isCameraTriggered by remember { mutableStateOf(false) }"),
                    DiffLine(DiffLineType.ADDED, null, 981, "+ var isRecordingMode by remember { mutableStateOf(false) }"),
                    DiffLine(DiffLineType.ADDED, null, 982, "+ var isRecordingActive by remember { mutableStateOf(false) }"),
                    DiffLine(DiffLineType.ADDED, null, 983, "+ var isRecordingLocked by remember { mutableStateOf(false) }"),
                    DiffLine(DiffLineType.CONTEXT, 982, 984, "val sendScale by animateFloatAsState("),
                    DiffLine(DiffLineType.DELETED, 983, null, "- targetValue = if (isSendPressed) 0.96f else 1.0f"),
                    DiffLine(DiffLineType.ADDED, null, 985, "+ targetValue = if (isSendPressed || isRecordingActive) 1.08f else 1.0f"),
                    DiffLine(DiffLineType.CONTEXT, 984, 986, "    animationSpec = M3EMotion.fastSpatial()"),
                    DiffLine(DiffLineType.CONTEXT, 985, 987, ")")
                )
            ),
            FileDiffItem(
                filename = "Theme.kt",
                path = "android/app/src/main/java/com/sarvesh173/talos/ui/Theme.kt",
                additions = 24,
                deletions = 18,
                diffLines = listOf(
                    DiffLine(DiffLineType.HEADER, content = "@@ -55,10 +55,10 @@ DarkColorScheme"),
                    DiffLine(DiffLineType.DELETED, 55, null, "- primary = Color(0xFFE0A261), // Molten Copper"),
                    DiffLine(DiffLineType.ADDED, null, 55, "+ primary = Color(0xFFF59E0B), // Luminous Molten Amber"),
                    DiffLine(DiffLineType.DELETED, 59, null, "- secondary = Color(0xFFB9C0C8), // Machined Steel"),
                    DiffLine(DiffLineType.ADDED, null, 59, "+ secondary = Color(0xFF94A3B8), // Cool Slate Titanium"),
                    DiffLine(DiffLineType.DELETED, 63, null, "- tertiary = Color(0xFF7FC8BB), // Verdigris Patina"),
                    DiffLine(DiffLineType.ADDED, null, 63, "+ tertiary = Color(0xFF2DD4BF), // Electric Verdigris")
                )
            ),
            FileDiffItem(
                filename = "FEATURE.md",
                path = "FEATURE.md",
                additions = 38,
                deletions = 0,
                diffLines = listOf(
                    DiffLine(DiffLineType.HEADER, content = "@@ -880,6 +880,12 @@ Section 50"),
                    DiffLine(DiffLineType.ADDED, null, 880, "+ ## 50. Material 3 Expressive Suite: Multi-Agent Spatial Canvas"),
                    DiffLine(DiffLineType.ADDED, null, 881, "+ - [x] Multi-Agent Spatial Canvas Screen"),
                    DiffLine(DiffLineType.ADDED, null, 882, "+ - [x] Code Diff Viewer & File Inspector"),
                    DiffLine(DiffLineType.ADDED, null, 883, "+ - [x] Attachment Bottom Sheet with Runtime Permissions")
                )
            )
        )
    }

    var selectedFileIndex by remember { mutableIntStateOf(0) }
    val currentFile = sampleFiles[selectedFileIndex]

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Code Changes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "branch: feature/m3-expressive-rust-first",
                            style = MaterialTheme.typography.bodySmall,
                            color = PrimaryAccent
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
                    IconButton(onClick = {
                        val fullDiff = currentFile.diffLines.joinToString("\n") { it.content }
                        clipboardManager.setText(AnnotatedString(fullDiff))
                        Toast.makeText(context, "Diff copied to clipboard!", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Diff",
                            tint = PrimaryAccent
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
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
                items(sampleFiles.indices.toList()) { index ->
                    val file = sampleFiles[index]
                    val isSelected = index == selectedFileIndex
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) PrimaryAccent.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) PrimaryAccent else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.clickable { selectedFileIndex = index }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = file.filename,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) PrimaryAccent else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "+${file.additions}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF4ADE80)
                            )
                            if (file.deletions > 0) {
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "-${file.deletions}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFF87171)
                                )
                            }
                        }
                    }
                }
            }

            // Path indicator
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

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 8.dp)
                        .horizontalScroll(horizontalScroll)
                ) {
                    items(currentFile.diffLines) { line ->
                        val (bgColor, textColor) = when (line.type) {
                            DiffLineType.ADDED -> Color(0xFF132F24) to Color(0xFF4ADE80)
                            DiffLineType.DELETED -> Color(0xFF3B1E22) to Color(0xFFF87171)
                            DiffLineType.HEADER -> Color(0xFF1E293B) to Color(0xFF93C5FD)
                            DiffLineType.CONTEXT -> Color.Transparent to MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(bgColor)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            // Line number gutter
                            val numStr = when (line.type) {
                                DiffLineType.ADDED -> "+${line.newLineNum ?: ""}".padEnd(5)
                                DiffLineType.DELETED -> "-${line.oldLineNum ?: ""}".padEnd(5)
                                DiffLineType.CONTEXT -> "${line.newLineNum ?: ""}".padEnd(5)
                                DiffLineType.HEADER -> "@@".padEnd(5)
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
