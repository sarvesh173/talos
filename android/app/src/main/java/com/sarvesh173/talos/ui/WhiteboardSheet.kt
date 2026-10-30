package com.sarvesh173.talos.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class StrokePath(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhiteboardSheet(
    onDismiss: () -> Unit,
    onAttachSketch: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val paths = remember { mutableStateListOf<StrokePath>() }
    val redoPaths = remember { mutableStateListOf<StrokePath>() }
    var currentPoints = remember { mutableStateListOf<Offset>() }
    val defaultColor = MaterialTheme.colorScheme.primary
    var currentColor by remember { mutableStateOf(defaultColor) }
    var strokeWidth by remember { mutableFloatStateOf(6f) }
    var activeTool by remember { mutableStateOf(OpenBoardTool.PEN) }
    var canvasBackground by remember { mutableStateOf("DotGrid") }

    val colorPalette = listOf(
        MaterialTheme.colorScheme.primary, // Signature Iris Purple
        Color(0xFF60A5FA), // Azure Blue
        Color(0xFF34D399), // Mint Emerald
        Color(0xFFF87171), // Coral Rose
        Color(0xFFFFFFFF)  // Chalk White
    )

    val effectiveColor = when (activeTool) {
        OpenBoardTool.PEN -> currentColor
        OpenBoardTool.HIGHLIGHTER -> currentColor.copy(alpha = 0.35f)
        OpenBoardTool.ERASER -> when (canvasBackground) {
            "DarkSlate" -> Color(0xFF1E293B)
            else -> Color(0xFF0F172A)
        }
    }
    val effectiveWidth = when (activeTool) {
        OpenBoardTool.PEN -> strokeWidth
        OpenBoardTool.HIGHLIGHTER -> 24f
        OpenBoardTool.ERASER -> 32f
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "OpenBoard Whiteboard",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${paths.size} strokes committed • ${activeTool.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Undo
                    IconButton(
                        onClick = {
                            if (paths.isNotEmpty()) {
                                val last = paths.removeAt(paths.size - 1)
                                redoPaths.add(last)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        enabled = paths.isNotEmpty(),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // Redo
                    IconButton(
                        onClick = {
                            if (redoPaths.isNotEmpty()) {
                                val last = redoPaths.removeAt(redoPaths.size - 1)
                                paths.add(last)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        enabled = redoPaths.isNotEmpty(),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // Clear
                    IconButton(
                        onClick = {
                            redoPaths.clear()
                            paths.clear()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        enabled = paths.isNotEmpty(),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tool & Geometry Controls Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = activeTool == OpenBoardTool.PEN,
                        onClick = {
                            activeTool = OpenBoardTool.PEN
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        label = { Text("Pen", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = activeTool == OpenBoardTool.HIGHLIGHTER,
                        onClick = {
                            activeTool = OpenBoardTool.HIGHLIGHTER
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        label = { Text("Highlighter", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = activeTool == OpenBoardTool.ERASER,
                        onClick = {
                            activeTool = OpenBoardTool.ERASER
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        label = { Text("Eraser", fontSize = 11.sp) }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val backgrounds = listOf("DotGrid", "GraphGrid", "Ruled", "Obsidian")
                    val nextBg = backgrounds[(backgrounds.indexOf(canvasBackground) + 1).coerceAtLeast(0) % backgrounds.size]
                    FilterChip(
                        selected = canvasBackground != "Obsidian",
                        onClick = {
                            canvasBackground = nextBg
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        label = { Text(canvasBackground, fontSize = 10.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Color Palette
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                colorPalette.forEach { color ->
                    val isSelected = currentColor == color
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                if (isSelected) 2.5.dp else 1.dp,
                                if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                CircleShape
                            )
                            .clickable {
                                currentColor = color
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Interactive Touch Drawing Surface
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = when (canvasBackground) {
                    "DarkSlate" -> Color(0xFF1E293B)
                    else -> Color(0xFF0F172A)
                },
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(activeTool, currentColor, strokeWidth, canvasBackground) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentPoints = mutableStateListOf(offset)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    currentPoints.add(change.position)
                                },
                                onDragEnd = {
                                    if (currentPoints.isNotEmpty()) {
                                        paths.add(
                                            StrokePath(
                                                points = currentPoints.toList(),
                                                color = effectiveColor,
                                                strokeWidth = effectiveWidth
                                            )
                                        )
                                        redoPaths.clear()
                                        currentPoints = mutableStateListOf()
                                    }
                                }
                            )
                        }
                ) {
                    // OpenBoard canvas background styles
                    when (canvasBackground) {
                        "DotGrid" -> {
                            val spacing = 24.dp.toPx()
                            var x = spacing
                            while (x < size.width) {
                                var y = spacing
                                while (y < size.height) {
                                    drawCircle(
                                        color = Color(0xFF334155),
                                        radius = 1.5f,
                                        center = Offset(x, y)
                                    )
                                    y += spacing
                                }
                                x += spacing
                            }
                        }
                        "GraphGrid" -> {
                            val spacing = 22.dp.toPx()
                            var x = spacing
                            while (x < size.width) {
                                drawLine(
                                    color = Color(0xFF263045),
                                    start = Offset(x, 0f),
                                    end = Offset(x, size.height),
                                    strokeWidth = 1f
                                )
                                x += spacing
                            }
                            var y = spacing
                            while (y < size.height) {
                                drawLine(
                                    color = Color(0xFF263045),
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 1f
                                )
                                y += spacing
                            }
                        }
                        "Ruled" -> {
                            val spacing = 28.dp.toPx()
                            var y = spacing
                            while (y < size.height) {
                                drawLine(
                                    color = Color(0xFF2B3648),
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 1f
                                )
                                y += spacing
                            }
                        }
                    }

                    // Render committed paths
                    paths.forEach { strokePath ->
                        if (strokePath.points.size > 1) {
                            val path = Path().apply {
                                moveTo(strokePath.points.first().x, strokePath.points.first().y)
                                for (i in 1 until strokePath.points.size) {
                                    lineTo(strokePath.points[i].x, strokePath.points[i].y)
                                }
                            }
                            drawPath(
                                path = path,
                                color = strokePath.color,
                                style = Stroke(
                                    width = strokePath.strokeWidth,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }

                    // Render active in-progress stroke
                    if (currentPoints.size > 1) {
                        val activePath = Path().apply {
                            moveTo(currentPoints.first().x, currentPoints.first().y)
                            for (i in 1 until currentPoints.size) {
                                lineTo(currentPoints[i].x, currentPoints[i].y)
                            }
                        }
                        drawPath(
                            path = activePath,
                            color = effectiveColor,
                            style = Stroke(
                                width = effectiveWidth,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val b64 = exportStrokesToPngBase64(paths, background = canvasBackground)
                    if (b64 != null) {
                        onAttachSketch("📐 [OpenBoard Sketch: ${paths.size} strokes]\ndata:image/png;base64,$b64")
                    } else {
                        onAttachSketch("📐 [OpenBoard Sketch Attached: ${paths.size} strokes]")
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Send, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Send Sketch to Agents", color = MaterialTheme.colorScheme.onPrimary)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
