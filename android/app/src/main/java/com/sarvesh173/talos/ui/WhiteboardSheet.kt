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
    val paths = remember { mutableStateListOf<StrokePath>() }
    var currentPoints = remember { mutableStateListOf<Offset>() }
    var currentColor by remember { mutableStateOf(Color(0xFFF59E0B)) }
    var strokeWidth by remember { mutableFloatStateOf(6f) }
    var canvasBackground by remember { mutableStateOf("DotGrid") } // DotGrid, DarkSlate, Plain

    val colorPalette = listOf(
        Color(0xFFF59E0B), // Molten Amber
        Color(0xFF2DD4BF), // Electric Verdigris
        Color(0xFF60A5FA), // Azure Blue
        Color(0xFFF87171), // Laser Red
        Color(0xFFFFFFFF)  // Chalk White
    )

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
                        text = "Sketch architecture or annotate bugs for agents",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Undo
                    IconButton(
                        onClick = { if (paths.isNotEmpty()) paths.removeAt(paths.size - 1) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Undo, contentDescription = "Undo", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // Clear
                    IconButton(
                        onClick = { paths.clear() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Clear", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Drawing Tools Bar (Color Palette, Stroke width, Background mode)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                .clickable { currentColor = color }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = canvasBackground == "DotGrid",
                        onClick = { canvasBackground = "DotGrid" },
                        label = { Text("Grid", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = canvasBackground == "DarkSlate",
                        onClick = { canvasBackground = "DarkSlate" },
                        label = { Text("Slate", fontSize = 11.sp) }
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
                        .pointerInput(currentColor, strokeWidth) {
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
                                                color = currentColor,
                                                strokeWidth = strokeWidth
                                            )
                                        )
                                        currentPoints = mutableStateListOf()
                                    }
                                }
                            )
                        }
                ) {
                    // Draw OpenBoard dot grid pattern
                    if (canvasBackground == "DotGrid") {
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
                            color = currentColor,
                            style = Stroke(
                                width = strokeWidth,
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
                    onAttachSketch("📐 [OpenBoard Sketch Attached: ${paths.size} strokes]")
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
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
